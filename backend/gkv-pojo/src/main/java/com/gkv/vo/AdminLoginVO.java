package com.gkv.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理员登录返回
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginVO {
    private Long id;
    private String username;
    private String nickname;
    private Long roleId;
    private String roleKey;
    private String roleName;
    /** 权限点列表 */
    private List<String> perms;
    private String token;
}
