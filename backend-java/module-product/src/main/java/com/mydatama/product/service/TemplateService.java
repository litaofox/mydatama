package com.mydatama.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydatama.product.entity.ProductTemplate;
import com.mydatama.product.mapper.ProductTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 产品模板查询。
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ProductTemplateMapper productTemplateMapper;

    /** 启用模板列表（enabled=1 按 id 升序）。 */
    public List<ProductTemplate> listEnabled() {
        return productTemplateMapper.selectList(new LambdaQueryWrapper<ProductTemplate>()
                .eq(ProductTemplate::getEnabled, 1)
                .orderByAsc(ProductTemplate::getId));
    }
}
