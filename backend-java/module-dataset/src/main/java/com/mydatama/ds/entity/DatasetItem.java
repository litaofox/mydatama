package com.mydatama.ds.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集项表（版本内资产明细）。assetSnapshot 为 jsonb 字符串（数据源 stringtype=unspecified）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ds.dataset_items")
public class DatasetItem extends BaseEntity {

    private Long versionId;
    private Long assetId;
    private String assetSnapshot;
}
