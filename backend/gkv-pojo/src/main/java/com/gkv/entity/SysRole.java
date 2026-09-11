package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体
 */
@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String roleKey;
    private String roleName;
    /** 权限点，逗号分隔 */
    private String perms;
    private String description;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
