package com.mydatama.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品产物表。ext 为 jsonb 字符串；filePath 为 /data 卷相对路径 products/{code}/...。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prod.product_artifacts")
public class ProductArtifact extends BaseEntity {

    private Long productId;
    private String artifactType;
    private String filePath;
    private Long fileSize;
    private String checksum;
    private String ext;
}
