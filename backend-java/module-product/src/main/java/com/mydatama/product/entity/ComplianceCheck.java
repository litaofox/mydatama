package com.mydatama.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合规校验记录表（只追加）。该表无 updated_at/update_by/deleted，不继承 BaseEntity。
 */
@Data
@TableName("prod.compliance_checks")
public class ComplianceCheck {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private String batchNo;
    private String ruleCode;
    private String ruleName;
    private Integer passed;
    private String detail;
    private LocalDateTime runAt;
    private String createBy;
    private LocalDateTime createdAt;
}
