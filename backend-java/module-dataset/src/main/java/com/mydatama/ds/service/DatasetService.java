package com.mydatama.ds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.common.security.UserContext;
import com.mydatama.common.security.UserInfo;
import com.mydatama.ds.entity.Dataset;
import com.mydatama.ds.entity.DatasetItem;
import com.mydatama.ds.entity.DatasetVersion;
import com.mydatama.ds.mapper.DatasetItemMapper;
import com.mydatama.ds.mapper.DatasetMapper;
import com.mydatama.ds.mapper.DatasetVersionMapper;
import com.mydatama.gov.api.GovApi;
import com.mydatama.gov.entity.Asset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private static final double PASS_THRESHOLD = 0.85;

    private final DatasetMapper datasetMapper;
    private final DatasetVersionMapper datasetVersionMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final GovApi govApi;
    private final ObjectMapper objectMapper;

    public IPage<Dataset> list(int page, int size) {
        return datasetMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Dataset>().orderByDesc(Dataset::getId));
    }

    public Long create(String name, String scenario, String description, Map<String, Object> filterCondMap) {
        UserInfo user = UserContext.get();
        String creator = user == null ? null : user.getUsername();
        LambdaQueryWrapper<Dataset> dup = new LambdaQueryWrapper<Dataset>().eq(Dataset::getName, name);
        if (creator == null) {
            dup.isNull(Dataset::getCreator);
        } else {
            dup.eq(Dataset::getCreator, creator);
        }
        Long dupCount = datasetMapper.selectCount(dup);
        if (dupCount != null && dupCount > 0) {
            throw new BizException(ErrorCode.CONFLICT, "同名数据集已存在");
        }
        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setScenario(scenario);
        dataset.setDescription(description);
        dataset.setCreator(creator);
        dataset.setFilterCond(toJson(filterCondMap == null ? Collections.emptyMap() : filterCondMap));
        datasetMapper.insert(dataset);
        return dataset.getId();
    }

    @Transactional
    public Map<String, Object> createVersion(Long datasetId, String changeNote) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BizException(ErrorCode.DATASET_NOT_FOUND);
        }
        Map<String, Object> filterCond = parseJsonMap(dataset.getFilterCond());
        List<Asset> assets = govApi.listAssetsByFilter(filterCond);
        if (assets == null || assets.isEmpty()) {
            throw new BizException(ErrorCode.NO_VISIBLE_ASSETS);
        }
        int versionNo = selectMaxVersionNo(datasetId) + 1;

        List<DatasetItem> items = new ArrayList<>();
        List<Double> completenessVals = new ArrayList<>();
        List<Double> consistencyVals = new ArrayList<>();
        List<Double> accuracyVals = new ArrayList<>();
        for (Asset asset : assets) {
            Map<String, Object> metrics = extractMetrics(asset);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", asset.getId());
            snapshot.put("name", asset.getName());
            snapshot.put("assetType", asset.getAssetType());
            snapshot.put("modality", asset.getModality());
            snapshot.put("bizDomain", asset.getBizDomain());
            snapshot.put("secretLevel", asset.getSecretLevel());
            snapshot.put("qualityScore", asset.getQualityScore());
            snapshot.put("storageRef", asset.getStorageRef());
            snapshot.put("metrics", metrics);

            DatasetItem item = new DatasetItem();
            item.setAssetId(asset.getId());
            item.setAssetSnapshot(toJson(snapshot));
            items.add(item);

            Double completeness = null;
            Number missingRate = asNumber(metrics.get("missingRate"));
            if (missingRate != null) {
                completeness = 1 - missingRate.doubleValue();
            }
            Double consistency = null;
            Number formatConsistencyRate = asNumber(metrics.get("formatConsistencyRate"));
            if (formatConsistencyRate != null) {
                consistency = formatConsistencyRate.doubleValue();
            } else {
                Number duplicateRate = asNumber(metrics.get("duplicateRate"));
                if (duplicateRate != null) {
                    consistency = 1 - duplicateRate.doubleValue();
                }
            }
            Double accuracy = null;
            Number validLineRate = asNumber(metrics.get("validLineRate"));
            if (validLineRate != null) {
                accuracy = validLineRate.doubleValue();
            } else if (asset.getQualityScore() != null) {
                accuracy = asset.getQualityScore().doubleValue() / 100.0;
            }
            if (completeness == null && consistency == null && accuracy == null) {
                completeness = 0.9;
                consistency = 0.9;
                accuracy = 0.9;
            }
            if (completeness != null) {
                completenessVals.add(completeness);
            }
            if (consistency != null) {
                consistencyVals.add(consistency);
            }
            if (accuracy != null) {
                accuracyVals.add(accuracy);
            }
        }

        Map<String, Object> report = buildQualityReport(completenessVals, consistencyVals, accuracyVals);

        DatasetVersion version = new DatasetVersion();
        version.setDatasetId(datasetId);
        version.setVersionNo(versionNo);
        version.setItemCount(items.size());
        version.setQualityReport(toJson(report));
        version.setChangeNote(changeNote);
        datasetVersionMapper.insert(version);

        for (DatasetItem item : items) {
            item.setVersionId(version.getId());
            datasetItemMapper.insert(item);
        }
        return Map.of("versionId", version.getId(), "versionNo", versionNo, "itemCount", items.size());
    }

    public List<DatasetVersion> listVersions(Long datasetId) {
        return datasetVersionMapper.selectList(new LambdaQueryWrapper<DatasetVersion>()
                .eq(DatasetVersion::getDatasetId, datasetId)
                .orderByDesc(DatasetVersion::getVersionNo));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getVersionDetail(Long versionId) {
        DatasetVersion version = datasetVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BizException(ErrorCode.VERSION_NOT_FOUND);
        }
        Map<String, Object> map = objectMapper.convertValue(version, Map.class);
        map.put("qualityReportParsed", parseJsonMap(version.getQualityReport()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (DatasetItem item : getVersionItems(versionId)) {
            Map<String, Object> m = new LinkedHashMap<>(parseJsonMap(item.getAssetSnapshot()));
            m.put("itemId", item.getId());
            m.put("versionId", item.getVersionId());
            m.put("assetId", item.getAssetId());
            items.add(m);
        }
        map.put("items", items);
        return map;
    }

    public Map<String, Object> compare(Long versionIdA, Long versionIdB) {
        Map<Long, Map<String, Object>> snapA = snapshotByAssetId(getVersionItems(versionIdA));
        Map<Long, Map<String, Object>> snapB = snapshotByAssetId(getVersionItems(versionIdB));

        List<Map<String, Object>> added = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> e : snapB.entrySet()) {
            if (!snapA.containsKey(e.getKey())) {
                added.add(assetBrief(e.getKey(), e.getValue()));
            }
        }
        List<Map<String, Object>> removed = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> e : snapA.entrySet()) {
            if (!snapB.containsKey(e.getKey())) {
                removed.add(assetBrief(e.getKey(), e.getValue()));
            }
        }

        double scoreA = overallScore(versionIdA);
        double scoreB = overallScore(versionIdB);
        Map<String, Object> metricsDiff = new LinkedHashMap<>();
        metricsDiff.put("overallScoreA", scoreA);
        metricsDiff.put("overallScoreB", scoreB);
        metricsDiff.put("delta", round4(scoreB - scoreA));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added);
        result.put("removed", removed);
        result.put("metricsDiff", metricsDiff);
        return result;
    }

    public List<DatasetItem> getVersionItems(Long versionId) {
        return datasetItemMapper.selectList(new LambdaQueryWrapper<DatasetItem>()
                .eq(DatasetItem::getVersionId, versionId)
                .orderByAsc(DatasetItem::getId));
    }

    private int selectMaxVersionNo(Long datasetId) {
        DatasetVersion latest = datasetVersionMapper.selectOne(new QueryWrapper<DatasetVersion>()
                .select("version_no")
                .eq("dataset_id", datasetId)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        return latest == null || latest.getVersionNo() == null ? 0 : latest.getVersionNo();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetrics(Asset asset) {
        Object metrics = parseJsonMap(asset.getExt()).get("metrics");
        return metrics instanceof Map ? (Map<String, Object>) metrics : Collections.emptyMap();
    }

    private Map<String, Object> buildQualityReport(List<Double> completenessVals,
                                                   List<Double> consistencyVals,
                                                   List<Double> accuracyVals) {
        double completeness = round4(avg(completenessVals));
        double consistency = round4(avg(consistencyVals));
        double accuracy = round4(avg(accuracyVals));
        double overall = round4((completeness + consistency + accuracy) / 3.0);
        boolean pass = overall >= PASS_THRESHOLD;
        String suggestion;
        if (pass) {
            suggestion = "三维质量达标，可用于产品加工";
        } else {
            List<String> weak = new ArrayList<>();
            if (completeness < PASS_THRESHOLD) {
                weak.add("完整性");
            }
            if (consistency < PASS_THRESHOLD) {
                weak.add("一致性");
            }
            if (accuracy < PASS_THRESHOLD) {
                weak.add("准确性");
            }
            suggestion = "存在质量短板（" + String.join("、", weak) + "），建议先完成数据治理再用于产品加工";
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("completeness", completeness);
        report.put("consistency", consistency);
        report.put("accuracy", accuracy);
        report.put("overall_score", overall);
        report.put("pass", pass);
        report.put("suggestion", suggestion);
        return report;
    }

    private double overallScore(Long versionId) {
        DatasetVersion version = datasetVersionMapper.selectById(versionId);
        if (version == null) {
            return 0;
        }
        Object overall = parseJsonMap(version.getQualityReport()).get("overall_score");
        return overall instanceof Number n ? n.doubleValue() : 0;
    }

    private Map<Long, Map<String, Object>> snapshotByAssetId(List<DatasetItem> items) {
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        for (DatasetItem item : items) {
            map.put(item.getAssetId(), parseJsonMap(item.getAssetSnapshot()));
        }
        return map;
    }

    private Map<String, Object> assetBrief(Long assetId, Map<String, Object> snapshot) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("id", assetId);
        brief.put("name", snapshot.get("name"));
        return brief;
    }

    private static double avg(List<Double> vals) {
        if (vals.isEmpty()) {
            return 0.9;
        }
        return vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.9);
    }

    private static double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static Number asNumber(Object o) {
        return o instanceof Number n ? n : null;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "JSON 序列化失败");
        }
    }
}
