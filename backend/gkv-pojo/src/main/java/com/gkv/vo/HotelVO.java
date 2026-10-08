package com.gkv.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 酒店候选（/hotel/search 与 /hotel/select 的返回结构）
 *
 * 字段刻意与前端 utils/hotels.ts 的 HotelOption 一一对应：
 * 接口可用时前端直接用这份数据，接口不可用时前端用手写兜底表 ——
 * 两边结构一致，页面和路线模型不用写两套逻辑。
 *
 * 价格语义（重要）：
 *   price 是**参考价**，不是实时价、不代表有房。
 *   前端展示必须带「参考价，以携程为准」，禁止出现「可订」「有房」字样。
 */
@Data
public class HotelVO {

    /** 业务编码（hotel_code）；兜底酒店没有编码，前端用本地 id */
    private String id;

    private String name;
    private String city;

    /** 档次：经济型 / 舒适型 / 高档型 / 特色民宿 */
    private String level;

    /** 评分 0~5 */
    private BigDecimal rating;

    /** 参考价（元/晚），以携程为准 */
    private BigDecimal price;

    private String address;

    /** 附近地标（深链关键词 + 位置筛选） */
    private String nearbyLandmark;

    /** 距市中心/主要景区直线距离（公里） */
    private BigDecimal distanceKm;

    private List<String> tags;

    private String cover;

    private BigDecimal lng;
    private BigDecimal lat;

    /** 一句话简介 */
    private String desc;

    /**
     * 携程深链（CtripUrlBuilder 单一出口生成）。
     * 为空表示深链模板不可用，前端应隐藏「携程预订」按钮而不是给一个死链。
     */
    private String ctripUrl;

    // ── 以下三个字段只有 GET /hotel/trip/{tripId} 会填 ──
    // 放在同一个 VO 里是为了让前端只维护一种酒店结构，
    // 「行程已保存的住宿」与「搜索出来的候选」在页面上是同一种卡片。

    /** 已保存住宿所属的行程id */
    private Long tripId;

    /** 入住日期（YYYY-MM-DD） */
    private String checkin;

    /** 离店日期（YYYY-MM-DD） */
    private String checkout;
}
