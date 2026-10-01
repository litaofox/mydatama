package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质量任务结果表（无 updated_at/update_by/deleted 列，不继承 BaseEntity）。
 * detail 为 jsonb 字符串。
 */
@Data
@TableName("gov.quality_task_results")
public class QualityTaskResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private LocalDateTime runAt;
    private Long totalCount;
    private Long passCount;
    private BigDecimal passRate;
    private String detail;
    private String createBy;
    private LocalDateTime createdAt;
}
