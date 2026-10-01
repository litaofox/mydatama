package com.mydatama.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.api.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部服务令牌过滤器：/internal/** 校验 X-Service-Token（API-COM-001）。
 */
@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

    @Value("${mydatama.service-token:}")
    private String serviceToken;

    private final ObjectMapper objectMapper;

    public ServiceTokenFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader("X-Service-Token");
        if (serviceToken.isBlank() || !serviceToken.equals(token)) {
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error(ErrorCode.UNAUTHORIZED.getCode(), "服务令牌无效")));
            return;
        }
        chain.doFilter(request, response);
    }
}
