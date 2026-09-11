package com.gkv.controller.user;

import com.gkv.dto.TripImportLinkDTO;
import com.gkv.result.Result;
import com.gkv.service.TripPlanService;
import com.gkv.vo.TripPlanVO;
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

@RestController
@RequestMapping("/trip")
@Slf4j
@Api(tags = "行程相关接口")
public class TripPlanController {

    @Autowired
    private TripPlanService tripPlanService;

    @PostMapping("/import/link")
    @ApiOperation("根据链接创建行程")
    public Result<TripPlanVO> importFromLink(@RequestBody @Validated TripImportLinkDTO dto) {
        log.info("根据链接创建行程：{}", dto.getSourceUrl());
        TripPlanVO tripPlan = tripPlanService.importFromLink(dto);
        return Result.success(tripPlan);
    }

    @GetMapping("/list")
    @ApiOperation("我的行程列表")
    public Result<List<TripPlanVO>> list() {
        return Result.success(tripPlanService.listMine());
    }

    @PostMapping("/save")
    @ApiOperation("保存行程（无id新增，有id更新）")
    public Result<TripPlanVO> save(@RequestBody TripPlanVO vo) {
        log.info("保存行程：title={}, id={}", vo.getTitle(), vo.getId());
        return Result.success(tripPlanService.save(vo));
    }

    @GetMapping("/{id}")
    @ApiOperation("获取行程详情")
    public Result<TripPlanVO> getById(@PathVariable Long id) {
        return Result.success(tripPlanService.getById(id));
    }

    @PutMapping("/{id}")
    @ApiOperation("更新行程")
    public Result<TripPlanVO> update(@PathVariable Long id, @RequestBody TripPlanVO vo) {
        return Result.success(tripPlanService.update(id, vo));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除行程")
    public Result<Void> delete(@PathVariable Long id) {
        tripPlanService.delete(id);
        return Result.success();
    }
}
