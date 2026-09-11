package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.dto.AdminUserPageDTO;
import com.gkv.result.PageResult;
import com.gkv.result.Result;
import com.gkv.service.AdminUserService;
import com.gkv.vo.AdminUserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-用户管理
 */
@RestController
@RequestMapping("/admin/user")
@Slf4j
@Api(tags = "管理端-用户管理")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    @GetMapping
    @RequirePerm(PermConstant.USER_VIEW)
    @ApiOperation("用户分页列表（关键词/状态筛选）")
    public Result<PageResult<AdminUserVO>> page(AdminUserPageDTO dto) {
        log.info("查询用户列表：keyword={}, status={}, page={}",
                dto.getKeyword(), dto.getStatus(), dto.getPageNum());
        return Result.success(adminUserService.page(dto));
    }

    @GetMapping("/{id}")
    @RequirePerm(PermConstant.USER_VIEW)
    @ApiOperation("用户详情")
    public Result<AdminUserVO> detail(@PathVariable Long id) {
        return Result.success(adminUserService.detail(id));
    }

    @PutMapping("/{id}/status/{status}")
    @RequirePerm(PermConstant.USER_MANAGE)
    @ApiOperation("启用/禁用用户（status：0禁用 1启用）")
    public Result updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        log.info("修改用户状态：id={}, status={}", id, status);
        adminUserService.updateStatus(id, status);
        return Result.success();
    }
}
