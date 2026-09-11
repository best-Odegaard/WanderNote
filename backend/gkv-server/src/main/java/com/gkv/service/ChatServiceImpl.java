package com.gkv.service;

import com.gkv.dto.*;
import com.gkv.entity.ChatHistory;
import com.gkv.mapper.ChatHistoryMapper;
import com.gkv.utils.AgentHttpUtil;
import com.gkv.vo.TravelResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private AgentHttpUtil agentHttpUtil;

    @Resource
    private ChatHistoryMapper chatHistoryMapper;

    @Override
    public ChatResponseDTO chat(ChatRequestDTO req) {
        String sessionId = req.getSession_id();

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            req.setSession_id(sessionId);
        }

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
            record.setSessionId(sessionId);
            record.setRole(msg.getRole());
            record.setContent(msg.getContent());
            record.setCreateTime(LocalDateTime.now());
            records.add(record);
        }
        for (ChatHistory record : records) {
            chatHistoryMapper.insert(record);
        }

        agentResp.setSession_id(sessionId);
        agentResp.setChat_history(updatedHistory);
        return agentResp;
    }

    /**
     * 长对话后生成完整行程计划，返回含 plan_data.day_list 的完整 PlanResponseDTO，
     * 供前端展示每日景点预览。
     */
    @Override
    public PlanResponseDTO generatePlan(ChatRequestDTO req) {
        if (req.getSession_id() == null || req.getSession_id().isEmpty()) {
            req.setSession_id(UUID.randomUUID().toString().replace("-", ""));
        }
        return agentHttpUtil.callPlan(req);
    }

    /** 查询会话历史消息：按 create_time 正序，供前端刷新页面后恢复多轮上下文 */
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
        );        List<ChatMessageDTO> messages = new ArrayList<>();
        for (ChatHistory record : records) {
            ChatMessageDTO msg = new ChatMessageDTO();
            msg.setRole(record.getRole());
            msg.setContent(record.getContent());
            messages.add(msg);
        }
        return messages;
    }
}