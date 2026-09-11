package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.dto.ActivityPageDTO;
import com.gkv.entity.ActivityInfo;
import com.gkv.entity.UserFavorite;
import com.gkv.mapper.ActivityInfoMapper;
import com.gkv.mapper.UserFavoriteMapper;
import com.gkv.result.PageResult;
import com.gkv.service.ActivityService;
import com.gkv.vo.ActivityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 活动服务实现 —— 列表/详情/热门/收藏/报名
 */
@Service
@Slf4j
public class ActivityServiceImpl implements ActivityService {

    /** 收藏类型：活动 */
    public static final String FAV_TYPE_ACTIVITY = "activity";

    @Autowired
    private ActivityInfoMapper activityInfoMapper;

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Override
    public PageResult<ActivityVO> pageQuery(ActivityPageDTO dto, Long userId) {
        int pageNo = dto.getPage() == null ? 1 : dto.getPage();
        int pageSize = dto.getPageSize() == null ? 10 : dto.getPageSize();

        LambdaQueryWrapper<ActivityInfo> wrapper = new LambdaQueryWrapper<ActivityInfo>()
                .eq(ActivityInfo::getStatus, 1)
                .eq(StringUtils.hasText(dto.getCity()), ActivityInfo::getCity, dto.getCity())
                .eq(StringUtils.hasText(dto.getCategory()), ActivityInfo::getCategory, dto.getCategory());

        // 日期筛选：活动时间与查询区间有交集
        if (StringUtils.hasText(dto.getStartDate())) {
            LocalDateTime start = LocalDate.parse(dto.getStartDate()).atStartOfDay();
            wrapper.ge(ActivityInfo::getEndTime, start);
        }
        if (StringUtils.hasText(dto.getEndDate())) {
            LocalDateTime end = LocalDate.parse(dto.getEndDate()).plusDays(1).atStartOfDay();
            wrapper.le(ActivityInfo::getStartTime, end);
        }

        // 默认：进行中/未开始的在前（start_time 升序）
        wrapper.orderByAsc(ActivityInfo::getStartTime).orderByAsc(ActivityInfo::getSortOrder);

        Page<ActivityInfo> page = activityInfoMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<ActivityVO> records = page.getRecords().stream()
                .map(a -> convert(a, userId))
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), records);
    }

    @Override
    public ActivityVO getById(Long id, Long userId) {
        ActivityInfo activity = activityInfoMapper.selectById(id);
        if (activity == null || activity.getStatus() == null || activity.getStatus() != 1) {
            return null;
        }
        return convert(activity, userId);
    }

    @Override
    public List<ActivityVO> hot(Integer limit, Long userId) {
        int size = limit == null ? 10 : Math.min(limit, 50);
        List<ActivityInfo> list = activityInfoMapper.selectList(
                new LambdaQueryWrapper<ActivityInfo>()
                        .eq(ActivityInfo::getIsHot, 1)
                        .eq(ActivityInfo::getStatus, 1)
                        .orderByAsc(ActivityInfo::getStartTime)
                        .orderByAsc(ActivityInfo::getSortOrder)
                        .last("LIMIT " + size)
        );
        return list.stream().map(a -> convert(a, userId)).collect(Collectors.toList());
    }

    @Override
    public void collect(Long id, Long userId) {
        if (userId == null) return;
        Long count = userFavoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getTargetType, FAV_TYPE_ACTIVITY)
                        .eq(UserFavorite::getTargetId, id)
        );
        if (count > 0) return; // 幂等：已收藏直接返回

        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId);
        fav.setTargetType(FAV_TYPE_ACTIVITY);
        fav.setTargetId(id);
        userFavoriteMapper.insert(fav);
    }

    @Override
    public void uncollect(Long id, Long userId) {
        if (userId == null) return;
        userFavoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getTargetType, FAV_TYPE_ACTIVITY)
                        .eq(UserFavorite::getTargetId, id)
        );
    }

    @Override
    public void enroll(Long id, Long userId) {
        if (userId == null) return;
        ActivityInfo activity = activityInfoMapper.selectById(id);
        if (activity == null) {
            throw new RuntimeException("活动不存在");
        }
        // 报名人数 +1（无报名记录表，仅计数）
        ActivityInfo update = new ActivityInfo();
        update.setId(id);
        update.setEnrollCount((activity.getEnrollCount() == null ? 0 : activity.getEnrollCount()) + 1);
        activityInfoMapper.updateById(update);
    }

    // ── 实体 → VO ──

    private ActivityVO convert(ActivityInfo a, Long userId) {
        ActivityVO vo = ActivityVO.builder()
                .id(a.getId())
                .title(a.getTitle())
                .cover(a.getCover())
                .city(a.getCity())
                .location(a.getLocation())
                .category(a.getCategory())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .description(a.getDescription())
                .enrollCount(a.getEnrollCount())
                .build();
        if (userId != null) {
            Long favCount = userFavoriteMapper.selectCount(
                    new LambdaQueryWrapper<UserFavorite>()
                            .eq(UserFavorite::getUserId, userId)
                            .eq(UserFavorite::getTargetType, FAV_TYPE_ACTIVITY)
                            .eq(UserFavorite::getTargetId, a.getId())
            );
            vo.setIsCollected(favCount > 0);
        } else {
            vo.setIsCollected(false);
        }
        return vo;
    }
}
