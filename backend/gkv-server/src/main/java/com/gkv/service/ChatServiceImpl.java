package com.gkv.service;

import com.gkv.dto.*;
import com.gkv.context.BaseContext;
import com.gkv.entity.ChatHistory;
import com.gkv.mapper.ChatHistoryMapper;
import com.gkv.utils.AgentHttpUtil;
import com.gkv.vo.TravelResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Resource
    private AgentHttpUtil agentHttpUtil;

    @Resource
    private ChatHistoryMapper chatHistoryMapper;

    @Resource
    private UserProfileService userProfileService;

    @Override
    public ChatResponseDTO chat(ChatRequestDTO req) {
        // 1) 从登录上下文取 userId（对话链路已收回登录态，这里必有值；取不到也照常对话，只是不沉淀）
        Long userId = BaseContext.getCurrentId();

        String sessionId = req.getSession_id();

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            req.setSession_id(sessionId);
        }

        // 2) 调 AI 之前注入画像回灌文本。
        //    顺序很关键：注入必须在调模型之前，否则模型看到的是没有画像的 prompt。
        injectProfileNote(req, userId);

        ChatResponseDTO agentResp = agentHttpUtil.callChat(req);

        List<ChatMessageDTO> updatedHistory = new ArrayList<>();
        if (req.getChat_history() != null) {
            updatedHistory.addAll(req.getChat_history());
        }

        ChatMessageDTO userMsg = new ChatMessageDTO();
        userMsg.setRole("user");
        userMsg.setContent(req.getUser_input());
        updatedHistory.add(userMsg);

        ChatMessageDTO assistantMsg = new ChatMessageDTO();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(agentResp.getReply());
        updatedHistory.add(assistantMsg);

        // 只入库本轮新增的 user + assistant 两条：
        // 完整历史由前端每轮回传（updatedHistory 仅用于响应），
        // 避免把历史消息重复 insert 造成 chat_history 表数据膨胀
        List<ChatHistory> records = new ArrayList<>();
        for (ChatMessageDTO msg : new ChatMessageDTO[]{userMsg, assistantMsg}) {
            ChatHistory record = new ChatHistory();
            // 3) 带上 user_id：画像要按用户聚合，必须知道每条消息属于谁
            record.setUserId(userId);
            record.setSessionId(sessionId);
            record.setRole(msg.getRole());
            record.setContent(msg.getContent());
            record.setCreateTime(LocalDateTime.now());
            records.add(record);
        }
        for (ChatHistory record : records) {
            chatHistoryMapper.insert(record);
        }

        // 4) 落库后做规则累加。整个画像链路包在 try/catch 里：
        //    画像任何失败都不能让用户的对话失败（画像表还没建、字段不兼容等都不能冒泡）。
        try {
            userProfileService.accumulate(userId, req.getBase_info(), req.getUser_input());
        } catch (Exception e) {
            log.warn("[Chat] 画像规则累加失败，不影响本轮对话: {}", e.getMessage(), e);
        }

        agentResp.setSession_id(sessionId);
        agentResp.setChat_history(updatedHistory);
        return agentResp;
    }

    /**
     * 注入画像回灌文本到 base_info.profile_note
     *
     * 必须在本方法的调用线程（请求线程）里做：如果行程生成是异步任务，
     * 后台线程拿不到登录上下文（ThreadLocal 取不到用户），回灌就会静默失效。
     * 这里 chat 是同步链路，但 generatePlan 是异步的 —— 那边在 PlanTaskService.submit 里注入。
     */
    private void injectProfileNote(ChatRequestDTO req, Long userId) {
        BaseInfoDTO base = req.getBase_info();
        if (base == null) {
            return;
        }
        try {
            String note = userProfileService.buildInjectNote(userId);
            // 无条件覆盖：这个字段只由服务端注入，客户端传什么都不作数
            base.setProfile_note(note);
            if (note != null) {
                log.info("[Chat] 已为用户{}注入画像回灌文本，长度={}", userId, note.length());
            }
        } catch (Exception e) {
            log.warn("[Chat] 构建画像回灌文本失败，按无画像继续: {}", e.getMessage());
            base.setProfile_note(null);
        }
    }

    /**
     * 长对话后生成完整行程计划，返回含 plan_data.day_list 的完整 PlanResponseDTO，
     * 供前端展示每日景点预览。
     *
     * 注意：控制器的 /travel/generatePlan 现在走 PlanTaskService（异步任务版），
     * 这个方法是同步版的遗留入口，当前没有调用方。这里同样注入画像回灌文本 ——
     * 否则将来谁把它接回去，就会出现"只有异步链路有画像、同步链路没有"的静默差异。
     */
    @Override
    public PlanResponseDTO generatePlan(ChatRequestDTO req) {
        if (req.getSession_id() == null || req.getSession_id().isEmpty()) {
            req.setSession_id(UUID.randomUUID().toString().replace("-", ""));
        }
        injectProfileNote(req, BaseContext.getCurrentId());
        return agentHttpUtil.callPlan(req);
    }

    /**
     * 查询会话历史消息：按 create_time 正序，供前端刷新页面后恢复多轮上下文
     *
     * 归属校验：老数据 user_id 为 NULL（迁移时不做归属回溯），所以不能拿 user_id 直接过滤，
     * 否则所有历史会话都恢复不了。这里改成"只要这个会话里出现过别人的消息就拒绝返回" ——
     * 既保住了老会话的恢复能力，又堵住了登录用户拿到别人 sessionId 就能读别人对话的口子。
     */
    @Override
    public List<ChatMessageDTO> listHistory(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChatHistory> records = chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getSessionId, sessionId)
                        .orderByAsc(ChatHistory::getCreateTime)
                        .orderByAsc(ChatHistory::getId)
        );
        Long userId = BaseContext.getCurrentId();
        for (ChatHistory record : records) {
            if (record.getUserId() != null && !Objects.equals(record.getUserId(), userId)) {
                log.warn("[Chat] 会话{}不属于当前用户{}，拒绝返回历史", sessionId, userId);
                return new ArrayList<>();
            }
        }
        List<ChatMessageDTO> messages = new ArrayList<>();
        for (ChatHistory record : records) {
            ChatMessageDTO msg = new ChatMessageDTO();
            msg.setRole(record.getRole());
            msg.setContent(record.getContent());
            messages.add(msg);
        }
        return messages;
    }
}