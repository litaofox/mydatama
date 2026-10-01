package com.mydatama.ds.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集版本表（快照不可变）。qualityReport 为 jsonb 字符串（数据源 stringtype=unspecified）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ds.dataset_versions")
public class DatasetVersion extends BaseEntity {

    private Long datasetId;
    private Integer versionNo;
    private Integer itemCount;
    private String qualityReport;
    private String changeNote;
}
