package com.mydatama.boot;

import com.mydatama.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查（compose 健康检查 curl /actuator/health 仅要求 HTTP 200）。
 */
@RestController
public class HealthController {

    @GetMapping({"/health", "/actuator/health"})
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of("status", "UP"));
    }
}
