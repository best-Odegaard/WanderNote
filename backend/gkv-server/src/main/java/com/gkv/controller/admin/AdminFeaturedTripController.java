package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.dto.FeaturedParseDTO;
import com.gkv.dto.FeaturedTripSaveDTO;
import com.gkv.entity.FeaturedTrip;
import com.gkv.result.Result;
import com.gkv.service.AdminFeaturedTripService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 管理端-精选行程管理
 * 权限复用首页内容管理的 HOME_MANAGE（本质同属首页运营内容）。
 */
@RestController
@RequestMapping("/admin/featured")
@Slf4j
@Api(tags = "管理端-精选行程管理")
public class AdminFeaturedTripController {

    @Autowired
    private AdminFeaturedTripService adminFeaturedTripService;

    @GetMapping("/list")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("精选行程列表（含下架）")
    public Result<List<FeaturedTrip>> list() {
        return Result.success(adminFeaturedTripService.list());
    }

    @PostMapping
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("新增精选行程")
    public Result add(@RequestBody FeaturedTripSaveDTO dto) {
        dto.setId(null);
        adminFeaturedTripService.save(dto);
        return Result.success();
    }

    @PutMapping
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("编辑精选行程")
    public Result edit(@RequestBody FeaturedTripSaveDTO dto) {
        adminFeaturedTripService.save(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("删除精选行程")
    public Result delete(@PathVariable Long id) {
        adminFeaturedTripService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status/{status}")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("上架/下架（status：0下架 1上架）")
    public Result status(@PathVariable Long id, @PathVariable Integer status) {
        adminFeaturedTripService.status(id, status);
        return Result.success();
    }

    @PostMapping("/parse-link")
    @RequirePerm(PermConstant.HOME_MANAGE)
    @ApiOperation("解析外部内容生成行程草稿（不落库）：可只给城市，也可粘贴行程原文")
    public Result<FeaturedTripSaveDTO> parseLink(@RequestBody FeaturedParseDTO dto) {
        log.info("解析生成精选行程草稿：city={}，url={}", dto.getCity(), dto.getSourceUrl());
        return Result.success(adminFeaturedTripService.parseLink(dto));
    }
}
