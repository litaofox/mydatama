package com.mydatama.iam.service.impl;

import com.mydatama.common.security.UserInfo;
import com.mydatama.iam.api.IamApi;
import com.mydatama.iam.service.AbacService;
import com.mydatama.iam.service.ApiKeyService;
import com.mydatama.iam.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class IamApiImpl implements IamApi {

    private final AuthService authService;
    private final AbacService abacService;
    private final ApiKeyService apiKeyService;

    @Override
    public UserInfo loadUserInfo(String username) {
        // 通过 username 查 id 再复用缓存装载
        return authService.loadUserInfo(
                authService.resolveUserId(username));
    }

    @Override
    public UserInfo loadUserInfoById(Long userId) {
        return authService.loadUserInfo(userId);
    }

    @Override
    public boolean checkAccess(String resource, String action,
                               Map<String, Object> subject, Map<String, Object> resourceAttrs) {
        return abacService.check(resource, action, subject, resourceAttrs);
    }

    @Override
    public String createMeteredApiKey(Long userId, String name, String scopes) {
        return apiKeyService.create(userId, name, scopes);
    }

    @Override
    public Long validateApiKey(String apiKey, String clientIp) {
        return apiKeyService.validate(apiKey, clientIp);
    }
}
