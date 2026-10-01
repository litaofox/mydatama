package com.mydatama.product.controller;

import com.mydatama.common.api.PageData;
import com.mydatama.common.api.Result;
import com.mydatama.common.security.RequirePerm;
import com.mydatama.product.entity.Product;
import com.mydatama.product.entity.ProductTemplate;
import com.mydatama.product.service.ComplianceEngine;
import com.mydatama.product.service.GenerateEngine;
import com.mydatama.product.service.ProductExportService;
import com.mydatama.product.service.ProductService;
import com.mydatama.product.service.TemplateService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PROD 数据产品：模板/配置/生成/合规/登记挂牌/导出/说明书预览。
 */
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final TemplateService templateService;
    private final ProductService productService;
    private final GenerateEngine generateEngine;
    private final ComplianceEngine complianceEngine;
    private final ProductExportService productExportService;

    /** 模板列表（enabled=1）。 */
    @GetMapping("/templates")
    @RequirePerm("prod:template:read")
    public Result<List<ProductTemplate>> templates() {
        return Result.ok(templateService.listEnabled());
    }

    /** 创建产品（DRAFT）。 */
    @PostMapping("/products")
    @RequirePerm("prod:product:write")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> req) {
        Long id = productService.create(req);
        return Result.ok(Map.of("id", id, "status", "DRAFT"));
    }

    /** 产品列表（分页）。 */
    @GetMapping("/products")
    @RequirePerm("prod:product:read")
    public Result<PageData<Product>> page(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String form,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(PageData.of(productService.page(status, form, keyword, page, size), p -> p));
    }

    /** 产品详情（含最新合规结果 + 产物清单）。 */
    @GetMapping("/products/{id}")
    @RequirePerm("prod:product:read")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    /** 更新配置（仅 DRAFT/BLOCKED）。 */
    @PutMapping("/products/{id}")
    @RequirePerm("prod:product:write")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        productService.update(id, req);
        return Result.ok();
    }

    /** 提交配置 → CONFIGURED。 */
    @PostMapping("/products/{id}/configure")
    @RequirePerm("prod:product:write")
    public Result<Map<String, Object>> configure(@PathVariable Long id) {
        productService.configure(id);
        return Result.ok(Map.of("status", "CONFIGURED"));
    }

    /** 执行生成引擎 → GENERATED（API_SERVICE 附加 apiKey 明文）。 */
    @PostMapping("/products/{id}/generate")
    @RequirePerm("prod:product:generate")
    public Result<Map<String, Object>> generate(@PathVariable Long id) {
        String apiKey = generateEngine.generate(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "GENERATED");
        if (apiKey != null) {
            data.put("apiKey", apiKey);
        }
        return Result.ok(data);
    }

    /** 执行合规校验 → PASSED/BLOCKED。 */
    @PostMapping("/products/{id}/compliance/run")
    @RequirePerm("prod:compliance:run")
    public Result<Map<String, Object>> complianceRun(@PathVariable Long id) {
        return Result.ok(complianceEngine.run(id));
    }

    /** 最新批次合规明细。 */
    @GetMapping("/products/{id}/compliance")
    @RequirePerm("prod:compliance:read")
    public Result<Map<String, Object>> complianceLatest(@PathVariable Long id) {
        return Result.ok(complianceEngine.latest(id));
    }

    /** 登记 → REGISTERED（生成 regNo）。 */
    @PostMapping("/products/{id}/register")
    @RequirePerm("prod:product:register")
    public Result<Map<String, Object>> register(@PathVariable Long id) {
        Product product = productService.register(id);
        return Result.ok(Map.of("status", product.getStatus(), "regNo", product.getRegNo()));
    }

    /** 挂牌 → LISTED。 */
    @PostMapping("/products/{id}/listing")
    @RequirePerm("prod:product:listing")
    public Result<Map<String, Object>> listing(@PathVariable Long id) {
        Product product = productService.listing(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", product.getStatus());
        data.put("listedAt", product.getListedAt() == null
                ? null
                : product.getListedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return Result.ok(data);
    }

    /** 导出交付物（文件流，不写 Result）。 */
    @GetMapping("/products/{id}/export")
    @RequirePerm("prod:product:export")
    public void export(@PathVariable Long id, HttpServletResponse response) throws Exception {
        productExportService.export(id, response);
    }

    /** 说明书 HTML 在线预览。 */
    @GetMapping(value = "/products/{id}/manual", produces = MediaType.TEXT_HTML_VALUE)
    @RequirePerm("prod:product:read")
    public String manual(@PathVariable Long id) {
        return productExportService.manualHtml(id);
    }
}
