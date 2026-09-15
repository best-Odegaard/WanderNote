package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.dto.FeaturedParseDTO;
import com.gkv.dto.FeaturedTripSaveDTO;
import com.gkv.entity.FeaturedTrip;
import com.gkv.exception.BaseException;
import com.gkv.mapper.FeaturedTripMapper;
import com.gkv.service.AdminFeaturedTripService;
import com.gkv.service.TripPlanService;
import com.gkv.vo.TripPlanVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 精选行程（管理端）实现
 */
@Service
@Slf4j
public class AdminFeaturedTripServiceImpl implements AdminFeaturedTripService {

    @Autowired
    private FeaturedTripMapper featuredTripMapper;

    @Autowired
    private TripPlanService tripPlanService;

    @Override
    public List<FeaturedTrip> list() {
        return featuredTripMapper.selectList(new LambdaQueryWrapper<FeaturedTrip>()
                .orderByAsc(FeaturedTrip::getSortOrder)
                .orderByDesc(FeaturedTrip::getId));
    }

    @Override
    public void save(FeaturedTripSaveDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BaseException("标题不能为空");
        }
        if (dto.getTripJson() == null || dto.getTripJson().trim().isEmpty()) {
            throw new BaseException("行程内容不能为空，可先用「解析链接」生成");
        }
        // 校验 JSON 合法性，避免把坏数据存进库
        try {
            JSON.parse(dto.getTripJson());
        } catch (Exception e) {
            throw new BaseException("行程内容不是合法 JSON，请检查格式");
        }

        FeaturedTrip entity = new FeaturedTrip();
        entity.setId(dto.getId());
        entity.setTitle(dto.getTitle().trim());
        entity.setSubtitle(dto.getSubtitle());
        entity.setCity(dto.getCity());
        entity.setDays(dto.getDays());
        entity.setCover(dto.getCover());
        entity.setTripJson(dto.getTripJson());
        entity.setSourceUrl(dto.getSourceUrl());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setUpdateTime(LocalDateTime.now());

        if (entity.getId() == null) {
            entity.setCreateTime(LocalDateTime.now());
            featuredTripMapper.insert(entity);
            log.info("新增精选行程：{}", entity.getTitle());
        } else {
            if (featuredTripMapper.selectById(entity.getId()) == null) {
                throw new BaseException("精选行程不存在");
            }
            featuredTripMapper.updateById(entity);
            log.info("编辑精选行程：id={}，title={}", entity.getId(), entity.getTitle());
        }
    }

    @Override
    public void delete(Long id) {
        featuredTripMapper.deleteById(id);
        log.info("删除精选行程：id={}", id);
    }

    @Override
    public void status(Long id, Integer status) {
        if (featuredTripMapper.selectById(id) == null) {
            throw new BaseException("精选行程不存在");
        }
        FeaturedTrip upd = new FeaturedTrip();
        upd.setId(id);
        upd.setStatus(status == null ? 0 : status);
        upd.setUpdateTime(LocalDateTime.now());
        featuredTripMapper.updateById(upd);
        log.info("修改精选行程状态：id={}，status={}", id, status);
    }

    @Override
    public FeaturedTripSaveDTO parseLink(FeaturedParseDTO dto) {
        if (dto == null) {
            throw new BaseException("参数不能为空");
        }
        // 复用行程侧的解析逻辑：用户粘的原文优先，没粘就自动抓链接正文，都没有才只按城市生成
        TripPlanVO trip = tripPlanService.parseLink(dto.getSourceUrl(), dto.getCity(), dto.getContent());

        // 抓到的正文单独回传，不塞进行程 JSON（那是行程数据，不该混进几 KB 网页正文）
        String sourceText = trip.getSourceText();
        trip.setSourceText(null);

        FeaturedTripSaveDTO draft = new FeaturedTripSaveDTO();
        draft.setTitle(trip.getTitle());
        draft.setCity(trip.getToCity() != null ? trip.getToCity() : dto.getCity());
        draft.setDays(trip.getDays());
        draft.setSourceUrl(dto.getSourceUrl());
        draft.setSourceText(sourceText);
        draft.setTripJson(JSON.toJSONString(trip));
        draft.setSortOrder(0);
        draft.setStatus(1);
        log.info("解析生成精选行程草稿：city={}，title={}，抓取正文字数={}",
                draft.getCity(), draft.getTitle(), sourceText == null ? 0 : sourceText.length());
        return draft;
    }
}
