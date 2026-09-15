package com.gkv.vo;

import lombok.Data;

/**
 * 精选行程 VO
 *
 * 列表接口（首页轮播）只用概要字段；详情接口额外回传 trip（完整行程对象）。
 * trip 由 tripJson 解析而来，前端可直接当作 TripPlan 喂给行程详情页渲染。
 */
@Data
public class FeaturedTripVO {

    private Long id;
    private String title;
    private String subtitle;
    private String city;
    private Integer days;
    private String cover;
    private String sourceUrl;
    private Integer sortOrder;
    private Integer status;

    /** 完整行程（解析自 trip_json）。列表接口不返回，避免列表过重 */
    private Object trip;
}
