package com.mydatama.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydatama.common.audit.AuditEvent;
import com.mydatama.common.audit.AuditPersister;
import com.mydatama.iam.entity.AuditLog;
import com.mydatama.iam.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审计服务：查询/导出/批量落库（实现 AuditPersister 供 common 的 AuditFlusher 回调）。
 */
@Service
@RequiredArgsConstructor
public class AuditService implements AuditPersister {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogMapper auditLogMapper;

    public Page<AuditLog> query(String username, String action, String from, String to, int page, int size) {
        LambdaQueryWrapper<AuditLog> q = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) {
            q.eq(AuditLog::getUsername, username);
        }
        if (action != null && !action.isBlank()) {
            q.eq(AuditLog::getAction, action);
        }
        if (from != null && !from.isBlank()) {
            q.ge(AuditLog::getCreatedAt, LocalDateTime.parse(from + " 00:00:00", FMT));
        }
        if (to != null && !to.isBlank()) {
            q.le(AuditLog::getCreatedAt, LocalDateTime.parse(to + " 23:59:59", FMT));
        }
        q.orderByDesc(AuditLog::getId);
        return auditLogMapper.selectPage(new Page<>(page, size), q);
    }

    public String exportCsv(String username, String action, String from, String to) {
        List<AuditLog> logs = query(username, action, from, to, 1, 5000).getRecords();
        StringBuilder sb = new StringBuilder("﻿id,username,action,method,path,statusCode,ip,costMs,createdAt\n");
        for (AuditLog l : logs) {
            sb.append(l.getId()).append(',')
                    .append(csv(l.getUsername())).append(',')
                    .append(csv(l.getAction())).append(',')
                    .append(csv(l.getMethod())).append(',')
                    .append(csv(l.getPath())).append(',')
                    .append(l.getStatusCode()).append(',')
                    .append(csv(l.getIp())).append(',')
                    .append(l.getCostMs()).append(',')
                    .append(l.getCreatedAt() == null ? "" : FMT.format(l.getCreatedAt()))
                    .append('\n');
        }
        return sb.toString();
    }

    private String csv(String s) {
        if (s == null) {
            return "";
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    @Override
    public void saveBatch(List<AuditEvent> events) {
        for (AuditEvent e : events) {
            AuditLog log = new AuditLog();
            log.setUsername(e.getUsername());
            log.setUserId(e.getUserId());
            log.setAction(e.getAction());
            log.setResource(e.getResource());
            log.setMethod(e.getMethod());
            log.setPath(e.getPath());
            log.setStatusCode(e.getStatusCode());
            log.setIp(e.getIp());
            log.setUserAgent(e.getUserAgent());
            log.setCostMs(e.getCostMs());
            log.setDetail(e.getDetail());
            log.setTraceId(e.getTraceId());
            auditLogMapper.insert(log);
        }
    }
}
