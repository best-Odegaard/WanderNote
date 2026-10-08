package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行程住宿（这条行程晚上住哪）
 *
 * 为什么单独一张表，而不是只用 trip_plan.hotel（名称字符串）：
 *   名字不够用。路线页要把当天行程补成「酒店出发 → 各站 → 返回酒店」的闭环，
 *   必须有坐标；用户选完酒店杀进程再打开行程时，名字还在但坐标丢了，
 *   闭环就退化回「第一站 → 最后一站」。所以名称 + 坐标 + 地址 + 参考价 + 深链一起落库。
 *
 * 一个行程一条记录（trip_id 唯一索引），service 层做 upsert。
 * hotel_code 只做关联线索，不建外键：候选库下架某家酒店后，
 * 历史行程仍要能显示名称与坐标，所以这里是冗余存储。
 */
@Data
@TableName("trip_hotel")
public class TripHotel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 行程id（trip_plan.id） */
    private Long tripId;

    /** 候选酒店编码；用户用的是兜底候选（库里没有）时为空 */
    private String hotelCode;

    private String hotelName;

    private BigDecimal lng;
    private BigDecimal lat;

    private String address;

    /** 风格标签，逗号分隔 */
    private String styleTags;

    private LocalDate checkin;
    private LocalDate checkout;

    /** 参考价（元/晚，以携程为准） */
    private BigDecimal refPrice;

    /** 携程深链（CtripUrlBuilder 生成，落库便于追溯历史链接） */
    private String ctripUrl;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
