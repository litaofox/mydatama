package com.mydatama.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.ds.api.DsApi;
import com.mydatama.ds.entity.DatasetItem;
import com.mydatama.ds.entity.DatasetVersion;
import com.mydatama.product.entity.Product;
import com.mydatama.product.entity.ProductArtifact;
import com.mydatama.product.mapper.ProductArtifactMapper;
import com.mydatama.product.mapper.ProductMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 产品交付物导出 + 说明书在线预览。
 */
@Service
@RequiredArgsConstructor
public class ProductExportService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductMapper productMapper;
    private final ProductArtifactMapper productArtifactMapper;
    private final DsApi dsApi;
    private final ManualPdfService manualPdfService;
    private final ObjectMapper objectMapper;

    @Value("${mydatama.data-dir:/data}")
    private String dataDir;

    /** 导出交付物（状态须 PASSED/REGISTERED/LISTED），流式写出。 */
    public void export(Long id, HttpServletResponse response) throws Exception {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(600001, "产品不存在");
        }
        if (!List.of("PASSED", "REGISTERED", "LISTED").contains(product.getStatus())) {
            throw new BizException(ErrorCode.PRODUCT_STATE_ERROR);
        }

        switch (product.getForm()) {
            case "DATA_PACKAGE" -> streamArtifact(product, "DATA_PACKAGE_ZIP", "application/zip", response);
            case "REPORT" -> streamArtifact(product, "REPORT_PDF", "application/pdf", response);
            case "API_SERVICE" -> streamApiPackage(product, response);
            default -> throw new BizException(ErrorCode.EXPORT_NOT_FOUND, "未知产品形态: " + product.getForm());
        }
    }

    /** 说明书 HTML 在线预览（manualMeta 缺失也可预览，占位"未填写"）。 */
    public String manualHtml(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(600001, "产品不存在");
        }
        DatasetVersion version = dsApi.getVersionById(product.getDatasetVersionId());
        List<Map<String, Object>> snapshots = new ArrayList<>();
        if (version != null) {
            List<DatasetItem> items = dsApi.getVersionItems(version.getId());
            if (items != null) {
                for (DatasetItem item : items) {
                    snapshots.add(parseMap(item.getAssetSnapshot()));
                }
            }
        }
        Map<String, Object> qualityReport = version == null ? Map.of() : parseMap(version.getQualityReport());
        return manualPdfService.buildManualHtml(product, version, snapshots, qualityReport);
    }

    /** 流式输出已生成产物文件。 */
    private void streamArtifact(Product product, String artifactType, String contentType,
                                HttpServletResponse response) throws Exception {
        ProductArtifact artifact = productArtifactMapper.selectOne(new LambdaQueryWrapper<ProductArtifact>()
                .eq(ProductArtifact::getProductId, product.getId())
                .eq(ProductArtifact::getArtifactType, artifactType)
                .orderByDesc(ProductArtifact::getId)
                .last("LIMIT 1"));
        Path path = artifact == null || !StringUtils.hasText(artifact.getFilePath())
                ? null
                : Paths.get(dataDir).resolve(artifact.getFilePath());
        if (path == null || !Files.exists(path)) {
            throw new BizException(ErrorCode.EXPORT_NOT_FOUND);
        }
        String filename = path.getFileName().toString();
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", contentDisposition(filename));
        if (StringUtils.hasText(artifact.getChecksum())) {
            response.setHeader("X-Checksum-SHA256", artifact.getChecksum());
        }
        response.setContentLengthLong(Files.size(path));
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(path, out);
            out.flush();
        }
    }

    /** API_SERVICE：动态生成对接说明 zip（endpoint/keyPrefix/rowLimit）。 */
    private void streamApiPackage(Product product, HttpServletResponse response) throws Exception {
        ProductArtifact artifact = productArtifactMapper.selectOne(new LambdaQueryWrapper<ProductArtifact>()
                .eq(ProductArtifact::getProductId, product.getId())
                .eq(ProductArtifact::getArtifactType, "API_KEY_REF")
                .orderByDesc(ProductArtifact::getId)
                .last("LIMIT 1"));
        if (artifact == null) {
            throw new BizException(ErrorCode.EXPORT_NOT_FOUND);
        }
        Map<String, Object> ext = parseMap(artifact.getExt());
        String endpoint = textOr(ext.get("endpoint"), "/openapi/v1/products/" + product.getCode() + "/rows");
        String rowLimit = textOr(ext.get("rowLimit"), "1000");
        String keyPrefix = textOr(artifact.getFilePath(), "-");

        StringBuilder readme = new StringBuilder();
        readme.append("API 服务对接说明\n");
        readme.append("产品名称：").append(product.getName()).append('\n');
        readme.append("产品编码：").append(product.getCode()).append('\n');
        readme.append("调用端点：GET ").append(endpoint).append('\n');
        readme.append("鉴权方式：请求头 X-Api-Key（完整 Key 仅在生成时下发一次）\n");
        readme.append("Key 前缀：").append(keyPrefix).append('\n');
        readme.append("单次返回行数上限：").append(rowLimit).append('\n');
        readme.append("导出时间：").append(DT.format(LocalDateTime.now())).append('\n');

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            zos.putNextEntry(new ZipEntry("README.txt"));
            zos.write(readme.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zipBytes = buffer.toByteArray();

        String filename = "product_" + product.getCode() + "_api_package.zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", contentDisposition(filename));
        response.setHeader("X-Checksum-SHA256", sha256(zipBytes));
        response.setContentLengthLong(zipBytes.length);
        response.getOutputStream().write(zipBytes);
        response.getOutputStream().flush();
    }

    private static String contentDisposition(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
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
