package com.gkv.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TravelResultVO {
    private Long id;
    // 用户原始需求
    private String destination;
    private Integer travelDays;
    private String travelPreference;
    private Integer peopleNum;
    private BigDecimal budget;
    private String startCity;
    // AI行程结果（aiPlan 走表单式生成）
    private LocalDate ticketDate;
    private LocalTime ticketTime;
    private String scenicName;
    private BigDecimal ticketPrice;
    private String agentFullStrategy;
    // AI行程结果（generatePlan 走长对话式生成）
    private String planTitle;
    private String totalWalk;
    private String firstDayDate;
    private String firstSpotName;
}
