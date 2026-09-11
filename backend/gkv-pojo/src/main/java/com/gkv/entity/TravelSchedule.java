package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;



@Data
@TableName("travel_schedule")
public class TravelSchedule {
        @TableId(type = IdType.AUTO)
        private Long id;

        // 前端表单原始数据
        private String destination;
        private Integer travelDays;
        private String travelPreference;
        private Integer peopleNum;
        private BigDecimal budget;
        private String startCity;

        // AI返回的内容
        /**
         * 整个行程标题
         */
        private String planTitle;

        /**
         * 行程天数标识 Day1/Day2
         */
        private String dayTag;

        /**
         * 实际出行日期
         */
        private LocalDate realTravelDate;

        /**
         * 游玩开始时间
         */
        private LocalTime visitStartTime;

        /**
         * 游玩结束时间
         */
        private LocalTime visitEndTime;

        /**
         * 游玩时段原文
         */
        private String visitTimeRange;

        /**
         * 景点名称
         */
        private String scenicName;

        /**
         * 景点营业时间
         */
        private String openTimeDesc;

        /**
         * 景点地址
         */
        private String location;

        /**
         * 景点标签
         */
        private String featureTag;

        /**
         * 门票价格，为空代表现场咨询
         */
        private BigDecimal ticketPrice;

        /**
         * 门票原始文案
         */
        private String ticketDesc;

        /**
         * 预算范围
         */
        private String budgetRange;

        /**
         * 旅行节奏
         */
        private String travelPace;

        /**
         * 游玩偏好体验
         */
        private String experienceDemand;

        /**
         * 同行人员
         */
        private String travelPeople;

        /**
         * 用户自定义额外需求
         */
        private String customDemand;

        /**
         * AI返回完整原始JSON
         */
        private String originAiJson;

        /**
         * AI生成完整行程攻略
         */
        private String agentTravelStrategy;
        @TableLogic
        private Integer isDeleted;
    }
