package com.gkv.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 首页聚合数据 VO
 */
@Data
public class HomeVO implements Serializable {

    /** Banner 轮播 */
    private List<BannerItemVO> banners;

    /** 推荐城市 */
    private List<CityItemVO> cities;

    /** 热门景点 */
    private List<ScenicItemVO> hotAttractions;

    /** 热门活动 */
    private List<ActivityItemVO> hotActivities;

    /** 热门游记 */
    private List<PostItemVO> hotPosts;

    /** 用户最新行程（未登录时为 null） */
    private TripSummaryVO latestTrip;

    // ── 内嵌 VO ──

    @Data
    public static class BannerItemVO implements Serializable {
        private Long id;
        private String title;
        private String subtitle;
        private String emoji;
        private String imageUrl;
        private String linkUrl;
    }

    @Data
    public static class CityItemVO implements Serializable {
        private String name;
        private String cover;
        private Double rating;
    }

    @Data
    public static class ScenicItemVO implements Serializable {
        private Long id;
        private String name;
        private String cover;
        private String city;
        private Double rating;
    }

    @Data
    public static class ActivityItemVO implements Serializable {
        private Long id;
        private String title;
        private String cover;
        private String city;
        private String startTime;
    }

    @Data
    public static class PostItemVO implements Serializable {
        private Long id;
        private String title;
        private String cover;
        private String authorName;
        private String authorAvatar;
        private Integer likeCount;
    }

    @Data
    public static class TripSummaryVO implements Serializable {
        private Long id;
        private String title;
        private String toCity;
        private Integer days;
        private String createdAt;
    }
}
