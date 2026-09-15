package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.context.BaseContext;
import com.gkv.entity.FeaturedTrip;
import com.gkv.exception.BaseException;
import com.gkv.mapper.FeaturedTripMapper;
import com.gkv.service.FeaturedTripService;
import com.gkv.service.TripPlanService;
import com.gkv.vo.FeaturedTripVO;
import com.gkv.vo.TripPlanVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 精选行程（用户端）实现
 */
@Service
@Slf4j
public class FeaturedTripServiceImpl implements FeaturedTripService {

    /** 上架状态 */
    private static final int STATUS_ON = 1;

    @Autowired
    private FeaturedTripMapper featuredTripMapper;

    @Autowired
    private TripPlanService tripPlanService;

    @Override
    public List<FeaturedTripVO> listActive() {
        LambdaQueryWrapper<FeaturedTrip> wrapper = new LambdaQueryWrapper<FeaturedTrip>()
                .eq(FeaturedTrip::getStatus, STATUS_ON)
                .orderByAsc(FeaturedTrip::getSortOrder)
                .orderByDesc(FeaturedTrip::getId);
        List<FeaturedTrip> list = featuredTripMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<FeaturedTripVO> result = new ArrayList<>(list.size());
        for (FeaturedTrip item : list) {
            result.add(toVO(item, false));
        }
        return result;
    }

    @Override
    public FeaturedTripVO getDetail(Long id) {
        FeaturedTrip item = featuredTripMapper.selectById(id);
        if (item == null || !Integer.valueOf(STATUS_ON).equals(item.getStatus())) {
            throw new BaseException("该精选行程不存在或已下架");
        }
        return toVO(item, true);
    }

    @Override
    public TripPlanVO copyToMine(Long id) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("请先登录后添加行程");
        }
        FeaturedTrip item = featuredTripMapper.selectById(id);
        if (item == null || !Integer.valueOf(STATUS_ON).equals(item.getStatus())) {
            throw new BaseException("该精选行程不存在或已下架");
        }

        // 把内嵌的行程 JSON 还原成一份 TripPlanVO，再走现有 save() 落库：
        // 归属、默认值、时间戳等都由 save() 统一处理，这里不重复实现插入逻辑。
        TripPlanVO vo;
        try {
            vo = JSON.parseObject(item.getTripJson(), TripPlanVO.class);
        } catch (Exception e) {
            log.warn("精选行程内容解析失败：id={}，err={}", id, e.getMessage());
            throw new BaseException("精选行程内容异常，无法添加");
        }
        if (vo == null) {
            throw new BaseException("精选行程内容异常，无法添加");
        }
        // 关键：清掉来源标识，副本与原行程彻底解绑（不继承 id / AI 会话）
        vo.setId(null);
        vo.setChatSessionId(null);
        vo.setUserId(null);
        if (vo.getTitle() == null || vo.getTitle().isEmpty()) {
            vo.setTitle(item.getTitle());
        }
        if (vo.getCover() == null || vo.getCover().isEmpty()) {
            vo.setCover(item.getCover());
        }

        TripPlanVO saved = tripPlanService.save(vo);
        log.info("用户{}把精选行程{}添加到我的行程，新行程 id={}", userId, id, saved.getId());
        return saved;
    }

    /** 实体 → VO；withTrip 为 true 时解析并返回完整行程 */
    private FeaturedTripVO toVO(FeaturedTrip item, boolean withTrip) {
        FeaturedTripVO vo = new FeaturedTripVO();
        vo.setId(item.getId());
        vo.setTitle(item.getTitle());
        vo.setSubtitle(item.getSubtitle());
        vo.setCity(item.getCity());
        vo.setDays(item.getDays());
        vo.setCover(item.getCover());
        vo.setSourceUrl(item.getSourceUrl());
        vo.setSortOrder(item.getSortOrder());
        vo.setStatus(item.getStatus());
        if (withTrip && item.getTripJson() != null && !item.getTripJson().isEmpty()) {
            try {
                vo.setTrip(JSON.parse(item.getTripJson()));
            } catch (Exception e) {
                log.warn("精选行程 tripJson 解析失败：id={}，err={}", item.getId(), e.getMessage());
            }
        }
        return vo;
    }
}
