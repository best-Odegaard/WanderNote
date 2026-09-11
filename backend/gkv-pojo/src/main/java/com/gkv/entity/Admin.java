package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员实体
 */
@Data
@TableName("sys_admin")
public class Admin {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String nickname;
    private Long roleId;
    private Integer status;
    private LocalDateTime lastLoginTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
