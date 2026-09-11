package com.gkv.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 意见反馈 VO（含提交用户信息）
 */
@Data
public class UserFeedbackVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String type;
    private String content;
    private List<String> images;
    private String contact;
    private Integer status;
    private String reply;
    private Long adminId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
