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

import java.util.List;

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
    @ApiOperation("用户分页列表（关键词/状态/画像标签/画像标记筛选）")
    public Result<PageResult<AdminUserVO>> page(AdminUserPageDTO dto) {
        log.info("查询用户列表：keyword={}, status={}, profileTag={}, profileMark={}, page={}",
                dto.getKeyword(), dto.getStatus(), dto.getProfileTag(), dto.getProfileMark(), dto.getPageNum());
        return Result.success(adminUserService.page(dto));
    }

    /**
     * 导出（路径是字面量，比 /admin/user/{id} 的模板更具体，Spring 会优先匹配到这个方法）
     */
    @GetMapping("/export")
    @RequirePerm(PermConstant.USER_VIEW)
    @ApiOperation("按当前筛选条件导出（不分页，有上限；前端转 CSV）")
    public Result<List<AdminUserVO>> export(AdminUserPageDTO dto) {
        log.info("导出用户列表：keyword={}, status={}, profileTag={}, profileMark={}",
                dto.getKeyword(), dto.getStatus(), dto.getProfileTag(), dto.getProfileMark());
        return Result.success(adminUserService.export(dto));
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
