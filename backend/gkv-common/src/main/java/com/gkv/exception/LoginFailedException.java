package com.gkv.exception;

/**
 * 登录失败
 */
public class LoginFailedException extends BaseException{//业务异常
    public LoginFailedException(String msg){
        super(msg);
    }
}
