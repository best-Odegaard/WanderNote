package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
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
    private LocalDateTime updateTime;

}
