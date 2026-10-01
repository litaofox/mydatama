package com.mydatama.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mydatama.common.util.CodecUtil;
import com.mydatama.iam.entity.ApiKey;
import com.mydatama.iam.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * API Key 服务：签发（明文仅返回一次，库存 SHA-256）与校验（Caffeine 缓存）。
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;

    /** keyHash -> userId，5 分钟缓存（吊销后最长 5 分钟失效，MVP 可接受）。 */
    private final Cache<String, Long> keyCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(5000).build();

    public String create(Long userId, String name, String scopes) {
        String plain = "mk_" + CodecUtil.randomHex(24);
        ApiKey key = new ApiKey();
        key.setUserId(userId);
        key.setName(name);
        key.setKeyPrefix(plain.substring(0, Math.min(11, plain.length())));
        key.setKeyHash(CodecUtil.sha256Hex(plain));
        key.setScopes(scopes);
        key.setEnabled(true);
        apiKeyMapper.insert(key);
        return plain;
    }

    /**
     * 校验 Key，有效返回 userId，无效返回 null。记录 last_used。
     */
    public Long validate(String plainKey, String clientIp) {
        if (plainKey == null || plainKey.isBlank()) {
            return null;
        }
        String hash = CodecUtil.sha256Hex(plainKey);
        Long cached = keyCache.getIfPresent(hash);
        if (cached != null) {
            return cached;
        }
        String prefix = plainKey.substring(0, Math.min(11, plainKey.length()));
        List<ApiKey> candidates = apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKeyPrefix, prefix).eq(ApiKey::getEnabled, true));
        for (ApiKey k : candidates) {
            if (k.getKeyHash().equals(hash)) {
                if (k.getExpireAt() != null && k.getExpireAt().isBefore(LocalDateTime.now())) {
                    return null;
                }
                k.setLastUsedAt(LocalDateTime.now());
                k.setLastUsedIp(clientIp);
                apiKeyMapper.updateById(k);
                keyCache.put(hash, k.getUserId());
                return k.getUserId();
            }
        }
        return null;
    }
}
