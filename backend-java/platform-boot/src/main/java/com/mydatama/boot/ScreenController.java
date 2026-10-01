package com.mydatama.boot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mydatama.common.api.Result;
import com.mydatama.common.security.RequirePerm;
import com.mydatama.ds.mapper.DatasetMapper;
import com.mydatama.ds.mapper.DatasetVersionMapper;
import com.mydatama.gov.entity.Asset;
import com.mydatama.gov.entity.ProcDataFile;
import com.mydatama.gov.mapper.AssetMapper;
import com.mydatama.gov.mapper.AssetUsageStatMapper;
import com.mydatama.gov.mapper.ProcDataFileMapper;
import com.mydatama.iam.entity.AuditLog;
import com.mydatama.iam.mapper.AuditLogMapper;
import com.mydatama.product.entity.Product;
import com.mydatama.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据大屏聚合（虚拟模块，跨模块直接注入 Mapper 聚合统计）。
 */
@RestController
@RequestMapping("/api/screen")
@RequiredArgsConstructor
public class ScreenController {

    private final AssetMapper assetMapper;
    private final ProcDataFileMapper procDataFileMapper;
    private final AssetUsageStatMapper assetUsageStatMapper;
    private final DatasetMapper datasetMapper;
    private final DatasetVersionMapper datasetVersionMapper;
    private final ProductMapper productMapper;
    private final AuditLogMapper auditLogMapper;

    @GetMapping("/overview")
    @RequirePerm("scr:screen:view")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("assetCount", assetMapper.selectCount(null));
        data.put("fileCount", procDataFileMapper.selectCount(null));
        data.put("datasetCount", datasetMapper.selectCount(null));
        data.put("productCount", productMapper.selectCount(null));

        List<Map<String, Object>> avgRows = assetMapper.selectMaps(
                new QueryWrapper<Asset>().select("AVG(quality_score) AS v"));
        double avgQuality = 0;
        if (!avgRows.isEmpty() && avgRows.get(0) != null && avgRows.get(0).get("v") != null) {
            avgQuality = new BigDecimal(avgRows.get(0).get("v").toString())
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
        data.put("avgQuality", avgQuality);

        data.put("todayUploads", procDataFileMapper.selectCount(
                new QueryWrapper<ProcDataFile>().ge("created_at", LocalDate.now().atStartOfDay())));
        return Result.ok(data);
    }

    @GetMapping("/trend")
    @RequirePerm("scr:screen:view")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "7") int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);

        List<String> dates = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            dates.add(today.minusDays(i).toString());
        }

        Map<String, Long> uploadMap = countByDate(procDataFileMapper.selectMaps(
                new QueryWrapper<ProcDataFile>()
                        .select("created_at::date AS d", "COUNT(*) AS c")
                        .ge("created_at", from.atStartOfDay())
                        .groupBy("created_at::date")));
        Map<String, Long> assetMap = countByDate(assetMapper.selectMaps(
                new QueryWrapper<Asset>()
                        .select("created_at::date AS d", "COUNT(*) AS c")
                        .ge("created_at", from.atStartOfDay())
                        .groupBy("created_at::date")));

        List<Long> uploads = new ArrayList<>();
        List<Long> assetsReady = new ArrayList<>();
        for (String d : dates) {
            uploads.add(uploadMap.getOrDefault(d, 0L));
            assetsReady.add(assetMap.getOrDefault(d, 0L));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dates", dates);
        data.put("uploads", uploads);
        data.put("assetsReady", assetsReady);
        return Result.ok(data);
    }

    @GetMapping("/distribution")
    @RequirePerm("scr:screen:view")
    public Result<Map<String, Object>> distribution() {
        Map<String, Object> data = new HashMap<>();
        data.put("modalityDist", nameValueList(assetMapper.selectMaps(
                new QueryWrapper<Asset>()
                        .select("COALESCE(modality, 'UNKNOWN') AS name", "COUNT(*) AS value")
                        .groupBy("modality"))));
        data.put("domainDist", nameValueList(assetMapper.selectMaps(
                new QueryWrapper<Asset>()
                        .select("COALESCE(biz_domain, 'UNKNOWN') AS name", "COUNT(*) AS value")
                        .groupBy("biz_domain"))));
        return Result.ok(data);
    }

    @GetMapping("/ops")
    @RequirePerm("scr:screen:view")
    public Result<Map<String, Object>> ops() {
        List<AuditLog> logs = auditLogMapper.selectList(
                new QueryWrapper<AuditLog>().orderByDesc("id").last("LIMIT 20"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (AuditLog l : logs) {
            Map<String, Object> row = new HashMap<>();
            row.put("username", l.getUsername());
            row.put("action", l.getAction());
            row.put("path", l.getPath());
            row.put("createdAt", l.getCreatedAt());
            list.add(row);
        }
        return Result.ok(Map.of("list", list));
    }

    @GetMapping("/product")
    @RequirePerm("scr:screen:view")
    public Result<Map<String, Object>> product() {
        Map<String, Object> data = new HashMap<>();
        long total = productMapper.selectCount(null);
        long listed = productMapper.selectCount(new QueryWrapper<Product>().eq("status", "LISTED"));
        long blocked = productMapper.selectCount(new QueryWrapper<Product>().eq("status", "BLOCKED"));
        long passed = productMapper.selectCount(new QueryWrapper<Product>().eq("status", "PASSED"));
        data.put("totalProducts", total);
        data.put("listedProducts", listed);
        data.put("blockedProducts", blocked);
        double rate = 0;
        if (passed + blocked > 0) {
            rate = BigDecimal.valueOf(passed * 100.0 / (passed + blocked))
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
        data.put("compliancePassRate", rate);

        List<Map<String, Object>> formDist = new ArrayList<>();
        for (Map<String, Object> row : productMapper.selectMaps(
                new QueryWrapper<Product>().select("form", "COUNT(*) AS count").groupBy("form"))) {
            Map<String, Object> item = new HashMap<>();
            item.put("form", row.get("form"));
            item.put("count", row.get("count"));
            formDist.add(item);
        }
        data.put("formDistribution", formDist);

        List<Map<String, Object>> latest = new ArrayList<>();
        for (Product p : productMapper.selectList(
                new QueryWrapper<Product>().orderByDesc("id").last("LIMIT 5"))) {
            Map<String, Object> item = new HashMap<>();
            item.put("code", p.getCode());
            item.put("name", p.getName());
            item.put("status", p.getStatus());
            item.put("listedAt", p.getListedAt());
            latest.add(item);
        }
        data.put("latest", latest);
        return Result.ok(data);
    }

    private Map<String, Long> countByDate(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            if (row.get("d") != null && row.get("c") != null) {
                map.put(row.get("d").toString(), ((Number) row.get("c")).longValue());
            }
        }
        return map;
    }

    private List<Map<String, Object>> nameValueList(List<Map<String, Object>> rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", row.get("name") == null ? "UNKNOWN" : row.get("name").toString());
            item.put("value", row.get("value"));
            list.add(item);
        }
        return list;
    }
}
