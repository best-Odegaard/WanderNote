package com.gkv.controller.user;

import com.alibaba.fastjson.JSON;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/travel")
@Slf4j
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

    /** SSE 连接最长存活时间：模型生成慢时留足余量，超时后前端回落整包接口 */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    /**
     * SSE 专用线程池。
     *
     * 对话是「一次请求占住几十秒」的长阻塞任务，直接在 Tomcat 请求线程里跑到结束会把
     * 工作线程耗光；SseEmitter 要求业务方自己提供执行线程。
     * 用守护线程：应用关闭时不会因为还有连接挂着而卡住退出。
     */
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "chat-sse");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdownStreamExecutor() {
        streamExecutor.shutdown();
    }

    /**
     * AI 聊天对话（SSE 流式）。
     *
     * 为什么单独开一个端点而不是改造 /travel/chat：
     * 前端在小程序端拿不到流式读取能力，需要保留整包接口作为回落；
     * 而且两者的失败模式不同（半截气泡 vs 整体失败），混在一起前端很难处理。
     *
     * 事件协议（前端按 event 名分发）：
     *   delta —— 增量文本，data 是 JSON 字符串字面量（已转义换行，避免被 SSE 的分行规则切开）
     *   done  —— 整轮结束，data 是 ChatResponseDTO 的 JSON（含 session_id / chat_history / slot_state）
     *   error —— 出错，data 是给用户看的一句话
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("AI聊天对话（SSE 流式）")
    public SseEmitter chatStream(@RequestBody ChatRequestDTO req) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 预算校验放在推流之前：等 SSE 已经开始再报错，前端只会留下一个半截气泡
        String budgetErr = validateBudget(req.getBase_info() != null ? req.getBase_info().getBudget() : null);
        if (budgetErr != null) {
            sendEvent(emitter, "error", toAsciiJson(budgetErr));
            emitter.complete();
            return emitter;
        }

        streamExecutor.execute(() -> {
            try {
                ChatResponseDTO resp = chatService.chatStream(req, chunk ->
                        // 客户端提前断开时这里会抛异常，异常一路冒泡出去中断对 Agent 的读取，
                        // 避免用户已经走了还在白烧模型额度
                        sendEvent(emitter, "delta", toAsciiJson(chunk)));
                sendEvent(emitter, "done", toAsciiJson(resp));
                emitter.complete();
            } catch (Exception e) {
                log.warn("[Chat] SSE 流式对话中断: {}", e.getMessage());
                // 文案同样要走 toAsciiJson：否则中文会被落成 "?"（这个坑实测踩过）
                sendEvent(emitter, "error", toAsciiJson("AI 服务暂时不可用，请稍后重试"));
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * 发 SSE 事件用的文本类型。
     *
     * 带上 charset=UTF-8 是应该的，但**光靠它不够**：
     * 实测过 SSE 事件体里的中文会被落成 "?"（0x3F），把栈里所有环节的字符集都对齐后
     * 才定位到是写出阶段按非 UTF-8 落笔。所以真正的保险是下面的 {@link #toAsciiJson}。
     */
    private static final MediaType TEXT_PLAIN_UTF8 =
            new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);

    /**
     * 把对象序列化成**纯 ASCII** 的 JSON。
     *
     * 所有非 ASCII 字符转成反斜杠-u 形式的 Unicode 转义（Java 源码里不能直接写出那个序列，
     * 注释里写也会被当成非法转义，所以这里描述为"反斜杠-u 形式"）。
     * 于是无论中间层（消息转换器 / 代理 / 容器）按什么字符集落笔，字节都是安全的 ASCII，
     * 前端 JSON.parse 后能原样拿回中文。
     *
     * 这段代码不是过度设计：SSE 事件体里的中文曾实测被写成 "?"，
     * 而 SSE 恰恰是"中间层最多"的一条链路（只能走明文响应体，没有 JSON 转换器帮忙定编码）。
     */
    private static String toAsciiJson(Object value) {
        String json = JSON.toJSONString(value);
        StringBuilder sb = new StringBuilder(json.length() + 32);
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                // 代理对的两个 char 各自转义，JSON 规范允许，JS 的 JSON.parse 也能正确还原
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 发一个 SSE 事件；失败（连接已断）时抛出去，由调用方终止整轮 */
    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, TEXT_PLAIN_UTF8));
        } catch (Exception e) {
            throw new IllegalStateException("SSE 连接已断开", e);
        }
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