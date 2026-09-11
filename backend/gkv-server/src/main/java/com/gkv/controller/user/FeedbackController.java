package com.gkv.controller.user;

import com.gkv.dto.UserFeedbackSubmitDTO;
import com.gkv.result.Result;
import com.gkv.service.FeedbackService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 意见反馈相关接口（用户端）
 */
@RestController
@RequestMapping("/feedback")
@Slf4j
@Api(tags = "意见反馈接口")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping("/submit")
    @ApiOperation("提交意见反馈（需登录）")
    public Result submit(@RequestBody @Validated UserFeedbackSubmitDTO dto) {
        log.info("用户提交意见反馈，type={}", dto.getType());
        feedbackService.submit(dto);
        return Result.success();
    }
}
