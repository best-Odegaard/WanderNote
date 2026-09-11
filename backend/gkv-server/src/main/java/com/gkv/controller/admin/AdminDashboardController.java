package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.result.Result;
import com.gkv.service.AdminDashboardService;
import com.gkv.vo.DashboardVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-数据看板
 */
@RestController
@RequestMapping("/admin/dashboard")
@Slf4j
@Api(tags = "管理端-数据看板")
public class AdminDashboardController {

    @Autowired
    private AdminDashboardService adminDashboardService;

    @GetMapping
    @RequirePerm(PermConstant.DASHBOARD_VIEW)
    @ApiOperation("数据看板统计（总量/今日新增/近7天趋势/反馈状态分布）")
    public Result<DashboardVO> dashboard() {
        return Result.success(adminDashboardService.getDashboard());
    }
}
