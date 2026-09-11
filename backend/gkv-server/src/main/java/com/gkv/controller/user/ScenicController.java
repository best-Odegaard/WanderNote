package com.gkv.controller.user;

import com.gkv.context.BaseContext;
import com.gkv.dto.ScenicPageDTO;
import com.gkv.result.PageResult;
import com.gkv.result.Result;
import com.gkv.service.ScenicService;
import com.gkv.vo.ScenicVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scenic")
@Slf4j
@Api(tags = "景点相关接口")
public class ScenicController {

    @Autowired
    private ScenicService scenicService;

    @GetMapping("/list")
    @ApiOperation("景点列表（分页，支持关键词/城市/分类/排序）")
    public Result<PageResult<ScenicVO>> list(ScenicPageDTO dto) {
        log.info("查询景点列表：city={}, category={}, keyword={}, page={}",
                dto.getCity(), dto.getCategory(), dto.getKeyword(), dto.getPage());
        return Result.success(scenicService.pageQuery(dto, BaseContext.getCurrentId()));
    }

    @GetMapping("/{id}")
    @ApiOperation("景点详情")
    public Result<ScenicVO> detail(@PathVariable Long id) {
        log.info("查询景点详情：id={}", id);
        ScenicVO vo = scenicService.getById(id, BaseContext.getCurrentId());
        if (vo == null) {
            return Result.error("景点不存在");
        }
        return Result.success(vo);
    }

    @GetMapping("/hot")
    @ApiOperation("热门景点列表")
    public Result<List<ScenicVO>> hot(@RequestParam(required = false) Integer limit) {
        return Result.success(scenicService.hot(limit, BaseContext.getCurrentId()));
    }

    @GetMapping("/search")
    @ApiOperation("搜索景点")
    public Result<List<ScenicVO>> search(@RequestParam String keyword) {
        log.info("搜索景点：keyword={}", keyword);
        return Result.success(scenicService.search(keyword, BaseContext.getCurrentId()));
    }

    @PostMapping("/{id}/collect")
    @ApiOperation("收藏景点")
    public Result collect(@PathVariable Long id) {
        log.info("收藏景点：id={}", id);
        scenicService.collect(id, BaseContext.getCurrentId());
        return Result.success();
    }

    @DeleteMapping("/{id}/collect")
    @ApiOperation("取消收藏景点")
    public Result uncollect(@PathVariable Long id) {
        log.info("取消收藏景点：id={}", id);
        scenicService.uncollect(id, BaseContext.getCurrentId());
        return Result.success();
    }
}
