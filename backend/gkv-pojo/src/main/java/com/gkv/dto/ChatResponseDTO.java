package com.gkv.dto;

import com.gkv.vo.SlotStateVO;
import lombok.Data;

import java.util.List;

@Data
public class ChatResponseDTO {
    private String session_id;
    private String reply;
    private List<ChatMessageDTO> chat_history;

    /**
     * 槽位快照：本轮对话后用户的行程信息收集到哪一步了。
     *
     * 由 Java 侧槽位引擎在调用智能体之后填充（智能体不感知槽位），
     * 前端据此渲染可点击选项、完整度进度条与渐显的能力入口。
     */
    private SlotStateVO slot_state;

    /**
     * 「猜你想问」：本轮之后推荐给用户的几个追问，点一下就成为下一轮输入。
     *
     * 与 slot_state 的分工：slot_state 收**参数**（去哪/几天/预算），这里给**话题**
     * （那地方有什么好吃、怎么去方便、带娃怎么排）。纯规则生成，不额外调用大模型，
     * 所以不会给每轮回复增加延迟。
     */
    private List<String> suggested_questions;
}
