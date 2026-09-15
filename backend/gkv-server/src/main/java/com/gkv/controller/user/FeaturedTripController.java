package com.gkv.controller.user;

import com.gkv.result.Result;
import com.gkv.service.FeaturedTripService;
import com.gkv.vo.FeaturedTripVO;
import com.gkv.vo.TripPlanVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 精选行程（用户端）
 * 首页轮播展示「一整个城市的完整行程」，点进去可查看、添加到我的行程、或对话修改后再加入。
 */
@RestController
@RequestMapping("/featured")
@Slf4j
@Api(tags = "精选行程接口")
public class FeaturedTripController {

    @Autowired
    private FeaturedTripService featuredTripService;

    @GetMapping("/list")
    @ApiOperation("精选行程列表（首页轮播用，不含完整行程）")
    public Result<List<FeaturedTripVO>> list() {
        return Result.success(featuredTripService.listActive());
    }

    @GetMapping("/{id}")
    @ApiOperation("精选行程详情（含完整行程）")
    public Result<FeaturedTripVO> detail(@PathVariable Long id) {
        return Result.success(featuredTripService.getDetail(id));
    }

    @PostMapping("/{id}/copy")
    @ApiOperation("添加到我的行程（复制一份独立副本）")
    public Result<TripPlanVO> copy(@PathVariable Long id) {
        log.info("添加精选行程到我的行程：featuredId={}", id);
        return Result.success(featuredTripService.copyToMine(id));
    }
}
