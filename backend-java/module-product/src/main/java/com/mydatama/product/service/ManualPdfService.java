package com.mydatama.product.service;

import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.ds.entity.DatasetVersion;
import com.mydatama.product.entity.Product;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 数据产品说明书：交易所口径八章 XHTML 渲染 + openhtmltopdf 出 PDF。
 */
@Service
public class ManualPdfService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 中文字体探测路径（按序取第一个存在的）。 */
    private static final String[] CJK_FONTS = {
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simhei.ttf"
    };

    /**
     * 渲染说明书八章 XHTML（well-formed）。
     * manualMeta 四要素缺失时占位"未填写"，保证预览可用。
     */
    @SuppressWarnings("unchecked")
    public String buildManualHtml(Product product, DatasetVersion version, List<Map<String, Object>> snapshots,
                                  Map<String, Object> qualityReport) {
        Map<String, Object> manualMeta = parseJsonObject(product.getManualMeta());
        String sourceDesc = textOr(manualMeta.get("sourceDesc"), "未填写");
        String fieldDesc = textOr(manualMeta.get("fieldDesc"), "未填写");
        String updateFreq = textOr(manualMeta.get("updateFreq"), "未填写");
        String deliveryMode = textOr(manualMeta.get("deliveryMode"), "未填写");

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\"/>\n");
        sb.append("<title>").append(esc(product.getName())).append(" - 数据产品说明书</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: 'CJK', 'Microsoft YaHei', sans-serif; color: #222; margin: 32px; font-size: 12px; line-height: 1.7; }\n");
        sb.append("h1 { font-size: 20px; text-align: center; margin-bottom: 4px; }\n");
        sb.append("p.subtitle { text-align: center; color: #666; margin-top: 0; }\n");
        sb.append("h2 { font-size: 14px; border-left: 4px solid #1a5fb4; padding-left: 8px; margin-top: 24px; }\n");
        sb.append("table { border-collapse: collapse; width: 100%; margin-top: 8px; }\n");
        sb.append("th, td { border: 1px solid #999; padding: 4px 8px; text-align: left; font-size: 11px; }\n");
        sb.append("th { background: #eef2f7; }\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<h1>数据产品说明书</h1>\n");
        sb.append("<p class=\"subtitle\">").append(esc(product.getCode())).append("</p>\n");

        // 一、产品基本信息
        sb.append("<h2>一、产品基本信息</h2>\n<table>\n");
        row(sb, "产品名称", product.getName());
        row(sb, "产品编码", product.getCode());
        row(sb, "产品形态", formLabel(product.getForm()));
        row(sb, "原料数据集版本", version == null ? "未知" : "数据集#" + version.getDatasetId() + " v" + version.getVersionNo());
        row(sb, "提供方", product.getProvider());
        row(sb, "密级", product.getSecretLevel() == null ? "-" : String.valueOf(product.getSecretLevel()));
        row(sb, "生成时间", product.getUpdatedAt() == null ? "-" : DT.format(product.getUpdatedAt()));
        sb.append("</table>\n");

        // 二、数据来源
        sb.append("<h2>二、数据来源</h2>\n");
        sb.append("<p>").append(esc(sourceDesc)).append("</p>\n");
        sb.append("<table>\n<tr><th>#</th><th>资产名称</th><th>类型</th><th>模态</th><th>密级</th></tr>\n");
        int idx = 1;
        for (Map<String, Object> snapshot : snapshots) {
            sb.append("<tr><td>").append(idx++).append("</td><td>")
                    .append(esc(textOr(snapshot.get("name"), "-"))).append("</td><td>")
                    .append(esc(textOr(snapshot.get("assetType"), "-"))).append("</td><td>")
                    .append(esc(textOr(snapshot.get("modality"), "-"))).append("</td><td>")
                    .append(esc(textOr(snapshot.get("secretLevel"), "-"))).append("</td></tr>\n");
        }
        sb.append("</table>\n");

        // 三、数据规模
        sb.append("<h2>三、数据规模</h2>\n");
        sb.append("<p>资产数：").append(snapshots.size());
        if (version != null && version.getItemCount() != null) {
            sb.append("；版本数据项数：").append(version.getItemCount());
        }
        sb.append("</p>\n");

        // 四、字段说明
        sb.append("<h2>四、字段说明</h2>\n");
        sb.append("<p>").append(esc(fieldDesc)).append("</p>\n");
        boolean hasMetrics = snapshots.stream().anyMatch(s -> s.get("metrics") instanceof Map);
        if (hasMetrics) {
            sb.append("<table>\n<tr><th>资产</th><th>指标</th><th>值</th></tr>\n");
            for (Map<String, Object> snapshot : snapshots) {
                Object metrics = snapshot.get("metrics");
                if (!(metrics instanceof Map)) {
                    continue;
                }
                for (Map.Entry<String, Object> e : ((Map<String, Object>) metrics).entrySet()) {
                    sb.append("<tr><td>").append(esc(textOr(snapshot.get("name"), "-"))).append("</td><td>")
                            .append(esc(e.getKey())).append("</td><td>")
                            .append(esc(textOr(e.getValue(), "-"))).append("</td></tr>\n");
                }
            }
            sb.append("</table>\n");
        }

        // 五、质量情况
        sb.append("<h2>五、质量情况</h2>\n");
        if (qualityReport == null || qualityReport.isEmpty()) {
            sb.append("<p>未提供质量报告。</p>\n");
        } else {
            sb.append("<table>\n<tr><th>维度</th><th>得分</th></tr>\n");
            qualityRow(sb, "完整性 completeness", qualityReport.get("completeness"));
            qualityRow(sb, "一致性 consistency", qualityReport.get("consistency"));
            qualityRow(sb, "准确性 accuracy", qualityReport.get("accuracy"));
            qualityRow(sb, "综合评分 overall_score", qualityReport.get("overall_score"));
            sb.append("</table>\n");
            Object pass = qualityReport.get("pass");
            sb.append("<p>达标结论：").append(Boolean.TRUE.equals(pass) ? "达标" : "未达标/未知").append("</p>\n");
            Object suggestion = qualityReport.get("suggestion");
            if (suggestion != null) {
                sb.append("<p>质量建议：").append(esc(suggestion.toString())).append("</p>\n");
            }
        }

        // 六、更新频率
        sb.append("<h2>六、更新频率</h2>\n<p>").append(esc(updateFreq)).append("</p>\n");

        // 七、交付方式
        sb.append("<h2>七、交付方式</h2>\n<p>").append(esc(deliveryMode))
                .append("（").append(esc(formLabel(product.getForm()))).append("）</p>\n");

        // 八、合规声明
        sb.append("<h2>八、合规声明</h2>\n");
        sb.append("<p>本产品在生成前已通过平台合规校验引擎四规则检查（密级合规 / 敏感列脱敏 / 质量达标 / 说明书完整）。</p>\n");
        sb.append("<table>\n");
        row(sb, "合规校验批次号", "【校验批次号占位】");
        row(sb, "校验结论", "【校验结论占位】");
        row(sb, "登记凭证号", product.getRegNo() == null ? "【登记凭证号占位】" : product.getRegNo());
        sb.append("</table>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /** XHTML → PDF。失败抛 MANUAL_FAILED。 */
    public byte[] htmlToPdf(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            for (String path : CJK_FONTS) {
                File font = new File(path);
                if (font.exists()) {
                    builder.useFont(font, "CJK");
                    break;
                }
            }
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.MANUAL_FAILED, "说明书 PDF 生成失败: " + e.getMessage());
        }
    }

    private static void row(StringBuilder sb, String key, String value) {
        sb.append("<tr><th>").append(esc(key)).append("</th><td>")
                .append(esc(value == null ? "-" : value)).append("</td></tr>\n");
    }

    private static void qualityRow(StringBuilder sb, String label, Object score) {
        sb.append("<tr><td>").append(esc(label)).append("</td><td>")
                .append(esc(score == null ? "-" : score.toString())).append("</td></tr>\n");
    }

    private static String formLabel(String form) {
        if (form == null) {
            return "-";
        }
        return switch (form) {
            case "DATA_PACKAGE" -> "数据包";
            case "API_SERVICE" -> "API 服务";
            case "REPORT" -> "分析报告";
            default -> form;
        };
    }

    private static String textOr(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** XML 特殊字符转义（保证 well-formed XHTML）。 */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
