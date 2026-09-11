package com.gkv.service;

import com.gkv.dto.ChatMessageDTO;
import com.gkv.dto.ChatRequestDTO;
import com.gkv.dto.ChatResponseDTO;
import com.gkv.dto.PlanResponseDTO;
import com.gkv.vo.TravelResultVO;

import java.util.List;

public interface ChatService {
    ChatResponseDTO chat(ChatRequestDTO req);
    /** 长对话后生成完整行程计划（返回含 plan_data.day_list 的完整响应） */
    PlanResponseDTO generatePlan(ChatRequestDTO req);
    /** 查询会话历史消息（chat_history 表，按时间正序） */
    List<ChatMessageDTO> listHistory(String sessionId);
}