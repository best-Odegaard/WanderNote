package com.gkv.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端管理员列表项
 */
@Data
public class AdminVO {
    private Long id;
    private String username;
    private String nickname;
    private Long roleId;
    private String roleName;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}
