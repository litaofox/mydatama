package com.mydatama.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT HS256 签发与验签（与 processing-app 共用 JWT_HMAC_SECRET，双端验签）。
 */
@Component
public class JwtUtil {

    @Value("${mydatama.jwt.secret}")
    private String secret;

    @Value("${mydatama.jwt.access-ttl:7200000}")
    private long accessTtl;

    @Value("${mydatama.jwt.refresh-ttl:43200000}")
    private long refreshTtl;

    private SecretKey hmacKey() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return new javax.crypto.spec.SecretKeySpec(bytes, "HmacSHA256");
    }

    public String createAccessToken(UserInfo u) {
        return build(u, "access", accessTtl);
    }

    public String createRefreshToken(UserInfo u) {
        return build(u, "refresh", refreshTtl);
    }

    private String build(UserInfo u, String type, long ttl) {
        Date now = new Date();
        return Jwts.builder()
                .subject(u.getUsername())
                .claim("uid", u.getUserId())
                .claim("realName", u.getRealName())
                .claim("roles", u.getRoles())
                .claim("sl", u.getSecretLevel())
                .claim("dept", u.getDeptCode())
                .claim("perms", u.getPermissions())
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl))
                .signWith(hmacKey())
                .compact();
    }

    /**
     * 解析并验签，失败抛 JwtException。
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(hmacKey()).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean isRefreshToken(Claims c) {
        return "refresh".equals(c.get("type", String.class));
    }

    @SuppressWarnings("unchecked")
    public UserInfo toUserInfo(Claims c) {
        return UserInfo.builder()
                .userId(c.get("uid", Number.class) == null ? null : c.get("uid", Number.class).longValue())
                .username(c.getSubject())
                .realName(c.get("realName", String.class))
                .roles((List<String>) (Object) c.get("roles", List.class))
                .secretLevel(c.get("sl", Number.class) == null ? 1 : c.get("sl", Number.class).intValue())
                .deptCode(c.get("dept", String.class))
                .permissions((List<String>) (Object) c.get("perms", List.class))
                .build();
    }

    public long getAccessTtl() {
        return accessTtl;
    }
}
