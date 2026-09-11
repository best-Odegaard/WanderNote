package com.gkv.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端接口权限校验注解，标注在 controller 方法上
 * 由 JwtTokenAdminInterceptor 校验当前管理员角色是否包含对应权限点
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {
    /** 权限点，如 feedback:handle，见 PermConstant */
    String value();
}
