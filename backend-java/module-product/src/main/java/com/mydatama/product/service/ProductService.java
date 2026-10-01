package com.mydatama.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.common.util.CodecUtil;
import com.mydatama.ds.api.DsApi;
import com.mydatama.ds.entity.DatasetItem;
import com.mydatama.ds.entity.DatasetVersion;
import com.mydatama.product.entity.Product;
import com.mydatama.product.entity.ProductArtifact;
import com.mydatama.product.entity.ProductTemplate;
import com.mydatama.product.mapper.ProductArtifactMapper;
import com.mydatama.product.mapper.ProductMapper;
import com.mydatama.product.mapper.ProductTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 产品配置与状态机服务。
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductTemplateMapper productTemplateMapper;
    private final ProductArtifactMapper productArtifactMapper;
    private final DsApi dsApi;
    private final ComplianceEngine complianceEngine;
    private final ObjectMapper objectMapper;

    /** 创建产品（DRAFT）。返回产品ID。 */
    public Long create(Map<String, Object> req) {
        Long templateId = toLong(req.get("templateId"));
        ProductTemplate template = templateId == null ? null : productTemplateMapper.selectById(templateId);
        if (template == null || template.getEnabled() == null || template.getEnabled() != 1) {
            throw new BizException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        String code = asText(req.get("code"));
        String name = asText(req.get("name"));
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "产品编码与名称必填");
        }
        assertCodeUnique(code, null);

        Long datasetVersionId = toLong(req.get("datasetVersionId"));
        DatasetVersion version = datasetVersionId == null ? null : dsApi.getVersionById(datasetVersionId);
        if (version == null) {
            throw new BizException(600004, "原料数据集版本不存在");
        }
        Long datasetId = toLong(req.get("datasetId"));
        if (datasetId != null && !datasetId.equals(version.getDatasetId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "datasetId 与版本归属不一致");
        }

        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setTemplateId(template.getId());
        product.setForm(template.getForm());
        product.setDatasetId(version.getDatasetId());
        product.setDatasetVersionId(version.getId());
        product.setDatasetVersionNo(version.getVersionNo());
        Integer secretLevel = toInt(req.get("secretLevel"));
        product.setSecretLevel(secretLevel == null ? 1 : secretLevel);
        product.setCategory(asText(req.get("category")));
        product.setPricingModel(asText(req.get("pricingModel")));
        product.setPrice(toBigDecimal(req.get("price")));
        product.setDescription(asText(req.get("description")));
        product.setProvider(StringUtils.hasText(asText(req.get("provider"))) ? asText(req.get("provider")) : "mydatama");
        product.setManualMeta(toJson(req.get("manualMeta")));
        product.setConfigParams(toJson(req.get("configParams")));
        product.setStatus("DRAFT");
        productMapper.insert(product);
        return product.getId();
    }

    /** 更新配置（仅 DRAFT/BLOCKED；LISTED 禁止修改）。 */
    public void update(Long id, Map<String, Object> req) {
        Product product = mustGet(id);
        if ("LISTED".equals(product.getStatus())) {
            throw new BizException(600010, "产品已挂牌，禁止修改");
        }
        assertStatus(product, "DRAFT", "BLOCKED");

        if (req.containsKey("code")) {
            String code = asText(req.get("code"));
            if (StringUtils.hasText(code) && !code.equals(product.getCode())) {
                assertCodeUnique(code, id);
                product.setCode(code);
            }
        }
        if (req.containsKey("name")) {
            product.setName(asText(req.get("name")));
        }
        if (req.containsKey("secretLevel")) {
            Integer secretLevel = toInt(req.get("secretLevel"));
            if (secretLevel != null) {
                product.setSecretLevel(secretLevel);
            }
        }
        if (req.containsKey("category")) {
            product.setCategory(asText(req.get("category")));
        }
        if (req.containsKey("pricingModel")) {
            product.setPricingModel(asText(req.get("pricingModel")));
        }
        if (req.containsKey("price")) {
            product.setPrice(toBigDecimal(req.get("price")));
        }
        if (req.containsKey("description")) {
            product.setDescription(asText(req.get("description")));
        }
        if (req.containsKey("manualMeta")) {
            product.setManualMeta(toJson(req.get("manualMeta")));
        }
        if (req.containsKey("configParams")) {
            product.setConfigParams(toJson(req.get("configParams")));
        }
        if (req.containsKey("datasetVersionId")) {
            Long datasetVersionId = toLong(req.get("datasetVersionId"));
            DatasetVersion version = datasetVersionId == null ? null : dsApi.getVersionById(datasetVersionId);
            if (version == null) {
                throw new BizException(600004, "原料数据集版本不存在");
            }
            product.setDatasetVersionId(version.getId());
            product.setDatasetId(version.getDatasetId());
            product.setDatasetVersionNo(version.getVersionNo());
        }
        productMapper.updateById(product);
    }

    /** 提交配置：DRAFT → CONFIGURED，含密级/说明书要素预检。 */
    public void configure(Long id) {
        Product product = mustGet(id);
        assertStatus(product, "DRAFT");

        DatasetVersion version = dsApi.getVersionById(product.getDatasetVersionId());
        if (version == null) {
            throw new BizException(600004, "原料数据集版本不存在");
        }
        List<DatasetItem> items = dsApi.getVersionItems(version.getId());
        int maxSecret = maxSnapshotSecretLevel(items);
        if (product.getSecretLevel() != null && product.getSecretLevel() < maxSecret) {
            throw new BizException(600008, "产品密级不能低于原料密级");
        }
        List<String> missing = ComplianceEngine.missingManualKeys(parseMap(product.getManualMeta()));
        if (!missing.isEmpty()) {
            throw new BizException(600006, "配置不完整，无法生成");
        }
        product.setStatus("CONFIGURED");
        product.setLastError(null);
        productMapper.updateById(product);
    }

    /** 登记：PASSED → REGISTERED，生成登记凭证号。 */
    public Product register(Long id) {
        Product product = mustGet(id);
        if (!"PASSED".equals(product.getStatus())) {
            throw new BizException(ErrorCode.REGISTER_NOT_ALLOWED);
        }
        product.setRegNo("REG-" + CodecUtil.today() + "-" + CodecUtil.randomDigits(6));
        product.setStatus("REGISTERED");
        productMapper.updateById(product);
        return product;
    }

    /** 挂牌：REGISTERED → LISTED。 */
    public Product listing(Long id) {
        Product product = mustGet(id);
        if (!"REGISTERED".equals(product.getStatus())) {
            throw new BizException(ErrorCode.LISTING_NOT_ALLOWED);
        }
        product.setStatus("LISTED");
        product.setListedAt(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    /** 分页列表。 */
    public IPage<Product> page(String status, String form, String keyword, int page, int size) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            qw.eq(Product::getStatus, status);
        }
        if (StringUtils.hasText(form)) {
            qw.eq(Product::getForm, form);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Product::getName, keyword).or().like(Product::getCode, keyword));
        }
        qw.orderByDesc(Product::getId);
        return productMapper.selectPage(new Page<>(page, size), qw);
    }

    /** 详情：产品全字段 + templateName + latestCompliance + artifacts。 */
    public Map<String, Object> detail(Long id) {
        Product product = mustGet(id);
        Map<String, Object> map = objectMapper.convertValue(product, new TypeReference<>() {
        });
        ProductTemplate template = productTemplateMapper.selectById(product.getTemplateId());
        map.put("templateName", template == null ? null : template.getName());
        map.put("latestCompliance", complianceEngine.latest(id));
        List<ProductArtifact> artifacts = productArtifactMapper.selectList(
                new LambdaQueryWrapper<ProductArtifact>()
                        .eq(ProductArtifact::getProductId, id)
                        .orderByAsc(ProductArtifact::getId));
        map.put("artifacts", artifacts);
        return map;
    }

    /** 引擎回写状态/失败摘要。 */
    public void markStatus(Long id, String status, String lastError) {
        Product update = new Product();
        update.setId(id);
        if (StringUtils.hasText(status)) {
            update.setStatus(status);
        }
        if (lastError != null && lastError.length() > 500) {
            lastError = lastError.substring(0, 500);
        }
        update.setLastError(lastError);
        productMapper.updateById(update);
    }

    /** 取产品，不存在抛 600001。 */
    public Product mustGet(Long id) {
        Product product = id == null ? null : productMapper.selectById(id);
        if (product == null) {
            throw new BizException(600001, "产品不存在");
        }
        return product;
    }

    /** 状态断言。 */
    private void assertStatus(Product product, String... allowed) {
        for (String s : allowed) {
            if (s.equals(product.getStatus())) {
                return;
            }
        }
        throw new BizException(ErrorCode.PRODUCT_STATE_ERROR);
    }

    /** items 快照中最大 secretLevel（无快照为 1）。 */
    public int maxSnapshotSecretLevel(List<DatasetItem> items) {
        int max = 1;
        if (items == null) {
            return max;
        }
        for (DatasetItem item : items) {
            Map<String, Object> snapshot = parseMap(item.getAssetSnapshot());
            Object level = snapshot.get("secretLevel");
            if (level instanceof Number n) {
                max = Math.max(max, n.intValue());
            }
        }
        return max;
    }

    /** 解析 jsonb 字符串为 Map（空/非法返回空 Map）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<Product>().eq(Product::getCode, code);
        if (excludeId != null) {
            qw.ne(Product::getId, excludeId);
        }
        Long count = productMapper.selectCount(qw);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PRODUCT_CODE_EXISTS);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "jsonb 字段序列化失败");
        }
    }

    static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static Integer toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String asText(Object value) {
        return value == null ? null : value.toString();
    }
}
