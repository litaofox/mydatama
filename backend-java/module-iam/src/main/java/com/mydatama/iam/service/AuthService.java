package com.mydatama.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.common.security.JwtUtil;
import com.mydatama.common.security.UserInfo;
import com.mydatama.iam.entity.Permission;
import com.mydatama.iam.entity.Role;
import com.mydatama.iam.entity.RolePermission;
import com.mydatama.iam.entity.User;
import com.mydatama.iam.entity.UserRole;
import com.mydatama.iam.mapper.PermissionMapper;
import com.mydatama.iam.mapper.RoleMapper;
import com.mydatama.iam.mapper.RolePermissionMapper;
import com.mydatama.iam.mapper.UserMapper;
import com.mydatama.iam.mapper.UserRoleMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务：登录（失败计数+锁定）、刷新、当前用户。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED = 5;
    private static final int LOCK_MINUTES = 10;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Cache<Long, UserInfo> userCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build();

    @Transactional
    public Map<String, Object> login(String username, String password, String ip) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            int failed = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
            user.setFailedLoginCount(failed);
            if (failed >= MAX_FAILED) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                user.setFailedLoginCount(0);
            }
            userMapper.updateById(user);
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userMapper.updateById(user);

        UserInfo info = loadUserInfo(user.getId());
        return tokenPair(info);
    }

    public Map<String, Object> refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtil.parse(refreshToken);
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (!jwtUtil.isRefreshToken(claims)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        Long uid = claims.get("uid", Number.class).longValue();
        UserInfo info = loadUserInfo(uid);
        return tokenPair(info);
    }

    public Map<String, Object> me(UserInfo current) {
        UserInfo fresh = loadUserInfo(current.getUserId());
        Map<String, Object> m = new HashMap<>();
        m.put("username", fresh.getUsername());
        m.put("realName", fresh.getRealName());
        m.put("roles", fresh.getRoles());
        m.put("secretLevel", fresh.getSecretLevel());
        m.put("deptCode", fresh.getDeptCode());
        m.put("permissions", fresh.getPermissions());
        return m;
    }

    public UserInfo loadUserInfo(Long userId) {
        UserInfo cached = userCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList();
        List<String> roleCodes = roleIds.isEmpty() ? List.of()
                : roleMapper.selectBatchIds(roleIds).stream().map(Role::getCode).toList();
        List<String> perms;
        if (roleCodes.contains("admin")) {
            perms = permissionMapper.selectList(null).stream().map(Permission::getCode).toList();
        } else if (roleIds.isEmpty()) {
            perms = List.of();
        } else {
            List<Long> permIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                            .in(RolePermission::getRoleId, roleIds))
                    .stream().map(RolePermission::getPermissionId).distinct().toList();
            perms = permIds.isEmpty() ? List.of()
                    : permissionMapper.selectBatchIds(permIds).stream().map(Permission::getCode).toList();
        }
        UserInfo info = UserInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(roleCodes)
                .secretLevel(user.getSecretLevel())
                .deptCode(user.getDeptCode())
                .permissions(perms)
                .build();
        userCache.put(userId, info);
        return info;
    }

    public void evictUser(Long userId) {
        userCache.invalidate(userId);
    }

    /** 按用户名解析用户ID（不存在抛 100001）。 */
    public Long resolveUserId(String username) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (u == null) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        return u.getId();
    }

    private Map<String, Object> tokenPair(UserInfo info) {
        Map<String, Object> userView = new HashMap<>();
        userView.put("username", info.getUsername());
        userView.put("realName", info.getRealName());
        userView.put("roles", info.getRoles());
        userView.put("secretLevel", info.getSecretLevel());
        userView.put("deptCode", info.getDeptCode());
        userView.put("permissions", info.getPermissions());

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", jwtUtil.createAccessToken(info));
        data.put("refreshToken", jwtUtil.createRefreshToken(info));
        data.put("user", userView);
        return data;
    }
}
