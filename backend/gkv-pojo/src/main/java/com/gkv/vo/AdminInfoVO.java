package com.gkv.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前管理员信息（无 token）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminInfoVO {
    private Long id;
    private String username;
    private String nickname;
    private Long roleId;
    private String roleKey;
    private String roleName;
    /** 权限点列表 */
    private List<String> perms;
}
