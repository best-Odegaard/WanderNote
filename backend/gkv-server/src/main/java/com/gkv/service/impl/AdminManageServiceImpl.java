package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.constant.PermConstant;
import com.gkv.constant.StatusConstant;
import com.gkv.context.BaseContext;
import com.gkv.dto.AdminSaveDTO;
import com.gkv.dto.PageQuery;
import com.gkv.dto.RoleSaveDTO;
import com.gkv.entity.Admin;
import com.gkv.entity.SysRole;
import com.gkv.exception.BaseException;
import com.gkv.mapper.AdminMapper;
import com.gkv.mapper.SysRoleMapper;
import com.gkv.result.PageResult;
import com.gkv.service.AdminManageService;
import com.gkv.vo.AdminVO;
import com.gkv.vo.RoleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminManageServiceImpl implements AdminManageService {

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Override
    public PageResult<AdminVO> adminPage(PageQuery dto) {
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();

        Page<Admin> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        page.addOrder(OrderItem.desc("create_time"));

        Page<Admin> result = adminMapper.selectPage(page, wrapper);
        List<AdminVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public void adminSave(AdminSaveDTO dto) {
        if (dto.getId() == null) {
            // 校验用户名唯一
            Long count = adminMapper.selectCount(
                    new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, dto.getUsername()));
            if (count > 0) {
                throw new BaseException("用户名已存在");
            }
            if (!StringUtils.hasText(dto.getPassword())) {
                throw new BaseException("密码不能为空");
            }
            Admin admin = new Admin();
            admin.setUsername(dto.getUsername());
            admin.setPassword(DigestUtils.md5DigestAsHex(dto.getPassword().getBytes()));
            admin.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
            admin.setRoleId(dto.getRoleId());
            admin.setStatus(dto.getStatus() != null ? dto.getStatus() : StatusConstant.ENABLE);
            admin.setCreateTime(LocalDateTime.now());
            admin.setUpdateTime(LocalDateTime.now());
            adminMapper.insert(admin);
            log.info("管理员{}新增管理员：{}", BaseContext.getCurrentId(), dto.getUsername());
        } else {
            Admin admin = adminMapper.selectById(dto.getId());
            if (admin == null) {
                throw new BaseException("管理员不存在");
            }
            // 用户名重复校验（排除自己）
            Long count = adminMapper.selectCount(
                    new LambdaQueryWrapper<Admin>()
                            .eq(Admin::getUsername, dto.getUsername())
                            .ne(Admin::getId, dto.getId()));
            if (count > 0) {
                throw new BaseException("用户名已存在");
            }
            admin.setUsername(dto.getUsername());
            if (StringUtils.hasText(dto.getPassword())) {
                admin.setPassword(DigestUtils.md5DigestAsHex(dto.getPassword().getBytes()));
            }
            admin.setNickname(dto.getNickname());
            admin.setRoleId(dto.getRoleId());
            if (dto.getStatus() != null) {
                admin.setStatus(dto.getStatus());
            }
            admin.setUpdateTime(LocalDateTime.now());
            adminMapper.updateById(admin);
            log.info("管理员{}编辑管理员：{}", BaseContext.getCurrentId(), dto.getUsername());
        }
    }

    @Override
    public void adminDelete(Long id) {
        if (id.equals(BaseContext.getCurrentId())) {
            throw new BaseException("不能删除当前登录账号");
        }
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            throw new BaseException("管理员不存在");
        }
        adminMapper.deleteById(id);
        log.info("管理员{}删除管理员：{}", BaseContext.getCurrentId(), admin.getUsername());
    }

    @Override
    public void adminStatus(Long id, Integer status) {
        if (id.equals(BaseContext.getCurrentId())) {
            throw new BaseException("不能禁用当前登录账号");
        }
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            throw new BaseException("管理员不存在");
        }
        if (status == null || (status != StatusConstant.ENABLE && status != StatusConstant.DISABLE)) {
            throw new BaseException("状态参数错误");
        }
        Admin update = new Admin();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(update);
    }

    @Override
    public List<RoleVO> roleList() {
        List<SysRole> roles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        return roles.stream().map(role -> {
            RoleVO vo = new RoleVO();
            BeanUtils.copyProperties(role, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void roleSave(RoleSaveDTO dto) {
        if (dto.getId() == null) {
            Long count = sysRoleMapper.selectCount(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, dto.getRoleKey()));
            if (count > 0) {
                throw new BaseException("角色标识已存在");
            }
            SysRole role = new SysRole();
            BeanUtils.copyProperties(dto, role);
            role.setCreateTime(LocalDateTime.now());
            role.setUpdateTime(LocalDateTime.now());
            sysRoleMapper.insert(role);
            log.info("管理员{}新增角色：{}", BaseContext.getCurrentId(), dto.getRoleName());
        } else {
            SysRole role = sysRoleMapper.selectById(dto.getId());
            if (role == null) {
                throw new BaseException("角色不存在");
            }
            // 角色标识唯一校验（排除自己）
            Long count = sysRoleMapper.selectCount(
                    new LambdaQueryWrapper<SysRole>()
                            .eq(SysRole::getRoleKey, dto.getRoleKey())
                            .ne(SysRole::getId, dto.getId()));
            if (count > 0) {
                throw new BaseException("角色标识已存在");
            }
            BeanUtils.copyProperties(dto, role);
            role.setUpdateTime(LocalDateTime.now());
            sysRoleMapper.updateById(role);
            log.info("管理员{}编辑角色：{}", BaseContext.getCurrentId(), dto.getRoleName());
        }
    }

    @Override
    public void roleDelete(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BaseException("角色不存在");
        }
        Long adminCount = adminMapper.selectCount(
                new LambdaQueryWrapper<Admin>().eq(Admin::getRoleId, id));
        if (adminCount > 0) {
            throw new BaseException("该角色下存在管理员，不能删除");
        }
        sysRoleMapper.deleteById(id);
        log.info("管理员{}删除角色：{}", BaseContext.getCurrentId(), role.getRoleName());
    }

    @Override
    public List<Map<String, String>> permList() {
        return PermConstant.list();
    }

    /** 管理员转 VO（补角色名） */
    private AdminVO toVO(Admin admin) {
        AdminVO vo = new AdminVO();
        BeanUtils.copyProperties(admin, vo);
        if (admin.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(admin.getRoleId());
            if (role != null) {
                vo.setRoleName(role.getRoleName());
            }
        }
        return vo;
    }
}
