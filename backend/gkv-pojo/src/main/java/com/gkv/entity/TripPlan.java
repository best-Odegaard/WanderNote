package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trip_plan")
public class TripPlan {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String chatSessionId;
    private Long userId;
    private String title;
    private String fromCity;
    private String toCity;
    /** 出发日期（YYYY-MM-DD） */
    private String startDate;
    /** 结束日期（YYYY-MM-DD） */
    private String endDate;
    private Integer days;
    private Integer people;
    private BigDecimal budget;
    private BigDecimal estimatedCost;
    private String hotel;
    private String tags;
    private String dayPlans;
    private String cover;
    private String sourceUrl;
    private Integer sourceType;
    private Integer visibility;
    private Integer status;
    private Integer shareCount;
    private Integer likeCount;

    /** 来源精选行程id（复制来源） */
    private Long sourceFeaturedId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
