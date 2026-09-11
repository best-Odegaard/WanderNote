package com.gkv.controller.admin;

import com.gkv.annotation.RequirePerm;
import com.gkv.constant.PermConstant;
import com.gkv.dto.FeedbackHandleDTO;
import com.gkv.dto.UserFeedbackPageDTO;
import com.gkv.result.PageResult;
import com.gkv.result.Result;
import com.gkv.service.AdminFeedbackService;
import com.gkv.vo.UserFeedbackVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-意见反馈
 */
@RestController
@RequestMapping("/admin/feedback")
@Slf4j
@Api(tags = "管理端-意见反馈")
public class AdminFeedbackController {

    @Autowired
    private AdminFeedbackService adminFeedbackService;

    @GetMapping
    @RequirePerm(PermConstant.FEEDBACK_VIEW)
    @ApiOperation("意见反馈分页列表（状态/类型/关键词筛选）")
    public Result<PageResult<UserFeedbackVO>> page(UserFeedbackPageDTO dto) {
        log.info("查询意见反馈：status={}, type={}, keyword={}, page={}",
                dto.getStatus(), dto.getType(), dto.getKeyword(), dto.getPageNum());
        return Result.success(adminFeedbackService.page(dto));
    }

    @GetMapping("/{id}")
    @RequirePerm(PermConstant.FEEDBACK_VIEW)
    @ApiOperation("意见反馈详情")
    public Result<UserFeedbackVO> detail(@PathVariable Long id) {
        return Result.success(adminFeedbackService.detail(id));
    }

    @PutMapping("/{id}/handle")
    @RequirePerm(PermConstant.FEEDBACK_HANDLE)
    @ApiOperation("回复并标记已处理")
    public Result handle(@PathVariable Long id, @RequestBody FeedbackHandleDTO dto) {
        log.info("处理意见反馈：id={}", id);
        adminFeedbackService.handle(id, dto);
        return Result.success();
    }

    @PutMapping("/{id}/close")
    @RequirePerm(PermConstant.FEEDBACK_HANDLE)
    @ApiOperation("关闭反馈")
    public Result close(@PathVariable Long id) {
        log.info("关闭意见反馈：id={}", id);
        adminFeedbackService.close(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequirePerm(PermConstant.FEEDBACK_HANDLE)
    @ApiOperation("删除反馈")
    public Result delete(@PathVariable Long id) {
        log.info("删除意见反馈：id={}", id);
        adminFeedbackService.delete(id);
        return Result.success();
    }
}
