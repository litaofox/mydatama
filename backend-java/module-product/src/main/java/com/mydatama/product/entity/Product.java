package com.mydatama.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品实例表。manualMeta/configParams 为 jsonb 字符串。
 * 状态机：DRAFT→CONFIGURED→GENERATED→CHECKING→PASSED/BLOCKED→REGISTERED→LISTED。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prod.products")
public class Product extends BaseEntity {

    private String code;
    private String name;
    private Long templateId;
    private String form;
    private Long datasetId;
    private Long datasetVersionId;
    private Integer datasetVersionNo;
    private Integer secretLevel;
    private String category;
    private String pricingModel;
    private BigDecimal price;
    private String description;
    private String provider;
    private String manualMeta;
    private String configParams;
    private String status;
    private String regNo;
    private LocalDateTime listedAt;
    private String lastError;
}
