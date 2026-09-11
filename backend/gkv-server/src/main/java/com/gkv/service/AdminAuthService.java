package com.gkv.service;

import com.gkv.dto.AdminLoginDTO;
import com.gkv.vo.AdminInfoVO;
import com.gkv.vo.AdminLoginVO;

public interface AdminAuthService {

    /** 管理员登录，返回 token 与权限信息 */
    AdminLoginVO login(AdminLoginDTO dto);

    /** 获取当前管理员信息（含权限点） */
    AdminInfoVO info();

    /** 登出（无状态，仅记录日志） */
    void logout();
}
