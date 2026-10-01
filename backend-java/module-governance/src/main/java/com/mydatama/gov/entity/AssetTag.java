package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 资产标签表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov.asset_tags")
public class AssetTag extends BaseEntity {

    private Long assetId;
    private String tag;
}
