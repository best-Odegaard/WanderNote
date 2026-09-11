package com.gkv.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelJournalVO {
    private long id;
    private long userId;
    private String title;
    private String content;
    private String avatar;
    private String nickname;
    private String location;
    private List<String> tags;
    private List<String> imgUrls;
    private Integer likeCount;
    private Integer collectCount;
    /** 当前登录用户是否已点赞 */
    private Boolean isLiked;
    /** 当前登录用户是否已收藏 */
    private Boolean isCollected;
    private LocalDateTime createTime;
}
