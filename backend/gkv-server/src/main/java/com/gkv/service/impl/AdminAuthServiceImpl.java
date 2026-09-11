package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.constant.JwtClaimsConstant;
import com.gkv.constant.MessageConstant;
import com.gkv.constant.StatusConstant;
import com.gkv.context.BaseContext;
import com.gkv.dto.AdminLoginDTO;
import com.gkv.entity.Admin;
import com.gkv.entity.SysRole;
import com.gkv.exception.AccountLockedException;
import com.gkv.exception.AccountNotFoundException;
import com.gkv.exception.PasswordErrorException;
import com.gkv.mapper.AdminMapper;
import com.gkv.mapper.SysRoleMapper;
import com.gkv.properties.JwtProperties;
import com.gkv.service.AdminAuthService;
import com.gkv.utils.JwtUtil;
import com.gkv.vo.AdminInfoVO;
import com.gkv.vo.AdminLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminAuthServiceImpl implements AdminAuthService {

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public AdminLoginVO login(AdminLoginDTO dto) {
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUsername, dto.getUsername());
        Admin admin = adminMapper.selectOne(wrapper);
        //判断账户是否存在
        if (admin == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        //判断账户有没有被封
        if (admin.getStatus() == StatusConstant.DISABLE) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }
        //验证密码（MD5）
        String md5Pwd = DigestUtils.md5DigestAsHex(dto.getPassword().getBytes());
        if (!md5Pwd.equals(admin.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        //更新最后登录时间
        Admin update = new Admin();
        update.setId(admin.getId());
        update.setLastLoginTime(LocalDateTime.now());
        adminMapper.updateById(update);

        //生成通行证
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, admin.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims
        );

        SysRole role = loadRole(admin.getRoleId());
        List<String> perms = parsePerms(role);

        return AdminLoginVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .nickname(admin.getNickname())
                .roleId(admin.getRoleId())
                .roleKey(role != null ? role.getRoleKey() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .perms(perms)
                .token(token)
                .build();
    }

    @Override
    public AdminInfoVO info() {
        Long adminId = BaseContext.getCurrentId();
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        SysRole role = loadRole(admin.getRoleId());
        return AdminInfoVO.builder()
                .id(admin.getId())
                .username(admin.getUsername())
                .nickname(admin.getNickname())
                .roleId(admin.getRoleId())
                .roleKey(role != null ? role.getRoleKey() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .perms(parsePerms(role))
                .build();
    }

    @Override
    public void logout() {
        Long adminId = BaseContext.getCurrentId();
        log.info("管理员登出：{}", adminId);
    }

    private SysRole loadRole(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return sysRoleMapper.selectById(roleId);
    }

    /** 角色权限点（逗号分隔）解析为列表 */
    private List<String> parsePerms(SysRole role) {
        if (role == null || !StringUtils.hasText(role.getPerms())) {
            return Collections.emptyList();
        }
        return Arrays.stream(role.getPerms().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }
}
