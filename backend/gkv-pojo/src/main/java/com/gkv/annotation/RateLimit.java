package com.gkv.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 频率限制注解
 * 用于防止接口被恶意刷取
 *
 * @author kaihuan
 * @since 2024-01-17
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * 限流key的前缀
     */
    String key() default "rate_limit";
    
    /**
     * 时间窗口（秒）
     */
    int timeWindow() default 60;
    
    /**
     * 最大请求次数
     */
    int maxCount() default 10;
    
    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
    
    /**
     * 是否基于用户ID限流
     */
    boolean byUser() default true;
}
