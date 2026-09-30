package com.gkv.service;

import com.gkv.dto.ChatMessageDTO;
import com.gkv.dto.ChatRequestDTO;
import com.gkv.dto.ChatResponseDTO;
import com.gkv.dto.PlanResponseDTO;
import com.gkv.vo.TravelResultVO;

import java.util.List;
import java.util.function.Consumer;

public interface ChatService {
    ChatResponseDTO chat(ChatRequestDTO req);

    /**
     * 流式对话：模型每吐出一段文本就回调 onDelta，方法本身阻塞到整轮结束。
     *
     * 与 {@link #chat} 的唯一区别是「边收边给」——落到库里的内容、返回的会话与槽位快照完全一致。
     * 之所以把回调而不是 SseEmitter 放在服务层：服务不该依赖 Web 类型，
     * SSE 的组装与连接生命周期交给 Controller。
     *
     * @param onDelta 增量回调；为 null 时行为等同 {@link #chat}
     */
    ChatResponseDTO chatStream(ChatRequestDTO req, Consumer<String> onDelta);

    /** 长对话后生成完整行程计划（返回含 plan_data.day_list 的完整响应） */
    PlanResponseDTO generatePlan(ChatRequestDTO req);
    /** 查询会话历史消息（chat_history 表，按时间正序） */
    List<ChatMessageDTO> listHistory(String sessionId);
}