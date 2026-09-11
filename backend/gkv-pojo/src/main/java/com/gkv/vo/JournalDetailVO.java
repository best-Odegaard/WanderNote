package com.gkv.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端游记详情（含作者信息与评论列表）
 */
@Data
public class JournalDetailVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String title;
    private String content;
    private String images;
    private String tags;
    private String location;
    private Integer status;
    private Integer likeCount;
    private Integer collectCount;
    private LocalDateTime createTime;
    private List<CommentVO> comments;
}
