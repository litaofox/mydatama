package com.mydatama.gov.controller;

import com.mydatama.common.api.PageData;
import com.mydatama.common.api.Result;
import com.mydatama.common.security.RequirePerm;
import com.mydatama.gov.entity.Asset;
import com.mydatama.gov.service.AssetService;
import com.mydatama.gov.service.HeatmapService;
import com.mydatama.gov.service.LineageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据资产查询/血缘/热力图。
 */
@RestController
@RequestMapping("/api/governance/assets")
@RequiredArgsConstructor
public class GovAssetController {

    private final AssetService assetService;
    private final LineageService lineageService;
    private final HeatmapService heatmapService;

    @GetMapping
    @RequirePerm("gov:asset:read")
    public Result<PageData<Asset>> page(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String modality,
                                        @RequestParam(required = false) String assetType,
                                        @RequestParam(required = false) String bizDomain,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(PageData.of(assetService.page(keyword, modality, assetType, bizDomain, page, size), a -> a));
    }

    @GetMapping("/{id}")
    @RequirePerm("gov:asset:read")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(assetService.detail(id));
    }

    @GetMapping("/lineage/{id}")
    @RequirePerm("gov:lineage:read")
    public Result<Map<String, Object>> lineage(@PathVariable Long id,
                                               @RequestParam(defaultValue = "3") int depth) {
        return Result.ok(lineageService.graph(id, depth));
    }

    @GetMapping("/heatmap")
    @RequirePerm("gov:asset:read")
    public Result<Map<String, Object>> heatmap(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(heatmapService.heatmap(days));
    }
}
