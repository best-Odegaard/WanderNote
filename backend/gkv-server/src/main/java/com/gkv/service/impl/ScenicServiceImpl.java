package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gkv.dto.ScenicPageDTO;
import com.gkv.entity.ScenicSpot;
import com.gkv.entity.UserFavorite;
import com.gkv.mapper.ScenicSpotMapper;
import com.gkv.mapper.UserFavoriteMapper;
import com.gkv.result.PageResult;
import com.gkv.service.ScenicService;
import com.gkv.vo.ScenicVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 景点服务实现 —— 列表/详情/热门/搜索/收藏
 */
@Service
@Slf4j
public class ScenicServiceImpl implements ScenicService {

    /** 收藏类型：景点 */
    public static final String FAV_TYPE_SCENIC = "scenic";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ScenicSpotMapper scenicSpotMapper;

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Override
    public PageResult<ScenicVO> pageQuery(ScenicPageDTO dto, Long userId) {
        int pageNo = dto.getPage() == null ? 1 : dto.getPage();
        int pageSize = dto.getPageSize() == null ? 10 : dto.getPageSize();

        LambdaQueryWrapper<ScenicSpot> wrapper = new LambdaQueryWrapper<ScenicSpot>()
                .eq(ScenicSpot::getStatus, 1)
                .eq(StringUtils.hasText(dto.getCity()), ScenicSpot::getCity, dto.getCity())
                .eq(StringUtils.hasText(dto.getCategory()), ScenicSpot::getCategory, dto.getCategory())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(ScenicSpot::getName, dto.getKeyword())
                        .or()
                        .like(ScenicSpot::getDescription, dto.getKeyword()));

        // 排序：rating-按评分降序，price-按价格升序，默认 sort_order 升序
        if ("rating".equals(dto.getSortBy())) {
            wrapper.orderByDesc(ScenicSpot::getRating);
        } else if ("price".equals(dto.getSortBy())) {
            wrapper.orderByAsc(ScenicSpot::getPrice);
        } else {
            wrapper.orderByAsc(ScenicSpot::getSortOrder);
        }

        Page<ScenicSpot> page = scenicSpotMapper.selectPage(Page.of(pageNo, pageSize), wrapper);
        List<ScenicVO> records = page.getRecords().stream()
                .map(s -> convert(s, userId))
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), records);
    }

    @Override
    public ScenicVO getById(Long id, Long userId) {
        ScenicSpot spot = scenicSpotMapper.selectById(id);
        if (spot == null || spot.getStatus() == null || spot.getStatus() != 1) {
            return null;
        }
        return convert(spot, userId);
    }

    @Override
    public List<ScenicVO> hot(Integer limit, Long userId) {
        int size = limit == null ? 10 : Math.min(limit, 50);
        List<ScenicSpot> list = scenicSpotMapper.selectList(
                new LambdaQueryWrapper<ScenicSpot>()
                        .eq(ScenicSpot::getIsHot, 1)
                        .eq(ScenicSpot::getStatus, 1)
                        .orderByAsc(ScenicSpot::getSortOrder)
                        .last("LIMIT " + size)
        );
        return list.stream().map(s -> convert(s, userId)).collect(Collectors.toList());
    }

    @Override
    public List<ScenicVO> search(String keyword, Long userId) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        List<ScenicSpot> list = scenicSpotMapper.selectList(
                new LambdaQueryWrapper<ScenicSpot>()
                        .eq(ScenicSpot::getStatus, 1)
                        .and(w -> w.like(ScenicSpot::getName, keyword)
                                .or()
                                .like(ScenicSpot::getDescription, keyword))
                        .orderByDesc(ScenicSpot::getRating)
                        .last("LIMIT 20")
        );
        return list.stream().map(s -> convert(s, userId)).collect(Collectors.toList());
    }

    @Override
    public void collect(Long id, Long userId) {
        if (userId == null) return;
        Long count = userFavoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getTargetType, FAV_TYPE_SCENIC)
                        .eq(UserFavorite::getTargetId, id)
        );
        if (count > 0) return; // 幂等：已收藏直接返回

        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId);
        fav.setTargetType(FAV_TYPE_SCENIC);
        fav.setTargetId(id);
        userFavoriteMapper.insert(fav);
    }

    @Override
    public void uncollect(Long id, Long userId) {
        if (userId == null) return;
        userFavoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getTargetType, FAV_TYPE_SCENIC)
                        .eq(UserFavorite::getTargetId, id)
        );
    }

    // ── 实体 → VO ──

    private ScenicVO convert(ScenicSpot s, Long userId) {
        ScenicVO vo = ScenicVO.builder()
                .id(s.getId())
                .name(s.getName())
                .cover(s.getCover())
                .city(s.getCity())
                .rating(s.getRating() != null ? s.getRating().doubleValue() : null)
                .category(s.getCategory())
                .price(s.getPrice())
                .openTime(s.getOpenTime())
                .description(s.getDescription())
                .address(s.getAddress())
                .images(parseImages(s.getImages()))
                .build();
        if (userId != null) {
            Long favCount = userFavoriteMapper.selectCount(
                    new LambdaQueryWrapper<UserFavorite>()
                            .eq(UserFavorite::getUserId, userId)
                            .eq(UserFavorite::getTargetType, FAV_TYPE_SCENIC)
                            .eq(UserFavorite::getTargetId, s.getId())
            );
            vo.setIsCollected(favCount > 0);
        } else {
            vo.setIsCollected(false);
        }
        return vo;
    }

    /** images 字段是 JSON 数组字符串，解析为 List；解析失败返回空列表 */
    private List<String> parseImages(String images) {
        if (!StringUtils.hasText(images)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(images, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析景点图片列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
