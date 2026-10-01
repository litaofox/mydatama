package com.mydatama.iam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydatama.common.api.PageData;
import com.mydatama.common.api.Result;
import com.mydatama.common.security.RequirePerm;
import com.mydatama.iam.entity.Permission;
import com.mydatama.iam.entity.Policy;
import com.mydatama.iam.entity.Role;
import com.mydatama.iam.entity.User;
import com.mydatama.iam.service.IamAdminService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IAM 管理接口：用户/角色/权限点/ABAC 策略。
 */
@RestController
@RequestMapping("/api/iam")
@RequiredArgsConstructor
public class IamAdminController {

    private final IamAdminService svc;

    // ===== 用户 =====

    public record UserCreateReq(@NotBlank String username, @NotBlank String password,
                                String realName, String deptCode, Integer secretLevel,
                                List<Long> roleIds) {
    }

    public record UserUpdateReq(String realName, String deptCode, Integer secretLevel,
                                Boolean enabled, List<Long> roleIds) {
    }

    @GetMapping("/users")
    @RequirePerm("iam:user:read")
    public Result<PageData<Map<String, Object>>> users(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam(required = false) String keyword) {
        Page<User> p = svc.listUsers(page, size, keyword);
        return Result.ok(PageData.of(p, u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("deptCode", u.getDeptCode());
            m.put("secretLevel", u.getSecretLevel());
            m.put("enabled", u.getEnabled());
            m.put("lastLoginAt", u.getLastLoginAt());
            m.put("roleIds", svc.userRoleIds(u.getId()));
            return m;
        }));
    }

    @PostMapping("/users")
    @RequirePerm("iam:user:write")
    public Result<Map<String, Object>> createUser(@Validated @RequestBody UserCreateReq req) {
        User u = svc.createUser(req.username(), req.password(), req.realName(),
                req.deptCode(), req.secretLevel(), req.roleIds());
        return Result.ok(Map.of("id", u.getId(), "username", u.getUsername()));
    }

    @PutMapping("/users/{id}")
    @RequirePerm("iam:user:write")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody UserUpdateReq req) {
        svc.updateUser(id, req.realName(), req.deptCode(), req.secretLevel(), req.enabled(), req.roleIds());
        return Result.ok();
    }

    // ===== 角色 =====

    @GetMapping("/roles")
    @RequirePerm("iam:role:read")
    public Result<List<Role>> roles() {
        return Result.ok(svc.listRoles());
    }

    @GetMapping("/roles/{id}/permissions")
    @RequirePerm("iam:role:read")
    public Result<List<Long>> rolePermissions(@PathVariable Long id) {
        return Result.ok(svc.rolePermissionIds(id));
    }

    public record GrantReq(List<Long> permissionIds) {
    }

    @PutMapping("/roles/{id}/permissions")
    @RequirePerm("iam:role:write")
    public Result<Void> grant(@PathVariable Long id, @RequestBody GrantReq req) {
        svc.grantPermissions(id, req.permissionIds());
        return Result.ok();
    }

    // ===== 权限点 =====

    @GetMapping("/permissions")
    @RequirePerm("iam:role:read")
    public Result<List<Permission>> permissions() {
        return Result.ok(svc.listPermissions());
    }

    // ===== ABAC 策略 =====

    @GetMapping("/policies")
    @RequirePerm("iam:policy:read")
    public Result<List<Policy>> policies() {
        return Result.ok(svc.listPolicies());
    }

    @PostMapping("/policies")
    @RequirePerm("iam:policy:write")
    public Result<Policy> createPolicy(@RequestBody Policy policy) {
        return Result.ok(svc.createPolicy(policy));
    }

    public record ToggleReq(boolean enabled) {
    }

    @PutMapping("/policies/{id}/enabled")
    @RequirePerm("iam:policy:write")
    public Result<Void> togglePolicy(@PathVariable Long id, @RequestBody ToggleReq req) {
        svc.togglePolicy(id, req.enabled());
        return Result.ok();
    }
}
