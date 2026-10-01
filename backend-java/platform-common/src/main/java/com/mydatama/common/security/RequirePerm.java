package com.mydatama.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限点校验（RBAC），如 @RequirePerm("gov:asset:read")。
 * admin 角色直通；其余用户 token 中权限点需包含全部声明值。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    String[] value();
}
