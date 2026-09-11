package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.entity.*;
import com.gkv.mapper.*;
import com.gkv.service.HomeService;
import com.gkv.vo.HomeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 首页服务实现 —— 聚合各模块热门数据
 * 各模块查询失败时返回空列表，不影响其他模块
 */
@Service
@Slf4j
public class HomeServiceImpl implements HomeService {

    @Autowired
    private HomeBannerMapper homeBannerMapper;

    @Autowired
    private HomeCityMapper homeCityMapper;

    @Autowired
    private ScenicSpotMapper scenicSpotMapper;

    @Autowired
    private ActivityInfoMapper activityInfoMapper;

    @Autowired
    private TravelJournalMapper travelJournalMapper;

    @Autowired
    private TripPlanMapper tripPlanMapper;

    @Override
    public HomeVO getHomeIndex(Long userId) {
        HomeVO vo = new HomeVO();
        vo.setBanners(buildBanners());
        vo.setCities(buildCities());
        vo.setHotAttractions(buildHotAttractions());
        vo.setHotActivities(buildHotActivities());
        vo.setHotPosts(buildHotPosts());
        vo.setLatestTrip(buildLatestTrip(userId));
        return vo;
    }

    // ── 各模块数据构建 ──

    /** Banner：按 sort_order 升序，取启用的 */
    private List<HomeVO.BannerItemVO> buildBanners() {
        try {
            List<HomeBanner> list = homeBannerMapper.selectList(
                    new LambdaQueryWrapper<HomeBanner>()
                            .eq(HomeBanner::getStatus, 1)
                            .orderByAsc(HomeBanner::getSortOrder)
            );
            return list.stream().map(b -> {
                HomeVO.BannerItemVO vo = new HomeVO.BannerItemVO();
                vo.setId(b.getId());
                vo.setTitle(b.getTitle());
                vo.setSubtitle(b.getSubtitle());
                vo.setEmoji(b.getEmoji());
                vo.setImageUrl(b.getImageUrl());
                vo.setLinkUrl(b.getLinkUrl());
                return vo;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询Banner失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 推荐城市：按 sort_order 升序，取前 8 个 */
    private List<HomeVO.CityItemVO> buildCities() {
        try {
            List<HomeCity> list = homeCityMapper.selectList(
                    new LambdaQueryWrapper<HomeCity>()
                            .eq(HomeCity::getStatus, 1)
                            .orderByAsc(HomeCity::getSortOrder)
                            .last("LIMIT 8")
            );
            return list.stream().map(c -> {
                HomeVO.CityItemVO vo = new HomeVO.CityItemVO();
                vo.setName(c.getName());
                vo.setCover(c.getCover());
                vo.setRating(c.getRating() != null ? c.getRating().doubleValue() : 5.0);
                return vo;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询推荐城市失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 热门景点：is_hot=1，按 sort_order 升序，取前 6 个 */
    private List<HomeVO.ScenicItemVO> buildHotAttractions() {
        try {
            List<ScenicSpot> list = scenicSpotMapper.selectList(
                    new LambdaQueryWrapper<ScenicSpot>()
                            .eq(ScenicSpot::getIsHot, 1)
                            .eq(ScenicSpot::getStatus, 1)
                            .orderByAsc(ScenicSpot::getSortOrder)
                            .last("LIMIT 6")
            );
            return list.stream().map(s -> {
                HomeVO.ScenicItemVO vo = new HomeVO.ScenicItemVO();
                vo.setId(s.getId());
                vo.setName(s.getName());
                vo.setCover(s.getCover());
                vo.setCity(s.getCity());
                vo.setRating(s.getRating() != null ? s.getRating().doubleValue() : 5.0);
                return vo;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询热门景点失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 热门活动：is_hot=1，按 start_time 升序（即将开始优先），取前 3 个 */
    private List<HomeVO.ActivityItemVO> buildHotActivities() {
        try {
            List<ActivityInfo> list = activityInfoMapper.selectList(
                    new LambdaQueryWrapper<ActivityInfo>()
                            .eq(ActivityInfo::getIsHot, 1)
                            .eq(ActivityInfo::getStatus, 1)
                            .orderByAsc(ActivityInfo::getStartTime)
                            .last("LIMIT 3")
            );
            return list.stream().map(a -> {
                HomeVO.ActivityItemVO vo = new HomeVO.ActivityItemVO();
                vo.setId(a.getId());
                vo.setTitle(a.getTitle());
                vo.setCover(a.getCover());
                vo.setCity(a.getCity());
                vo.setStartTime(a.getStartTime() != null ? a.getStartTime().toString() : null);
                return vo;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询热门活动失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 热门游记：按 like_count 倒序，取前 3 个 */
    private List<HomeVO.PostItemVO> buildHotPosts() {
        try {
            List<TravelJournal> list = travelJournalMapper.selectList(
                    new LambdaQueryWrapper<TravelJournal>()
                            .eq(TravelJournal::getStatus, 1)
                            .orderByDesc(TravelJournal::getLikeCount)
                            .last("LIMIT 3")
            );
            return list.stream().map(j -> {
                HomeVO.PostItemVO vo = new HomeVO.PostItemVO();
                vo.setId(j.getId());
                vo.setTitle(j.getTitle());
                String images = j.getImages();
                if (images != null && !images.isEmpty()) {
                    try {
                        String[] arr = images.replace("[", "").replace("]", "").replace("\"", "").split(",");
                        vo.setCover(arr.length > 0 ? arr[0].trim() : "");
                    } catch (Exception e) {
                        vo.setCover("");
                    }
                }
                vo.setLikeCount(j.getLikeCount() != null ? j.getLikeCount() : 0);
                return vo;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询热门游记失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 用户最新行程：按 create_time 倒序取第一条 */
    private HomeVO.TripSummaryVO buildLatestTrip(Long userId) {
        if (userId == null) return null;
        try {
            List<TripPlan> list = tripPlanMapper.selectList(
                    new LambdaQueryWrapper<TripPlan>()
                            .eq(TripPlan::getUserId, userId)
                            .orderByDesc(TripPlan::getCreateTime)
                            .last("LIMIT 1")
            );
            if (list.isEmpty()) return null;
            TripPlan t = list.get(0);
            HomeVO.TripSummaryVO vo = new HomeVO.TripSummaryVO();
            vo.setId(t.getId());
            vo.setTitle(t.getTitle());
            vo.setToCity(t.getToCity());
            vo.setDays(t.getDays());
            vo.setCreatedAt(t.getCreateTime() != null ? t.getCreateTime().toString() : null);
            return vo;
        } catch (Exception e) {
            log.warn("查询用户最新行程失败: {}", e.getMessage());
            return null;
        }
    }
}
