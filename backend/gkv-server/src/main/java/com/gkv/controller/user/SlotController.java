package com.gkv.controller.user;

import com.gkv.dto.SlotAnswerDTO;
import com.gkv.result.Result;
import com.gkv.service.slot.SlotEngine;
import com.gkv.vo.SlotStateVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 槽位问答接口 —— 支撑「点一下就能完善行程」的对话交互。
 *
 * 为什么单独开一个 Controller 而不是塞进 TravelController：
 * 这条链路不调大模型、不写库，是纯规则的毫秒级接口，
 * 与 TravelController 里的 AI 长链路（对话 / 异步生成）在延迟与失败模式上完全不同，
 * 混在一起会让限流与超时策略没法分别设置。
 *
 * 鉴权：本接口不在匿名白名单内，与 /travel/chat 一样需要登录
 * （槽位快照当前存进程内，按会话隔离，无登录态就没有会话归属）。
 */
@RestController
@RequestMapping("/travel/plan")
@Slf4j
@Api(tags = "行程槽位问答接口")
public class SlotController {

    @Resource
    private SlotEngine slotEngine;

    @PostMapping("/slot")
    @ApiOperation("回答一个槽位问题（点选项 / 点跳过），返回最新快照与下一个问题")
    public Result<SlotStateVO> answer(@RequestBody SlotAnswerDTO req) {
        if (req == null || req.getSessionId() == null || req.getSessionId().trim().isEmpty()) {
            return Result.error("缺少会话 id");
        }
        return Result.success(slotEngine.answer(req));
    }

    @GetMapping("/slot/state")
    @ApiOperation("查询槽位快照（刷新页面后恢复进度）")
    public Result<SlotStateVO> state(@RequestParam("sessionId") String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Result.error("缺少会话 id");
        }
        return Result.success(slotEngine.snapshot(sessionId));
    }
}
