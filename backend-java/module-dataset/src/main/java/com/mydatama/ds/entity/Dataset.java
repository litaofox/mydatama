package com.mydatama.ds.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据集定义表。filterCond 为 jsonb 字符串（数据源 stringtype=unspecified）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ds.datasets")
public class Dataset extends BaseEntity {

    private String name;
    private String scenario;
    private String description;
    private String filterCond;
    private String creator;
}
