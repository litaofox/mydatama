package com.mydatama.boot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.api.Result;
import com.mydatama.ds.api.DsApi;
import com.mydatama.ds.entity.DatasetItem;
import com.mydatama.gov.api.GovApi;
import com.mydatama.iam.api.IamApi;
import com.mydatama.product.entity.Product;
import com.mydatama.product.mapper.ProductMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 开放 API（X-API-Key 认证，不走 JWT）。
 */
@RestController
@RequestMapping("/openapi/v1")
@RequiredArgsConstructor
public class OpenApiController {

    private static final Set<String> READABLE_STATUS = Set.of("PASSED", "REGISTERED", "LISTED");

    private final IamApi iamApi;
    private final GovApi govApi;
    private final DsApi dsApi;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    @GetMapping("/products/{code}/rows")
    public Result<Map<String, Object>> rows(@PathVariable String code,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "100") int size,
                                            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                            HttpServletRequest request) {
        if (apiKey == null || apiKey.isBlank()
                || iamApi.validateApiKey(apiKey, request.getRemoteAddr()) == null) {
            return Result.error(ErrorCode.UNAUTHORIZED.getCode(), "API Key 无效");
        }

        Product product = productMapper.selectOne(new QueryWrapper<Product>().eq("code", code));
        if (product == null) {
            return Result.error(600001, "产品不存在");
        }
        if (!READABLE_STATUS.contains(product.getStatus())) {
            return Result.error(600003, "产品未通过合规或未挂牌");
        }

        List<DatasetItem> items = dsApi.getVersionItems(product.getDatasetVersionId());
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Long> assetIds = new ArrayList<>();
        if (items != null) {
            for (DatasetItem item : items) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = objectMapper.readValue(item.getAssetSnapshot(), Map.class);
                    rows.add(row);
                    assetIds.add(item.getAssetId());
                } catch (Exception ignored) {
                    // 快照解析失败的行跳过
                }
            }
        }

        int pageNo = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 1000);
        int fromIndex = Math.min((pageNo - 1) * pageSize, rows.size());
        int toIndex = Math.min(fromIndex + pageSize, rows.size());
        List<Map<String, Object>> pageRows = rows.subList(fromIndex, toIndex);

        for (int i = fromIndex; i < toIndex; i++) {
            Long assetId = assetIds.get(i);
            if (assetId != null) {
                try {
                    govApi.recordUsage(assetId);
                } catch (Exception ignored) {
                    // 计量失败跳过单行
                }
            }
        }

        return Result.ok(Map.of("total", rows.size(), "list", pageRows));
    }
}
