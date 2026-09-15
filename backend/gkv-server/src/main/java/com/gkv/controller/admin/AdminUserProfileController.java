package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.dto.UserProfileEditDTO;
import com.gkv.dto.UserProfileRemarkDTO;
import com.gkv.result.Result;
import com.gkv.service.AdminUserProfileService;
import com.gkv.vo.UserProfileVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 管理端-AI 用户画像
 *
 * 路径刻意挂在 /admin/user-profile/** 而不是 /admin/user/{id}/profile：
 * 后者和已有的 /admin/user/{id} 通配路由挤在同一层，路径匹配容易产生歧义，
 * 出问题时表现为"请求打到了别的方法上"，非常难查。
 *
 * 权限点复用已有的 user:view / user:manage，不新增权限点：
 * 新增权限点意味着还要去改角色表里的权限串，否则菜单不显示、页面被路由守卫重定向，
 * 这类"代码没问题但看不到"的坑很容易被误判成功能没生效。
 */
@RestController
@RequestMapping("/admin/user-profile")
@Slf4j
@Api(tags = "管理端-AI用户画像")
public class AdminUserProfileController {

    @Resource
    private AdminUserProfileService adminUserProfileService;

    @GetMapping("/{userId}")
    @RequirePerm(PermConstant.USER_VIEW)
    @ApiOperation("画像详情（无画像时返回带用户基础信息的空结构）")
    public Result<UserProfileVO> detail(@PathVariable Long userId) {
        return Result.success(adminUserProfileService.detail(userId));
    }

    @GetMapping("/tags")
    @RequirePerm(PermConstant.USER_VIEW)
    @ApiOperation("受控标签词表（筛选下拉用）")
    public Result<List<String>> tags() {
        return Result.success(adminUserProfileService.tags());
    }

    @PutMapping("/{userId}")
    @RequirePerm(PermConstant.USER_MANAGE)
    @ApiOperation("人工修正画像（整份覆盖，允许清空；不改 user_id 与回灌开关）")
    public Result<UserProfileVO> edit(@PathVariable Long userId, @RequestBody UserProfileEditDTO dto) {
        log.info("管理员人工修正画像：userId={}", userId);
        return Result.success(adminUserProfileService.edit(userId, dto));
    }

    @PutMapping("/{userId}/remark")
    @RequirePerm(PermConstant.USER_MANAGE)
    @ApiOperation("更新运营备注与标记")
    public Result<UserProfileVO> remark(@PathVariable Long userId, @RequestBody UserProfileRemarkDTO dto) {
        return Result.success(adminUserProfileService.updateRemark(userId, dto));
    }
}
