package com.mydatama.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * condition_tree 为 jsonb 字符串（数据源 stringtype=unspecified）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iam.policies")
public class Policy extends BaseEntity {

    private String code;
    private String name;
    private String description;
    private String resource;
    private String action;
    private String effect;
    private String conditionTree;
    private Integer priority;
    private Boolean enabled;
}
