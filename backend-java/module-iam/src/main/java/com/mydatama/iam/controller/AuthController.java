package com.mydatama.iam.controller;

import com.mydatama.common.api.Result;
import com.mydatama.common.security.UserContext;
import com.mydatama.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口：login/refresh/logout/me。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record LoginReq(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshReq(@NotBlank String refreshToken) {
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginReq req, HttpServletRequest http) {
        return Result.ok(authService.login(req.username(), req.password(), clientIp(http)));
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@Validated @RequestBody RefreshReq req) {
        return Result.ok(authService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // 无状态 JWT：演示环境仅返回成功（可选：加入黑名单）
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(authService.me(UserContext.get()));
    }

    static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}
