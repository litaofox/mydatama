package com.mydatama.gov.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.Result;
import com.mydatama.gov.api.GovApi;
import com.mydatama.gov.entity.Asset;
import com.mydatama.gov.entity.MetadataColumn;
import com.mydatama.gov.entity.MetadataTable;
import com.mydatama.gov.mapper.AssetMapper;
import com.mydatama.gov.mapper.MetadataColumnMapper;
import com.mydatama.gov.mapper.MetadataTableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部资产登记（/internal/** 由 ServiceTokenFilter 统一鉴权，Python 处理完成回调）。
 */
@RestController
@RequestMapping("/internal/assets")
@RequiredArgsConstructor
public class InternalAssetController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssetMapper assetMapper;
    private final MetadataTableMapper metadataTableMapper;
    private final MetadataColumnMapper metadataColumnMapper;
    private final GovApi govApi;

    @PostMapping("/register")
    @Transactional
    public Result<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        Long sourceFileId = toLong(body.get("sourceFileId"));
        if (sourceFileId != null) {
            Asset existing = assetMapper.selectOne(new LambdaQueryWrapper<Asset>()
                    .eq(Asset::getSourceFileId, sourceFileId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return Result.ok(Map.of("assetId", existing.getId(), "created", false));
            }
        }

        String fileName = body.get("fileName") == null ? null : body.get("fileName").toString();
        String modality = body.get("modality") == null ? null : body.get("modality").toString();
        String format = body.get("format") == null ? null : body.get("format").toString();
        String bizDomain = body.get("bizDomain") == null ? null : body.get("bizDomain").toString();
        String rawPath = body.get("rawPath") == null ? null : body.get("rawPath").toString();
        String processedPath = body.get("processedPath") == null ? null : body.get("processedPath").toString();
        String thumbPath = body.get("thumbPath") == null ? null : body.get("thumbPath").toString();
        Long sizeBytes = toLong(body.get("sizeBytes"));
        Integer secretLevel = body.get("secretLevel") instanceof Number n ? n.intValue() : 1;

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = body.get("metrics") instanceof Map ? (Map<String, Object>) body.get("metrics") : null;

        Asset asset = new Asset();
        asset.setName(fileName);
        asset.setAssetType("STRUCTURED".equals(modality) ? "DATASET_FILE" : "FILE");
        asset.setModality(modality);
        asset.setBizDomain(bizDomain);
        asset.setSecretLevel(secretLevel);
        asset.setStorageRef(StringUtils.hasText(processedPath) ? processedPath : rawPath);
        asset.setSourceFileId(sourceFileId);
        asset.setQualityScore(computeQualityScore(metrics));
        asset.setStatus("ACTIVE");
        asset.setExt(buildExt(format, sizeBytes, rawPath, processedPath, thumbPath, metrics));
        assetMapper.insert(asset);
        Long assetId = asset.getId();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columns = body.get("columns") instanceof List ? (List<Map<String, Object>>) body.get("columns") : null;
        if (columns != null && !columns.isEmpty()) {
            MetadataTable table = new MetadataTable();
            table.setAssetId(assetId);
            table.setTableName(fileName);
            table.setComment("上传结构化文件");
            table.setRowCount(metrics == null ? null : toLong(metrics.get("rowCount")));
            metadataTableMapper.insert(table);

            int ordinal = 1;
            for (Map<String, Object> col : columns) {
                MetadataColumn column = new MetadataColumn();
                column.setTableId(table.getId());
                column.setColName(col.get("colName") == null ? null : col.get("colName").toString());
                column.setDataType(col.get("dataType") == null ? null : col.get("dataType").toString());
                column.setOrdinal(ordinal++);
                column.setSensitive(Boolean.TRUE.equals(col.get("sensitive")) ? 1 : 0);
                column.setMaskStrategy(col.get("maskStrategy") == null ? null : col.get("maskStrategy").toString());
                metadataColumnMapper.insert(column);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> lineage = body.get("lineage") instanceof Map ? (Map<String, Object>) body.get("lineage") : null;
        if (lineage != null && lineage.get("fromAssetId") instanceof Number fromNum) {
            String relType = lineage.get("relType") == null ? null : lineage.get("relType").toString();
            govApi.addLineageEdge(fromNum.longValue(), assetId, relType);
        }

        return Result.ok(Map.of("assetId", assetId, "created", true));
    }

    private BigDecimal computeQualityScore(Map<String, Object> metrics) {
        if (metrics == null) {
            return null;
        }
        Double missing = toDouble(metrics.get("missingRate"));
        Double duplicate = toDouble(metrics.get("duplicateRate"));
        if (missing == null && duplicate == null) {
            return null;
        }
        double score = 100.0 * (1 - (missing == null ? 0 : missing) - (duplicate == null ? 0 : duplicate));
        return BigDecimal.valueOf(Math.max(0, score)).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildExt(String format, Long sizeBytes, String rawPath, String processedPath,
                            String thumbPath, Map<String, Object> metrics) {
        Map<String, Object> ext = new HashMap<>();
        ext.put("format", format);
        ext.put("sizeBytes", sizeBytes);
        ext.put("rawPath", rawPath);
        ext.put("processedPath", processedPath);
        ext.put("thumbPath", thumbPath);
        ext.put("metrics", metrics);
        try {
            return OBJECT_MAPPER.writeValueAsString(ext);
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private Double toDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
