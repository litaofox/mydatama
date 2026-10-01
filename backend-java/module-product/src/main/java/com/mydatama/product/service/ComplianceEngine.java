package com.mydatama.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.common.security.UserContext;
import com.mydatama.common.util.CodecUtil;
import com.mydatama.ds.api.DsApi;
import com.mydatama.ds.entity.DatasetItem;
import com.mydatama.ds.entity.DatasetVersion;
import com.mydatama.gov.api.GovApi;
import com.mydatama.gov.entity.MetadataColumn;
import com.mydatama.product.entity.ComplianceCheck;
import com.mydatama.product.entity.Product;
import com.mydatama.product.mapper.ComplianceCheckMapper;
import com.mydatama.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合规校验引擎：四规则同步执行，逐条落库（同批 batch_no 一致）。
 */
@Service
@RequiredArgsConstructor
public class ComplianceEngine {

    /** 说明书四要素（manualMeta 必填键）。 */
    public static final List<String> MANUAL_KEYS = List.of("sourceDesc", "fieldDesc", "updateFreq", "deliveryMode");

    private final ProductMapper productMapper;
    private final ComplianceCheckMapper complianceCheckMapper;
    private final DsApi dsApi;
    private final GovApi govApi;
    private final ObjectMapper objectMapper;

    /** 执行四规则校验：仅 GENERATED/BLOCKED 允许。返回 {batchNo,status,items}。 */
    public Map<String, Object> run(Long productId) {
        Product product = productId == null ? null : productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(600001, "产品不存在");
        }
        if (!"GENERATED".equals(product.getStatus()) && !"BLOCKED".equals(product.getStatus())) {
            throw new BizException(ErrorCode.PRODUCT_STATE_ERROR);
        }

        Product checking = new Product();
        checking.setId(product.getId());
        checking.setStatus("CHECKING");
        productMapper.updateById(checking);

        String batchNo = "BC" + CodecUtil.today() + CodecUtil.randomHex(4).toUpperCase();
        LocalDateTime runAt = LocalDateTime.now();

        DatasetVersion version = dsApi.getVersionById(product.getDatasetVersionId());
        List<DatasetItem> items = version == null ? List.of() : dsApi.getVersionItems(version.getId());
        List<Map<String, Object>> snapshots = snapshotsOf(items);
        Map<String, Object> configParams = parseMap(product.getConfigParams());
        Map<String, Object> manualMeta = parseMap(product.getManualMeta());
        Map<String, Object> qualityReport = version == null ? Map.of() : parseMap(version.getQualityReport());

        List<RuleResult> results = new ArrayList<>();
        results.add(ruleSecretLevel(product, snapshots));
        results.add(ruleSensitiveMasked(snapshots, items));
        results.add(ruleQualityThreshold(qualityReport, configParams));
        results.add(ruleManualComplete(manualMeta));

        boolean allPassed = true;
        for (RuleResult r : results) {
            ComplianceCheck check = new ComplianceCheck();
            check.setProductId(product.getId());
            check.setBatchNo(batchNo);
            check.setRuleCode(r.ruleCode);
            check.setRuleName(r.ruleName);
            check.setPassed(r.passed ? 1 : 0);
            check.setDetail(r.detail);
            check.setRunAt(runAt);
            // compliance_checks.create_by 为 NOT NULL，该实体不继承 BaseEntity，需手动填充
            check.setCreateBy(UserContext.get() == null ? "system" : UserContext.get().getUsername());
            complianceCheckMapper.insert(check);
            allPassed &= r.passed;
        }

        String status = allPassed ? "PASSED" : "BLOCKED";
        Product done = new Product();
        done.setId(product.getId());
        done.setStatus(status);
        productMapper.updateById(done);

        return toResult(batchNo, status, results);
    }

    /** 最新批次合规明细（无批次返回 null）。 */
    public Map<String, Object> latest(Long productId) {
        List<ComplianceCheck> checks = complianceCheckMapper.selectList(
                new LambdaQueryWrapper<ComplianceCheck>()
                        .eq(ComplianceCheck::getProductId, productId)
                        .orderByDesc(ComplianceCheck::getRunAt)
                        .orderByDesc(ComplianceCheck::getId));
        if (checks.isEmpty()) {
            return null;
        }
        String batchNo = checks.get(0).getBatchNo();
        List<RuleResult> results = new ArrayList<>();
        for (ComplianceCheck c : checks) {
            if (!batchNo.equals(c.getBatchNo())) {
                break;
            }
            results.add(new RuleResult(c.getRuleCode(), c.getRuleName(), c.getPassed() != null && c.getPassed() == 1,
                    c.getDetail()));
        }
        boolean allPassed = results.stream().allMatch(r -> r.passed);
        java.util.Collections.reverse(results);
        return toResult(batchNo, allPassed ? "PASSED" : "BLOCKED", results);
    }

    /** 说明书四要素缺失键列表。 */
    public static List<String> missingManualKeys(Map<String, Object> manualMeta) {
        List<String> missing = new ArrayList<>();
        for (String key : MANUAL_KEYS) {
            Object v = manualMeta == null ? null : manualMeta.get(key);
            if (v == null || !StringUtils.hasText(v.toString())) {
                missing.add(key);
            }
        }
        return missing;
    }

    /** R1 密级合规：原料最大密级 ≤ 产品密级。 */
    private RuleResult ruleSecretLevel(Product product, List<Map<String, Object>> snapshots) {
        int max = 1;
        for (Map<String, Object> snapshot : snapshots) {
            Object level = snapshot.get("secretLevel");
            if (level instanceof Number n) {
                max = Math.max(max, n.intValue());
            }
        }
        int productLevel = product.getSecretLevel() == null ? 1 : product.getSecretLevel();
        if (max > productLevel) {
            return new RuleResult("SECRET_LEVEL", "密级合规", false,
                    "原料最大密级 " + max + " 高于产品密级 " + productLevel);
        }
        return new RuleResult("SECRET_LEVEL", "密级合规", true,
                "原料最大密级 " + max + " ≤ 产品密级 " + productLevel);
    }

    /** R2 敏感列脱敏：结构化原料的全部敏感列须有 mask_strategy。 */
    private RuleResult ruleSensitiveMasked(List<Map<String, Object>> snapshots, List<DatasetItem> items) {
        List<Long> structuredAssetIds = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Object modality = snapshots.get(i).get("modality");
            if ("STRUCTURED".equals(modality) && items.get(i).getAssetId() != null) {
                structuredAssetIds.add(items.get(i).getAssetId());
            }
        }
        if (structuredAssetIds.isEmpty()) {
            return new RuleResult("SENSITIVE_MASKED", "敏感列脱敏", true, "无结构化原料资产");
        }
        List<MetadataColumn> columns = govApi.listColumnsByAssetIds(structuredAssetIds);
        List<String> unmasked = new ArrayList<>();
        for (MetadataColumn column : columns) {
            if (column.getSensitive() != null && column.getSensitive() == 1
                    && !StringUtils.hasText(column.getMaskStrategy())) {
                unmasked.add(column.getColName());
            }
        }
        if (!unmasked.isEmpty()) {
            return new RuleResult("SENSITIVE_MASKED", "敏感列脱敏", false,
                    "存在 " + unmasked.size() + " 个未脱敏敏感列: " + String.join(",", unmasked));
        }
        return new RuleResult("SENSITIVE_MASKED", "敏感列脱敏", true, "敏感列均已配置脱敏策略");
    }

    /** R3 质量达标：overall_score ≥ 阈值（默认 0.85）。 */
    private RuleResult ruleQualityThreshold(Map<String, Object> qualityReport, Map<String, Object> configParams) {
        double threshold = 0.85d;
        Object configured = configParams.get("qualityThreshold");
        if (configured instanceof Number n) {
            threshold = n.doubleValue();
        }
        Object scoreObj = qualityReport.get("overall_score");
        if (!(scoreObj instanceof Number n)) {
            return new RuleResult("QUALITY_THRESHOLD", "质量达标", false, "原料版本缺少质量评分 overall_score");
        }
        double score = n.doubleValue();
        if (score < threshold) {
            return new RuleResult("QUALITY_THRESHOLD", "质量达标", false,
                    "质量分 " + score + " 低于阈值 " + threshold);
        }
        return new RuleResult("QUALITY_THRESHOLD", "质量达标", true,
                "质量分 " + score + " ≥ 阈值 " + threshold);
    }

    /** R4 说明书完整：manualMeta 四要素非空。 */
    private RuleResult ruleManualComplete(Map<String, Object> manualMeta) {
        List<String> missing = missingManualKeys(manualMeta);
        if (!missing.isEmpty()) {
            return new RuleResult("MANUAL_COMPLETE", "说明书完整", false,
                    "说明书要素缺失: " + String.join(",", missing));
        }
        return new RuleResult("MANUAL_COMPLETE", "说明书完整", true, "说明书要素完整");
    }

    private List<Map<String, Object>> snapshotsOf(List<DatasetItem> items) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (DatasetItem item : items) {
            snapshots.add(parseMap(item.getAssetSnapshot()));
        }
        return snapshots;
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

    private Map<String, Object> toResult(String batchNo, String status, List<RuleResult> results) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (RuleResult r : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ruleCode", r.ruleCode);
            item.put("ruleName", r.ruleName);
            item.put("passed", r.passed);
            item.put("detail", r.detail);
            items.add(item);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("batchNo", batchNo);
        map.put("status", status);
        map.put("items", items);
        return map;
    }

    private record RuleResult(String ruleCode, String ruleName, boolean passed, String detail) {
    }
}
