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
    private Integer days;
    private Integer people;
    private BigDecimal budget;
    private BigDecimal estimatedCost;
    private String hotel;
    private List<String> tags;
    private Object dayPlans;
    private String cover;
    private String sourceUrl;
    private Integer sourceType;
    private Integer visibility;
    private Integer status;
    private Integer shareCount;
    private Integer likeCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
