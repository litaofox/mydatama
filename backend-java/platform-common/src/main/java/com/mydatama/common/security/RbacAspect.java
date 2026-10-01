package com.mydatama.common.security;

import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * RBAC 校验切面：配合 @RequirePerm 使用。
 */
@Aspect
@Component
public class RbacAspect {

    @Before("@annotation(p)")
    public void check(JoinPoint jp, RequirePerm p) {
        UserInfo user = UserContext.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getRoles() != null && user.getRoles().contains("admin")) {
            return;
        }
        Set<String> own = user.getPermissions() == null ? Set.of() : new HashSet<>(user.getPermissions());
        for (String need : p.value()) {
            if (!own.contains(need)) {
                throw new BizException(ErrorCode.PERM_DENIED);
            }
        }
    }
}
