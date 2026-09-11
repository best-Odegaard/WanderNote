package com.gkv.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatResponseDTO {
    private String session_id;
    private String reply;
    private List<ChatMessageDTO> chat_history;
}