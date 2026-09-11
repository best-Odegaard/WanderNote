package com.gkv.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityVO {
    private Long id;
    private String title;
    private String cover;
    private String city;
    private String location;
    private String category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private String organizer;
    private Integer enrollCount;
    /** 当前登录用户是否已收藏 */
    private Boolean isCollected;
}
