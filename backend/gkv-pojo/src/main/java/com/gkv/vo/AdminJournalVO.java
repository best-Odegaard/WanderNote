package com.gkv.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端游记列表项（含作者信息与评论数）
 */
@Data
public class AdminJournalVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String title;
    private String location;
    private Integer status;
    private Integer likeCount;
    private Integer collectCount;
    private Long commentCount;
    private LocalDateTime createTime;
}
