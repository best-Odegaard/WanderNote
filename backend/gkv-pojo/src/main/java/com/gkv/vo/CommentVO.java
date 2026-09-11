package com.gkv.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long id;
    private Long journalId;
    private Long userId;
    private String username;
    private String nickname;
    private String content;
    private String avatar;
    private Integer status;
    private Long parentCommentIds;
    private String parentUsername;
    private LocalDateTime createTime;
}
