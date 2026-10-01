package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 数据资产表。ext 为 jsonb 字符串（数据源 stringtype=unspecified）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov.assets")
public class Asset extends BaseEntity {

    private String name;
    private String assetType;
    private String modality;
    private String bizDomain;
    private Integer secretLevel;
    private String ownerDept;
    private String storageRef;
    private Long sourceFileId;
    private BigDecimal qualityScore;
    private String status;
    private String ext;
}
