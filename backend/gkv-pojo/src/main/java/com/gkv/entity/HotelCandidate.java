package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 酒店候选（自建精选库，运营维护）
 *
 * 为什么自建而不是拉第三方实时数据：
 *   携程没有公开的开放 API（见 产品项目文档 5.7.2），交易只能跳转。
 *   自建库保证「档次筛选 / 位置筛选」的体验可控，价格一律标「参考价，以携程为准」。
 *
 * hotelCode 是业务主键（如 zq-sf-1），不是自增 id：
 *   前端 utils/hotels.ts 的本地兜底表用同一套 code，
 *   接口不可用时走本地兜底，用户上次选的那家还能按 code 对上号。
 *
 * 坐标（lng/lat）是必需的：路线页靠它把当天行程补成
 * 「酒店出发 → 各站 → 返回酒店」的闭环（见 utils/routeBuild.ts 的 RouteHotel）。
 */
@Data
@TableName("hotel_candidate")
public class HotelCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码（前端本地兜底表同款） */
    private String hotelCode;

    private String city;
    private String name;

    /** 档次：经济型 / 舒适型 / 高档型 / 特色民宿 */
    private String level;

    /** 评分 0~5 */
    private BigDecimal rating;

    /** 参考价（元/晚）；不是实时价，展示时必须带「以携程为准」 */
    private BigDecimal refPrice;

    private String address;

    /** 附近地标：携程深链关键词 + 位置筛选都用它 */
    private String nearbyLandmark;

    /** 距市中心/主要景区直线距离（公里） */
    private BigDecimal distanceKm;

    /** 风格标签，逗号分隔 */
    private String styleTags;

    private String cover;

    private BigDecimal lng;
    private BigDecimal lat;

    /** 一句话简介 */
    private String intro;

    private Integer sortOrder;

    /** 状态：0下架 1上架 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
