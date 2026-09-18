package com.gkv.service;

import com.gkv.vo.FeaturedTripVO;
import com.gkv.vo.TripPlanVO;

import java.util.List;

/**
 * 精选行程（用户端）：首页轮播展示的「一整个城市的完整行程」
 */
public interface FeaturedTripService {

    /** 上架中的精选行程概要列表（首页轮播用，不含完整行程） */
    List<FeaturedTripVO> listActive();

    /** 精选行程详情（含完整行程，供详情页渲染） */
    FeaturedTripVO getDetail(Long id);

    /**
     * 把精选行程复制一份到当前登录用户名下（独立副本，之后互不影响）。
     *
     * @param id        精选行程 id
     * @param startDate 用户选定的出发日期 yyyy-MM-dd；为空按今天出发（兼容未传该参数的老版本 App）
     */
    TripPlanVO copyToMine(Long id, String startDate);
}
