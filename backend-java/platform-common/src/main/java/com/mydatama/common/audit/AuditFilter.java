package com.mydatama.common.audit;

import com.mydatama.common.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 审计过滤器：覆盖 /api/**（白名单 login/refresh/health），异步落库。
 */
@Component
public class AuditFilter extends OncePerRequestFilter {

    private final AuditSink sink;

    public AuditFilter(AuditSink sink) {
        this.sink = sink;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return true;
        }
        return path.equals("/api/auth/login") || path.equals("/api/auth/refresh") || path.equals("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            var user = UserContext.get();
            String path = request.getRequestURI();
            sink.offer(AuditEvent.builder()
                    .username(user == null ? null : user.getUsername())
                    .userId(user == null ? null : user.getUserId())
                    .action(resolveAction(path, request.getMethod()))
                    .resource(path)
                    .method(request.getMethod())
                    .path(path + (request.getQueryString() == null ? "" : "?" + request.getQueryString()))
                    .statusCode(response.getStatus())
                    .ip(clientIp(request))
                    .userAgent(truncate(request.getHeader("User-Agent"), 250))
                    .costMs(System.currentTimeMillis() - start)
                    .build());
        }
    }

    static String resolveAction(String path, String method) {
        if (path.matches("/api/iam/roles/\\d+/permissions")) {
            return "PERMISSION_GRANT";
        }
        if (path.startsWith("/api/iam/policies")) {
            return "MASK_POLICY_CHANGE";
        }
        if (path.matches("/api/dataset/datasets/\\d+/versions")) {
            return "DATASET_PUBLISH";
        }
        if (path.matches("/api/product/products/\\d+/generate")) {
            return "PRODUCT_GENERATE";
        }
        if (path.matches("/api/product/products/\\d+/register")) {
            return "PRODUCT_REGISTER";
        }
        if (path.matches("/api/product/products/\\d+/listing")) {
            return "PRODUCT_LISTING";
        }
        return "API_CALL";
    }

    static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return bytes.length <= max ? s : new String(bytes, 0, max, StandardCharsets.UTF_8);
    }
}
