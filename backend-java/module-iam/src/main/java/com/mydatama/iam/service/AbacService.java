package com.mydatama.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydatama.common.security.AbacEngine;
import com.mydatama.iam.entity.Policy;
import com.mydatama.iam.mapper.PolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * ABAC 判定服务：加载 enabled 策略（按 priority 升序），DENY 优先短路。
 */
@Service
@RequiredArgsConstructor
public class AbacService {

    private final PolicyMapper policyMapper;
    private final AbacEngine engine;

    public boolean check(String resource, String action,
                         Map<String, Object> subject, Map<String, Object> resourceAttrs) {
        List<Policy> policies = policyMapper.selectList(new LambdaQueryWrapper<Policy>()
                .eq(Policy::getResource, resource)
                .eq(Policy::getAction, action)
                .eq(Policy::getEnabled, true))
                .stream().sorted(Comparator.comparingInt(Policy::getPriority)).toList();
        for (Policy p : policies) {
            boolean matched = engine.evaluate(p.getConditionTree(), subject, resourceAttrs);
            if (matched && "DENY".equalsIgnoreCase(p.getEffect())) {
                return false;
            }
            if (matched && "ALLOW".equalsIgnoreCase(p.getEffect())) {
                return true;
            }
        }
        return true;
    }
}
