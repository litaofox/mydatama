package com.mydatama.iam.controller;

import com.mydatama.common.api.PageData;
import com.mydatama.common.api.Result;
import com.mydatama.common.security.RequirePerm;
import com.mydatama.iam.entity.AuditLog;
import com.mydatama.iam.service.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 审计日志查询/导出。
 */
@RestController
@RequestMapping("/api/iam/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @RequirePerm("iam:audit:read")
    public Result<PageData<AuditLog>> query(@RequestParam(required = false) String username,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(PageData.of(auditService.query(username, action, from, to, page, size), l -> l));
    }

    @GetMapping("/export")
    @RequirePerm("iam:audit:export")
    public void export(@RequestParam(required = false) String username,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       HttpServletResponse response) throws IOException {
        String csv = auditService.exportCsv(username, action, from, to);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=audit-logs.csv");
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
    }
}
