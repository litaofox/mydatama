package com.mydatama.iam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志（只追加，无软删/更新字段）。
 */
@Data
@TableName("iam.audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private Long userId;
    private String action;
    private String resource;
    private String method;
    private String path;
    private Integer statusCode;
    private String ip;
    private String userAgent;
    private Long costMs;
    private String detail;
    private String traceId;
    private LocalDateTime createdAt;
}
