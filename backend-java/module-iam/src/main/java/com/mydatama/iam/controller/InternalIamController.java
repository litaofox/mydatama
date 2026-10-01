package com.mydatama.iam.controller;

import com.mydatama.common.api.Result;
import com.mydatama.common.audit.AuditEvent;
import com.mydatama.common.audit.AuditSink;
import com.mydatama.common.security.UserInfo;
import com.mydatama.iam.api.IamApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部接口（API-COM-001）：processing-app 回调。X-Service-Token 由 ServiceTokenFilter 校验。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalIamController {

    private final IamApi iamApi;
    private final AuditSink auditSink;

    /**
     * 权限查询：processing-app 校验某用户是否有某权限点。
     * 入参 {username, permission} → data {allowed}
     */
    @PostMapping("/authz")
    public Result<Map<String, Object>> authz(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String permission = req.get("permission");
        boolean allowed = false;
        try {
            UserInfo u = iamApi.loadUserInfo(username);
            allowed = u.getRoles().contains("admin")
                    || (u.getPermissions() != null && u.getPermissions().contains(permission));
        } catch (Exception ignored) {
            // 用户不存在按不允许处理
        }
        Map<String, Object> data = new HashMap<>();
        data.put("allowed", allowed);
        return Result.ok(data);
    }

    /**
     * 审计事件接收：processing-app 异步上报。
     */
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody AuditEvent event) {
        auditSink.offer(event);
        return Result.ok();
    }
}
