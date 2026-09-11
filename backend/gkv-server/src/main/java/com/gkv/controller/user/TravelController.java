package com.gkv.controller.user;

import com.gkv.dto.ChatMessageDTO;
import com.gkv.dto.ChatRequestDTO;
import com.gkv.dto.ChatResponseDTO;
import com.gkv.dto.TravelScheduleDTO;
import com.gkv.result.Result;
import com.gkv.service.ChatService;
import com.gkv.service.PlanTaskService;
import com.gkv.service.TravelScheduleService;
import com.gkv.vo.PlanSubmitVO;
import com.gkv.vo.PlanTaskVO;
import com.gkv.vo.TravelResultVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/travel")
@Api(tags = "AI行程助手接口")
public class TravelController {
    @Resource
    private TravelScheduleService travelScheduleService;

    @Resource
    private ChatService chatService;

    @Resource
    private PlanTaskService planTaskService;

    /**
     * 前端点击【智能规划】提交表单调用该接口
     */
    @PostMapping("/aiPlan")
    @ApiOperation("智能规划行程（表单式）")
    public Result<TravelResultVO> aiPlan(@Validated @RequestBody TravelScheduleDTO ScheduleDTO){
        // 预算约束：不允许负数预算进入智能规划
        if (ScheduleDTO.getBudget() != null && ScheduleDTO.getBudget().compareTo(BigDecimal.ZERO) < 0) {
            return Result.error("预算不能为负数");
        }
        TravelResultVO vo = travelScheduleService.createPlanByAi(ScheduleDTO);
        return Result.success(vo);
    }

    /**
     * AI 长对话聊天
     */
    @PostMapping("/chat")
    @ApiOperation("AI聊天对话")
    public Result<ChatResponseDTO> chat(@RequestBody ChatRequestDTO req) {
        // 预算约束：不允许负数预算进入智能规划
        String budgetErr = validateBudget(req.getBase_info() != null ? req.getBase_info().getBudget() : null);
        if (budgetErr != null) {
            return Result.error(budgetErr);
        }
        ChatResponseDTO resp = chatService.chat(req);
        return Result.success(resp);
    }

    /**
     * 长对话后生成详细行程计划。
     * 异步任务：命中缓存直接返回完整行程（fromCache=true），否则返回 taskId，
     * 前端轮询 /travel/plan/status/{taskId} 获取阶段进度与骨架预览。
     */
    @PostMapping("/generatePlan")
    @ApiOperation("提交行程生成任务（异步，返回 taskId 或缓存结果）")
    public Result<PlanSubmitVO> generatePlan(@RequestBody ChatRequestDTO req) {
        // 预算约束：不允许负数预算进入智能规划
        String budgetErr = validateBudget(req.getBase_info() != null ? req.getBase_info().getBudget() : null);
        if (budgetErr != null) {
            return Result.error(budgetErr);
        }
        return Result.success(planTaskService.submit(req));
    }

    /**
     * 预算约束校验：预算字符串（如 "2000"、"人均1000"、"2000-3000"）中的预算值不能为负数
     * @return 校验失败时的错误信息，通过则返回 null
     */
    private String validateBudget(String budget) {
        if (budget == null || budget.trim().isEmpty()) {
            return null;
        }
        Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(budget.trim());
        if (m.find() && Double.parseDouble(m.group()) < 0) {
            return "预算不能为负数";
        }
        return null;
    }

    /**
     * 查询会话历史消息（供前端刷新页面后恢复多轮上下文）
     */
    @GetMapping("/history")
    @ApiOperation("查询会话历史消息")
    public Result<List<ChatMessageDTO>> history(@RequestParam("sessionId") String sessionId) {
        return Result.success(chatService.listHistory(sessionId));
    }

    /**
     * 查询行程生成任务状态（前端轮询）
     */
    @GetMapping("/plan/status/{taskId}")
    @ApiOperation("查询行程生成任务状态")
    public Result<PlanTaskVO> planStatus(@PathVariable("taskId") String taskId) {
        PlanTaskVO vo = planTaskService.getStatus(taskId);
        if (vo == null) {
            return Result.error("任务不存在或已过期");
        }
        return Result.success(vo);
    }

    /**
     * 取消行程生成任务
     */
    @PostMapping("/plan/cancel/{taskId}")
    @ApiOperation("取消行程生成任务")
    public Result<Void> cancelPlan(@PathVariable("taskId") String taskId) {
        planTaskService.cancel(taskId);
        return Result.success();
    }
}