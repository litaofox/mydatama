package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据标准表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov.data_standards")
public class DataStandard extends BaseEntity {

    private String code;
    private String name;
    private String ruleExpr;
    private String description;
    private Integer enabled;
}
