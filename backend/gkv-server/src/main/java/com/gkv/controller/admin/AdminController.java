package com.gkv.controller.admin;

import com.gkv.dto.AdminLoginDTO;
import com.gkv.result.Result;
import com.gkv.service.AdminAuthService;
import com.gkv.vo.AdminInfoVO;
import com.gkv.vo.AdminLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端登录相关接口
 */
@RestController
@RequestMapping("/admin")
@Slf4j
@Api(tags = "管理端-登录")
public class AdminController {

    @Autowired
    private AdminAuthService adminAuthService;

    @PostMapping("/login")
    @ApiOperation("管理员登录")
    public Result<AdminLoginVO> login(@RequestBody @Validated AdminLoginDTO dto) {
        log.info("管理员登录：{}", dto.getUsername());
        AdminLoginVO vo = adminAuthService.login(dto);
        log.info("管理员登录成功，adminId={}", vo.getId());
        return Result.success(vo);
    }

    @PostMapping("/logout")
    @ApiOperation("管理员登出")
    public Result logout() {
        adminAuthService.logout();
        return Result.success();
    }

    @GetMapping("/info")
    @ApiOperation("获取当前管理员信息（含权限点）")
    public Result<AdminInfoVO> info() {
        return Result.success(adminAuthService.info());
    }
}
