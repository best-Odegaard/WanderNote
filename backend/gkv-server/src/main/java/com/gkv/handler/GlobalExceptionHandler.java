package com.gkv.handler;

import com.gkv.constant.MessageConstant;
import com.gkv.exception.BaseException;
import com.gkv.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        //Duplicate entry 'zhangsi' for key 'employee.idx_username'
        //指定捕获异常日志，然后给前端报错误弹窗
        String message = ex.getMessage();
        log.error("SQL约束异常：{}", message);
        if(message.contains("Duplicate entry")){
            String[] split=message.split(" ");
            String username=split[2];
            String msg = username + MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);//张三已存在
        }
        return Result.error(MessageConstant.UNKNOWN_ERROR + ": " + message);
    }

    /**
     * 兜底异常处理，捕获所有未处理异常
     */
    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception ex){
        log.error("未知异常：", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR + ": " + ex.getMessage());
    }

}
