package com.mydatama.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.api.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：校验 access token 并填充 UserContext。
 * 白名单（登录/刷新/健康检查）直接放行；openapi 用 API Key；internal 用服务令牌。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> WHITELIST = List.of(
            "/api/auth/login", "/api/auth/refresh", "/health", "/error");

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public static boolean isWhitelisted(String path) {
        return WHITELIST.contains(path);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/openapi/") || path.startsWith("/internal/")
                || isWhitelisted(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            deny(response, "缺少 Authorization Bearer 凭证");
            return;
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            if (jwtUtil.isRefreshToken(claims)) {
                deny(response, "refresh token 不可用于接口访问");
                return;
            }
            UserInfo user = jwtUtil.toUserInfo(claims);
            UserContext.set(user);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            deny(response, "凭证无效或已过期");
        } finally {
            UserContext.clear();
        }
    }

    private void deny(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(ErrorCode.UNAUTHORIZED.getCode(), msg)));
    }
}
