package com.gkv.service;

import com.gkv.dto.UserInfoDTO;
import com.gkv.dto.UserLoginDTO;
import com.gkv.dto.UserRegisterDTO;
import com.gkv.vo.UserInfoVO;
import com.gkv.vo.UserLoginVO;

public interface UserService {
    void register(UserRegisterDTO dto);
    UserLoginVO login(UserLoginDTO dto);

    /** 获取当前登录用户信息 */
    UserInfoVO getInfo();

    /** 更新当前登录用户信息 */
    UserInfoVO updateInfo(UserInfoDTO dto);
}
