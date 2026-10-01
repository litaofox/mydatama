package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 元数据字段表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov.metadata_columns")
public class MetadataColumn extends BaseEntity {

    private Long tableId;
    private String colName;
    private String dataType;
    private Integer ordinal;
    private Integer sensitive;
    private String maskStrategy;
    private String comment;
}
