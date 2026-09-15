package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_history")
public class ChatHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户ID：存量历史消息为 NULL（不做归属回溯），画像读取侧忽略 NULL 行 */
    private Long userId;
    private String sessionId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}