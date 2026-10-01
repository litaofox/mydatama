package com.mydatama.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.iam.entity.Permission;
import com.mydatama.iam.entity.Policy;
import com.mydatama.iam.entity.Role;
import com.mydatama.iam.entity.RolePermission;
import com.mydatama.iam.entity.User;
import com.mydatama.iam.entity.UserRole;
import com.mydatama.iam.mapper.PermissionMapper;
import com.mydatama.iam.mapper.PolicyMapper;
import com.mydatama.iam.mapper.RoleMapper;
import com.mydatama.iam.mapper.RolePermissionMapper;
import com.mydatama.iam.mapper.UserMapper;
import com.mydatama.iam.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * IAM 管理：用户/角色/权限点/ABAC 策略。
 */
@Service
@RequiredArgsConstructor
public class IamAdminService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PolicyMapper policyMapper;
    private final AuthService authService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ===== 用户 =====

    public Page<User> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            q.like(User::getUsername, keyword).or().like(User::getRealName, keyword);
        }
        q.orderByAsc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), q);
        result.getRecords().forEach(u -> u.setPasswordHash(null));
        return result;
    }

    @Transactional
    public User createUser(String username, String password, String realName,
                           String deptCode, Integer secretLevel, List<Long> roleIds) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(password));
        u.setRealName(realName);
        u.setDeptCode(deptCode);
        u.setSecretLevel(secretLevel == null ? 1 : secretLevel);
        u.setEnabled(true);
        userMapper.insert(u);
        bindRoles(u.getId(), roleIds);
        u.setPasswordHash(null);
        return u;
    }

    @Transactional
    public void updateUser(Long id, String realName, String deptCode, Integer secretLevel,
                           Boolean enabled, List<Long> roleIds) {
        User u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (realName != null) {
            u.setRealName(realName);
        }
        if (deptCode != null) {
            u.setDeptCode(deptCode);
        }
        if (secretLevel != null) {
            u.setSecretLevel(secretLevel);
        }
        if (enabled != null) {
            u.setEnabled(enabled);
        }
        userMapper.updateById(u);
        if (roleIds != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
            bindRoles(id, roleIds);
        }
        authService.evictUser(id);
    }

    public List<Long> userRoleIds(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
    }

    private void bindRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            try {
                userRoleMapper.insert(ur);
            } catch (Exception ignored) {
                // 重复绑定忽略
            }
        }
    }

    // ===== 角色 =====

    public List<Role> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
    }

    public List<Long> rolePermissionIds(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId))
                .stream().map(RolePermission::getPermissionId).toList();
    }

    @Transactional
    public void grantPermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(pid);
                try {
                    rolePermissionMapper.insert(rp);
                } catch (Exception ignored) {
                    // 重复忽略
                }
            }
        }
    }

    // ===== 权限点 =====

    public List<Permission> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getId));
    }

    // ===== ABAC 策略 =====

    public List<Policy> listPolicies() {
        return policyMapper.selectList(new LambdaQueryWrapper<Policy>().orderByAsc(Policy::getPriority));
    }

    public Policy createPolicy(Policy policy) {
        if (policy.getEffect() == null) {
            policy.setEffect("ALLOW");
        }
        if (policy.getPriority() == null) {
            policy.setPriority(100);
        }
        if (policy.getEnabled() == null) {
            policy.setEnabled(true);
        }
        policyMapper.insert(policy);
        return policy;
    }

    public void togglePolicy(Long id, boolean enabled) {
        Policy p = policyMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        p.setEnabled(enabled);
        policyMapper.updateById(p);
    }
}
