package com.gkv.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户信息 VO（返回给前端，不含密码）
 */
@Data
public class UserInfoVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private Integer gender;
    private LocalDate birthday;
    private String bio;
}
