package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.dto.AdminSaveDTO;
import com.gkv.dto.PageQuery;
import com.gkv.dto.RoleSaveDTO;
import com.gkv.result.PageResult;
import com.gkv.result.Result;
import com.gkv.service.AdminManageService;
import com.gkv.vo.AdminVO;
import com.gkv.vo.RoleVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端-系统管理（管理员/角色）
 */
@RestController
@RequestMapping("/admin/manage")
@Slf4j
@Api(tags = "管理端-系统管理（管理员/角色）")
public class AdminManageController {

    @Autowired
    private AdminManageService adminManageService;

    // ─────────── 管理员账号 ───────────

    @GetMapping("/admin")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("管理员分页列表")
    public Result<PageResult<AdminVO>> adminPage(PageQuery dto) {
        return Result.success(adminManageService.adminPage(dto));
    }

    @PostMapping("/admin")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("新增管理员")
    public Result adminAdd(@RequestBody @Validated AdminSaveDTO dto) {
        log.info("新增管理员：{}", dto.getUsername());
        adminManageService.adminSave(dto);
        return Result.success();
    }

    @PutMapping("/admin")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("编辑管理员（密码留空不修改）")
    public Result adminEdit(@RequestBody @Validated AdminSaveDTO dto) {
        log.info("编辑管理员：id={}", dto.getId());
        adminManageService.adminSave(dto);
        return Result.success();
    }

    @DeleteMapping("/admin/{id}")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("删除管理员（不能删除自己）")
    public Result adminDelete(@PathVariable Long id) {
        log.info("删除管理员：id={}", id);
        adminManageService.adminDelete(id);
        return Result.success();
    }

    @PutMapping("/admin/{id}/status/{status}")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("启用/禁用管理员（status：0禁用 1启用）")
    public Result adminStatus(@PathVariable Long id, @PathVariable Integer status) {
        log.info("修改管理员状态：id={}, status={}", id, status);
        adminManageService.adminStatus(id, status);
        return Result.success();
    }

    // ─────────── 角色 ───────────

    @GetMapping("/role/list")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("角色列表")
    public Result<List<RoleVO>> roleList() {
        return Result.success(adminManageService.roleList());
    }

    @PostMapping("/role")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("新增角色")
    public Result roleAdd(@RequestBody @Validated RoleSaveDTO dto) {
        log.info("新增角色：{}", dto.getRoleName());
        adminManageService.roleSave(dto);
        return Result.success();
    }

    @PutMapping("/role")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("编辑角色")
    public Result roleEdit(@RequestBody @Validated RoleSaveDTO dto) {
        log.info("编辑角色：id={}", dto.getId());
        adminManageService.roleSave(dto);
        return Result.success();
    }

    @DeleteMapping("/role/{id}")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("删除角色（有管理员关联时禁止）")
    public Result roleDelete(@PathVariable Long id) {
        log.info("删除角色：id={}", id);
        adminManageService.roleDelete(id);
        return Result.success();
    }

    // ─────────── 权限点 ───────────

    @GetMapping("/perm/list")
    @RequirePerm(PermConstant.ADMIN_MANAGE)
    @ApiOperation("全部权限点列表（key+name）")
    public Result<List<Map<String, String>>> permList() {
        return Result.success(adminManageService.permList());
    }
}
