package com.gkv.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色列表项
 */
@Data
public class RoleVO {
    private Long id;
    private String roleKey;
    private String roleName;
    private String perms;
    private String description;
    private LocalDateTime createTime;
}
