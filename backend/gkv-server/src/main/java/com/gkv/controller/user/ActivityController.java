package com.gkv.controller.user;

import com.gkv.context.BaseContext;
import com.gkv.dto.ActivityPageDTO;
import com.gkv.result.PageResult;
import com.gkv.result.Result;
import com.gkv.service.ActivityService;
import com.gkv.vo.ActivityVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activity")
@Slf4j
@Api(tags = "活动相关接口")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @GetMapping("/list")
    @ApiOperation("活动列表（分页，支持城市/分类/日期筛选）")
    public Result<PageResult<ActivityVO>> list(ActivityPageDTO dto) {
        log.info("查询活动列表：city={}, category={}, page={}",
                dto.getCity(), dto.getCategory(), dto.getPage());
        return Result.success(activityService.pageQuery(dto, BaseContext.getCurrentId()));
    }

    @GetMapping("/{id}")
    @ApiOperation("活动详情")
    public Result<ActivityVO> detail(@PathVariable Long id) {
        log.info("查询活动详情：id={}", id);
        ActivityVO vo = activityService.getById(id, BaseContext.getCurrentId());
        if (vo == null) {
            return Result.error("活动不存在");
        }
        return Result.success(vo);
    }

    @GetMapping("/hot")
    @ApiOperation("热门活动列表")
    public Result<List<ActivityVO>> hot(@RequestParam(required = false) Integer limit) {
        return Result.success(activityService.hot(limit, BaseContext.getCurrentId()));
    }

    @PostMapping("/{id}/collect")
    @ApiOperation("收藏活动")
    public Result collect(@PathVariable Long id) {
        log.info("收藏活动：id={}", id);
        activityService.collect(id, BaseContext.getCurrentId());
        return Result.success();
    }

    @DeleteMapping("/{id}/collect")
    @ApiOperation("取消收藏活动")
    public Result uncollect(@PathVariable Long id) {
        log.info("取消收藏活动：id={}", id);
        activityService.uncollect(id, BaseContext.getCurrentId());
        return Result.success();
    }

    @PostMapping("/{id}/enroll")
    @ApiOperation("活动报名（报名人数+1）")
    public Result enroll(@PathVariable Long id) {
        log.info("活动报名：id={}", id);
        activityService.enroll(id, BaseContext.getCurrentId());
        return Result.success();
    }
}
