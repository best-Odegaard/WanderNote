package com.gkv.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequestDTO {
    private String session_id;
    private String user_input;
    private BaseInfoDTO base_info;
    private List<ChatMessageDTO> chat_history;
}