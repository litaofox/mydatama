package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 血缘边表（有向：上游→下游）。ext 为 jsonb 字符串。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gov.lineage_edges")
public class LineageEdge extends BaseEntity {

    private Long fromAssetId;
    private Long toAssetId;
    private String relType;
    private String ext;
}
