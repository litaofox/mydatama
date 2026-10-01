package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 质量规则表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov.quality_rules")
public class QualityRule extends BaseEntity {

    private String targetRef;
    private String checkType;
    private String expr;
    private Long standardId;
    private Integer enabled;
}
