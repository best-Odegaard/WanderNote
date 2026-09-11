package com.gkv.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端用户列表项（含登录密码，供运营查看）
 */
@Data
public class AdminUserVO {
    private Long id;
    private String username;
    /** 登录密码（明文，供运营查看） */
    private String password;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
    private String bio;
    private Integer points;
    private Integer status;
    private LocalDateTime createTime;
}
