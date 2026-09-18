package com.gkv.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TripPlanVO {
    private Long id;
    private String chatSessionId;
    private Long userId;
    private String title;
    private String fromCity;
    private String toCity;
    /** 出发日期（YYYY-MM-DD），前端行程卡片按它判定待出行/进行中/已结束 */
    private String startDate;
    /** 结束日期（YYYY-MM-DD） */
    private String endDate;
    private Integer days;
    private Integer people;
    private BigDecimal budget;
    private BigDecimal estimatedCost;
    private String hotel;
    private List<String> tags;
    private Object dayPlans;
    private String cover;
    private String sourceUrl;
    /**
     * 解析链接时自动抓到的网页正文，只在「解析生成行程草稿」接口返回，其他接口为 null。
     * 管理端拿它填进「行程原文」，让人看到这次生成到底用了什么内容。
     */
    private String sourceText;
    private Integer sourceType;
    private Integer visibility;
    private Integer status;
    private Integer shareCount;
    private Integer likeCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 来源精选行程id（复制来源） */
    private Long sourceFeaturedId;
}
