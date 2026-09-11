package com.gkv.annotation;
import com.gkv.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解：原注解：用于标识某个方法需要进行功能字段自动填充处理
 */
@Target(ElementType.METHOD)   //作用范围：方法上
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {

    OperationType value();

}
