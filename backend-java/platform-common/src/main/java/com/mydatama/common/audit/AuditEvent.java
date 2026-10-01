package com.mydatama.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审计事件（内存队列传输，异步批量落 iam.audit_logs）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

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
}
