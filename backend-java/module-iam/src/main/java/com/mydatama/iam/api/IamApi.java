package com.mydatama.iam.api;

import com.mydatama.common.security.UserInfo;

import java.util.Map;

/**
 * IAM 对外服务接口（跨模块调用仅允许走 api 包）。
 */
public interface IamApi {

    /** 按用户名装载完整用户信息（角色+权限点），供登录/刷新。 */
    UserInfo loadUserInfo(String username);

    /** 按用户ID装载完整用户信息。 */
    UserInfo loadUserInfoById(Long userId);

    /**
     * ABAC 判定：subject 为用户属性，resourceAttrs 为资源属性。
     * 命中任一 enabled DENY 策略且条件成立 → false；命中 ALLOW 且条件成立 → true；否则默认 true。
     */
    boolean checkAccess(String resource, String action, Map<String, Object> subject, Map<String, Object> resourceAttrs);

    /**
     * 签发计量 API Key（产品 API_SERVICE 用）。返回完整明文 Key（仅此一次返回）。
     * @param userId 归属用户
     * @param name   Key 名称
     * @param scopes 逗号分隔权限范围
     * @return apiKey 明文
     */
    String createMeteredApiKey(Long userId, String name, String scopes);

    /** 校验 API Key（openapi 计量用），有效返回归属用户ID，无效返回 null。 */
    Long validateApiKey(String apiKey, String clientIp);
}
