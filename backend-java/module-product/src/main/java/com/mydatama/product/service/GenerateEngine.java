package com.mydatama.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.common.security.UserContext;
import com.mydatama.common.security.UserInfo;
import com.mydatama.ds.api.DsApi;
import com.mydatama.ds.entity.DatasetItem;
import com.mydatama.ds.entity.DatasetVersion;
import com.mydatama.gov.api.GovApi;
import com.mydatama.iam.api.IamApi;
import com.mydatama.product.entity.Product;
import com.mydatama.product.entity.ProductArtifact;
import com.mydatama.product.mapper.ProductArtifactMapper;
import com.mydatama.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 产品生成引擎：DATA_PACKAGE zip / API_SERVICE 计量 Key / REPORT HTML→PDF。
 */
@Service
@RequiredArgsConstructor
public class GenerateEngine {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DsApi dsApi;
    private final GovApi govApi;
    private final IamApi iamApi;
    private final ProductMapper productMapper;
    private final ProductArtifactMapper productArtifactMapper;
    private final ObjectMapper objectMapper;
    private final ManualPdfService manualPdfService;

    @Value("${mydatama.data-dir:/data}")
    private String dataDir;

    /**
     * 执行生成（仅 CONFIGURED/GENERATED 允许）。
     * @return API_SERVICE 形态返回计量 Key 明文（仅此一次），其余形态返回 null
     */
    public String generate(Long productId) {
        Product product = productId == null ? null : productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(600001, "产品不存在");
        }
        if (!"CONFIGURED".equals(product.getStatus()) && !"GENERATED".equals(product.getStatus())) {
            throw new BizException(ErrorCode.PRODUCT_STATE_ERROR);
        }
        try {
            return doGenerate(product);
        } catch (BizException e) {
            markError(product, e.getMessage());
            throw e;
        } catch (Exception e) {
            markError(product, e.getMessage());
            throw new BizException(ErrorCode.GENERATE_FAILED, "产品生成失败: " + e.getMessage());
        }
    }

    private String doGenerate(Product product) throws Exception {
        DatasetVersion version = dsApi.getVersionById(product.getDatasetVersionId());
        if (version == null) {
            throw new BizException(ErrorCode.GENERATE_FAILED, "原料数据集版本不存在");
        }
        List<DatasetItem> items = dsApi.getVersionItems(version.getId());
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.GENERATE_FAILED, "原料数据集版本无数据项");
        }
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (DatasetItem item : items) {
            snapshots.add(parseMap(item.getAssetSnapshot()));
        }
        Map<String, Object> qualityReport = parseMap(version.getQualityReport());

        Path productDir = Paths.get(dataDir, "products", product.getCode());
        Files.createDirectories(productDir);

        String apiKey = null;
        String mainArtifactPath;
        switch (product.getForm()) {
            case "DATA_PACKAGE" -> mainArtifactPath = generateDataPackage(product, version, snapshots, qualityReport,
                    productDir);
            case "API_SERVICE" -> {
                apiKey = generateApiService(product);
                mainArtifactPath = "/openapi/v1/products/" + product.getCode() + "/rows";
            }
            case "REPORT" -> mainArtifactPath = generateReport(product, version, snapshots, qualityReport, productDir);
            default -> throw new BizException(ErrorCode.GENERATE_FAILED, "未知产品形态: " + product.getForm());
        }

        // 三形态统一收尾：登记产品资产 + 血缘边 + 状态推进
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("productId", product.getId());
        ext.put("code", product.getCode());
        ext.put("form", product.getForm());
        Long productAssetId = govApi.registerProductAsset(product.getName(), product.getCategory(),
                product.getSecretLevel(), mainArtifactPath, objectMapper.writeValueAsString(ext));
        int lineageCount = 0;
        for (DatasetItem item : items) {
            if (lineageCount++ >= 50) {
                break;
            }
            govApi.addLineageEdge(item.getAssetId(), productAssetId, "DERIVED_BY");
        }

        Product update = new Product();
        update.setId(product.getId());
        update.setStatus("GENERATED");
        update.setLastError(null);
        productMapper.updateById(update);
        return apiKey;
    }

    /** DATA_PACKAGE：复制 processed 文件 + README + quality_report.json + manual.pdf → zip。 */
    private String generateDataPackage(Product product, DatasetVersion version, List<Map<String, Object>> snapshots,
                                       Map<String, Object> qualityReport, Path productDir) throws Exception {
        Path tmp = productDir.resolve("build-" + UUID.randomUUID());
        Files.createDirectories(tmp);
        try {
            List<String> skipped = new ArrayList<>();
            for (Map<String, Object> snapshot : snapshots) {
                String storageRef = textOr(snapshot.get("storageRef"), null);
                if (!StringUtils.hasText(storageRef)) {
                    skipped.add(textOr(snapshot.get("name"), "未知资产") + "（无 storageRef）");
                    continue;
                }
                Path source = resolveDataPath(storageRef);
                if (!Files.exists(source)) {
                    skipped.add(textOr(snapshot.get("name"), "未知资产") + "（文件不存在: " + storageRef + "）");
                    continue;
                }
                String targetName = sanitize(textOr(snapshot.get("name"), "asset")) + "_" + source.getFileName();
                Files.copy(source, tmp.resolve(targetName), StandardCopyOption.REPLACE_EXISTING);
            }

            // README.txt
            StringBuilder readme = new StringBuilder();
            readme.append("数据产品交付包\n");
            readme.append("产品名称：").append(product.getName()).append('\n');
            readme.append("产品编码：").append(product.getCode()).append('\n');
            readme.append("来源数据集版本：数据集#").append(version.getDatasetId())
                    .append(" v").append(version.getVersionNo()).append('\n');
            readme.append("数据规模：资产数 ").append(snapshots.size());
            if (version.getItemCount() != null) {
                readme.append("，数据项数 ").append(version.getItemCount());
            }
            readme.append('\n');
            readme.append("密级：").append(product.getSecretLevel()).append('\n');
            readme.append("生成时间：").append(DT.format(LocalDateTime.now())).append('\n');
            Map<String, Object> manualMeta = parseMap(product.getManualMeta());
            readme.append("交付方式：").append(textOr(manualMeta.get("deliveryMode"), "未填写")).append('\n');
            if (!skipped.isEmpty()) {
                readme.append("\n以下原料文件缺失，未纳入本包：\n");
                for (String s : skipped) {
                    readme.append(" - ").append(s).append('\n');
                }
            }
            Files.writeString(tmp.resolve("README.txt"), readme.toString(), StandardCharsets.UTF_8);

            // quality_report.json（版本质量报告原文）
            String qualityJson = StringUtils.hasText(version.getQualityReport()) ? version.getQualityReport() : "{}";
            byte[] qualityBytes = qualityJson.getBytes(StandardCharsets.UTF_8);
            Files.write(tmp.resolve("quality_report.json"), qualityBytes);

            // manual.pdf
            String manualHtml = manualPdfService.buildManualHtml(product, version, snapshots, qualityReport);
            byte[] manualPdf = manualPdfService.htmlToPdf(manualHtml);
            Files.write(tmp.resolve("manual.pdf"), manualPdf);

            // zip 打包
            String zipName = "product_" + product.getCode() + "_v" + version.getVersionNo() + ".zip";
            Path zipPath = productDir.resolve(zipName);
            zipDirectory(tmp, zipPath);

            // 说明书/质量报告同步留存至产品目录（artifact 可独立定位）
            Path manualPath = productDir.resolve("manual.pdf");
            Files.write(manualPath, manualPdf);
            Path qualityPath = productDir.resolve("quality_report.json");
            Files.write(qualityPath, qualityBytes);

            resetArtifacts(product.getId());
            String rel = "products/" + product.getCode() + "/";
            insertArtifact(product.getId(), "DATA_PACKAGE_ZIP", rel + zipName, Files.size(zipPath), sha256(zipPath),
                    null);
            insertArtifact(product.getId(), "MANUAL_PDF", rel + "manual.pdf", Files.size(manualPath),
                    sha256(manualPath), null);
            insertArtifact(product.getId(), "QUALITY_REPORT", rel + "quality_report.json", Files.size(qualityPath),
                    sha256(qualityPath), null);
            return rel + zipName;
        } finally {
            deleteRecursively(tmp);
        }
    }

    /** API_SERVICE：签发计量 Key（明文仅本次返回），写 API_KEY_REF 产物。 */
    private String generateApiService(Product product) throws Exception {
        UserInfo user = UserContext.get();
        Long userId = user == null ? null : user.getUserId();
        String plainKey = iamApi.createMeteredApiKey(userId, "product-" + product.getCode(), "openapi:product:read");
        if (!StringUtils.hasText(plainKey)) {
            throw new BizException(ErrorCode.GENERATE_FAILED, "计量 Key 签发失败");
        }
        String keyPrefix = plainKey.substring(0, Math.min(11, plainKey.length()));

        Map<String, Object> configParams = parseMap(product.getConfigParams());
        Object rowLimitObj = configParams.get("rowLimit");
        int rowLimit = rowLimitObj instanceof Number n ? n.intValue() : 1000;

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("endpoint", "/openapi/v1/products/" + product.getCode() + "/rows");
        ext.put("rowLimit", rowLimit);

        resetArtifacts(product.getId());
        insertArtifact(product.getId(), "API_KEY_REF", keyPrefix, 0L, null,
                objectMapper.writeValueAsString(ext));
        return plainKey;
    }

    /** REPORT：分析报告 HTML → PDF。 */
    private String generateReport(Product product, DatasetVersion version, List<Map<String, Object>> snapshots,
                                  Map<String, Object> qualityReport, Path productDir) throws Exception {
        Map<String, Object> configParams = parseMap(product.getConfigParams());
        String title = textOr(configParams.get("title"), product.getName());
        String html = buildReportHtml(product, version, snapshots, qualityReport, title);

        Path htmlPath = productDir.resolve("report.html");
        Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
        byte[] pdf = manualPdfService.htmlToPdf(html);
        Path pdfPath = productDir.resolve("report.pdf");
        Files.write(pdfPath, pdf);

        resetArtifacts(product.getId());
        String rel = "products/" + product.getCode() + "/";
        insertArtifact(product.getId(), "REPORT_HTML", rel + "report.html", Files.size(htmlPath), sha256(htmlPath),
                null);
        insertArtifact(product.getId(), "REPORT_PDF", rel + "report.pdf", Files.size(pdfPath), sha256(pdfPath), null);
        return rel + "report.pdf";
    }

    /** 分析报告 HTML：质量三维 + 资产清单表 + 模态统计（内联 CSS 简洁商务风）。 */
    private String buildReportHtml(Product product, DatasetVersion version, List<Map<String, Object>> snapshots,
                                   Map<String, Object> qualityReport, String title) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<meta charset=\"UTF-8\"/>\n<title>")
                .append(ManualPdfService.esc(title)).append("</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: 'CJK', 'Microsoft YaHei', sans-serif; color: #1f2937; margin: 36px; font-size: 12px; line-height: 1.7; }\n");
        sb.append("h1 { font-size: 22px; color: #0f3057; border-bottom: 2px solid #0f3057; padding-bottom: 8px; }\n");
        sb.append("h2 { font-size: 15px; color: #0f3057; margin-top: 26px; }\n");
        sb.append("table { border-collapse: collapse; width: 100%; margin-top: 8px; }\n");
        sb.append("th, td { border: 1px solid #b6c2cf; padding: 5px 9px; text-align: left; font-size: 11px; }\n");
        sb.append("th { background: #0f3057; color: #ffffff; }\n");
        sb.append("tr:nth-child(even) td { background: #f4f7fa; }\n");
        sb.append("p.meta { color: #6b7280; }\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<h1>").append(ManualPdfService.esc(title)).append("</h1>\n");
        sb.append("<p class=\"meta\">产品编码：").append(ManualPdfService.esc(product.getCode()))
                .append("　来源：数据集#").append(version.getDatasetId()).append(" v").append(version.getVersionNo())
                .append("　生成时间：").append(DT.format(LocalDateTime.now())).append("</p>\n");

        // 质量三维
        sb.append("<h2>数据质量</h2>\n");
        if (qualityReport.isEmpty()) {
            sb.append("<p>未提供质量报告。</p>\n");
        } else {
            sb.append("<table>\n<tr><th>维度</th><th>得分</th></tr>\n");
            reportQualityRow(sb, "完整性", qualityReport.get("completeness"));
            reportQualityRow(sb, "一致性", qualityReport.get("consistency"));
            reportQualityRow(sb, "准确性", qualityReport.get("accuracy"));
            reportQualityRow(sb, "综合评分", qualityReport.get("overall_score"));
            sb.append("</table>\n");
        }

        // 资产清单
        sb.append("<h2>资产清单</h2>\n");
        sb.append("<table>\n<tr><th>#</th><th>资产名称</th><th>类型</th><th>模态</th><th>密级</th><th>质量分</th></tr>\n");
        int idx = 1;
        for (Map<String, Object> snapshot : snapshots) {
            sb.append("<tr><td>").append(idx++).append("</td><td>")
                    .append(ManualPdfService.esc(textOr(snapshot.get("name"), "-"))).append("</td><td>")
                    .append(ManualPdfService.esc(textOr(snapshot.get("assetType"), "-"))).append("</td><td>")
                    .append(ManualPdfService.esc(textOr(snapshot.get("modality"), "-"))).append("</td><td>")
                    .append(ManualPdfService.esc(textOr(snapshot.get("secretLevel"), "-"))).append("</td><td>")
                    .append(ManualPdfService.esc(textOr(snapshot.get("qualityScore"), "-"))).append("</td></tr>\n");
        }
        sb.append("</table>\n");

        // 模态统计
        Map<String, Integer> modalityCount = new TreeMap<>();
        for (Map<String, Object> snapshot : snapshots) {
            String modality = textOr(snapshot.get("modality"), "未知");
            modalityCount.merge(modality, 1, Integer::sum);
        }
        sb.append("<h2>模态统计</h2>\n");
        sb.append("<table>\n<tr><th>模态</th><th>资产数</th></tr>\n");
        for (Map.Entry<String, Integer> e : modalityCount.entrySet()) {
            sb.append("<tr><td>").append(ManualPdfService.esc(e.getKey())).append("</td><td>")
                    .append(e.getValue()).append("</td></tr>\n");
        }
        sb.append("</table>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private static void reportQualityRow(StringBuilder sb, String label, Object score) {
        sb.append("<tr><td>").append(label).append("</td><td>")
                .append(score == null ? "-" : ManualPdfService.esc(score.toString())).append("</td></tr>\n");
    }

    /** storageRef（"/data/processed/..." 或 "processed/..."）→ 本地路径。 */
    private Path resolveDataPath(String storageRef) {
        String ref = storageRef;
        if (ref.startsWith("/data/")) {
            ref = ref.substring("/data/".length());
        } else if (ref.startsWith("/")) {
            ref = ref.substring(1);
        }
        return Paths.get(dataDir).resolve(ref);
    }

    private void resetArtifacts(Long productId) {
        productArtifactMapper.delete(new LambdaQueryWrapper<ProductArtifact>()
                .eq(ProductArtifact::getProductId, productId));
    }

    private void insertArtifact(Long productId, String type, String filePath, Long fileSize, String checksum,
                                String ext) {
        ProductArtifact artifact = new ProductArtifact();
        artifact.setProductId(productId);
        artifact.setArtifactType(type);
        artifact.setFilePath(filePath);
        artifact.setFileSize(fileSize == null ? 0L : fileSize);
        artifact.setChecksum(checksum);
        artifact.setExt(ext);
        productArtifactMapper.insert(artifact);
    }

    private void markError(Product product, String message) {
        Product update = new Product();
        update.setId(product.getId());
        String error = message == null ? "未知错误" : message;
        update.setLastError(error.length() > 500 ? error.substring(0, 500) : error);
        productMapper.updateById(update);
    }

    private static void zipDirectory(Path dir, Path zipPath) throws IOException {
        try (OutputStream fos = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            try (var stream = Files.list(dir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private static String textOr(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
