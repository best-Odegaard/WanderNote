package com.gkv.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户信息更新 DTO（仅允许更新的字段，避免误改 username/password）
 */
@Data
public class UserInfoDTO {
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private Integer gender;
    private LocalDate birthday;
    private String bio;
}
