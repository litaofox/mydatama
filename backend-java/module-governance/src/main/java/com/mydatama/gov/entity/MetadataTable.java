package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 元数据表结构表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov.metadata_tables")
public class MetadataTable extends BaseEntity {

    private Long assetId;
    private String tableName;
    private String comment;
    private Long rowCount;
}
