package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.dto.BannerSaveDTO;
import com.gkv.dto.CitySaveDTO;
import com.gkv.entity.HomeBanner;
import com.gkv.entity.HomeCity;
import com.gkv.result.Result;
import com.gkv.service.AdminHomeService;
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

/**
 * 管理端-首页管理（Banner/城市）
 */
@RestController
@RequestMapping("/admin/home")
@Slf4j
@Api(tags = "管理端-首页管理（Banner/城市）")
public class AdminHomeController {

    @Autowired
    private AdminHomeService adminHomeService;

    // ─────────── Banner ───────────

    @GetMapping("/banner/list")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("Banner列表")
    public Result<List<HomeBanner>> bannerList() {
        return Result.success(adminHomeService.bannerList());
    }

    @PostMapping("/banner")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("新增Banner")
    public Result bannerAdd(@RequestBody BannerSaveDTO dto) {
        log.info("新增Banner：{}", dto.getTitle());
        adminHomeService.bannerSave(dto);
        return Result.success();
    }

    @PutMapping("/banner")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("编辑Banner")
    public Result bannerEdit(@RequestBody BannerSaveDTO dto) {
        log.info("编辑Banner：id={}", dto.getId());
        adminHomeService.bannerSave(dto);
        return Result.success();
    }

    @DeleteMapping("/banner/{id}")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("删除Banner")
    public Result bannerDelete(@PathVariable Long id) {
        log.info("删除Banner：id={}", id);
        adminHomeService.bannerDelete(id);
        return Result.success();
    }

    @PutMapping("/banner/{id}/status/{status}")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("启用/禁用Banner（status：0禁用 1启用）")
    public Result bannerStatus(@PathVariable Long id, @PathVariable Integer status) {
        log.info("修改Banner状态：id={}, status={}", id, status);
        adminHomeService.bannerStatus(id, status);
        return Result.success();
    }

    // ─────────── 城市 ───────────

    @GetMapping("/city/list")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("城市列表")
    public Result<List<HomeCity>> cityList() {
        return Result.success(adminHomeService.cityList());
    }

    @PostMapping("/city")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("新增城市")
    public Result cityAdd(@RequestBody @Validated CitySaveDTO dto) {
        log.info("新增城市：{}", dto.getName());
        adminHomeService.citySave(dto);
        return Result.success();
    }

    @PutMapping("/city")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("编辑城市")
    public Result cityEdit(@RequestBody @Validated CitySaveDTO dto) {
        log.info("编辑城市：id={}", dto.getId());
        adminHomeService.citySave(dto);
        return Result.success();
    }

    @DeleteMapping("/city/{id}")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("删除城市")
    public Result cityDelete(@PathVariable Long id) {
        log.info("删除城市：id={}", id);
        adminHomeService.cityDelete(id);
        return Result.success();
    }

    @PutMapping("/city/{id}/status/{status}")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("启用/禁用城市（status：0禁用 1启用）")
    public Result cityStatus(@PathVariable Long id, @PathVariable Integer status) {
        log.info("修改城市状态：id={}, status={}", id, status);
        adminHomeService.cityStatus(id, status);
        return Result.success();
    }
}
