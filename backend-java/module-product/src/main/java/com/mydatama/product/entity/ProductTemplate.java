package com.mydatama.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品模板表。configSchema 为 jsonb 字符串（数据源 stringtype=unspecified）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prod.product_templates")
public class ProductTemplate extends BaseEntity {

    private String code;
    private String name;
    private String form;
    private String description;
    private String configSchema;
    private String version;
    private Integer builtin;
    private Integer enabled;
}
