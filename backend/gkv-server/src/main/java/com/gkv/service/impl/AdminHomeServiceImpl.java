package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.constant.StatusConstant;
import com.gkv.dto.BannerSaveDTO;
import com.gkv.dto.CitySaveDTO;
import com.gkv.entity.HomeBanner;
import com.gkv.entity.HomeCity;
import com.gkv.exception.BaseException;
import com.gkv.mapper.HomeBannerMapper;
import com.gkv.mapper.HomeCityMapper;
import com.gkv.service.AdminHomeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class AdminHomeServiceImpl implements AdminHomeService {

    @Autowired
    private HomeBannerMapper homeBannerMapper;

    @Autowired
    private HomeCityMapper homeCityMapper;

    // ─────────── Banner ───────────

    @Override
    public List<HomeBanner> bannerList() {
        return homeBannerMapper.selectList(
                new LambdaQueryWrapper<HomeBanner>().orderByAsc(HomeBanner::getSortOrder));
    }

    @Override
    public void bannerSave(BannerSaveDTO dto) {
        if (dto.getId() == null) {
            HomeBanner banner = new HomeBanner();
            BeanUtils.copyProperties(dto, banner);
            if (banner.getStatus() == null) banner.setStatus(StatusConstant.ENABLE);
            if (banner.getSortOrder() == null) banner.setSortOrder(0);
            banner.setCreateTime(LocalDateTime.now());
            banner.setUpdateTime(LocalDateTime.now());
            homeBannerMapper.insert(banner);
            log.info("管理员新增Banner：{}", banner.getTitle());
        } else {
            HomeBanner banner = homeBannerMapper.selectById(dto.getId());
            if (banner == null) {
                throw new BaseException("Banner不存在");
            }
            BeanUtils.copyProperties(dto, banner);
            banner.setUpdateTime(LocalDateTime.now());
            homeBannerMapper.updateById(banner);
            log.info("管理员编辑Banner：{}", banner.getTitle());
        }
    }

    @Override
    public void bannerDelete(Long id) {
        HomeBanner banner = homeBannerMapper.selectById(id);
        if (banner == null) {
            throw new BaseException("Banner不存在");
        }
        homeBannerMapper.deleteById(id);
    }

    @Override
    public void bannerStatus(Long id, Integer status) {
        HomeBanner banner = homeBannerMapper.selectById(id);
        if (banner == null) {
            throw new BaseException("Banner不存在");
        }
        if (status == null || (status != StatusConstant.ENABLE && status != StatusConstant.DISABLE)) {
            throw new BaseException("状态参数错误");
        }
        HomeBanner update = new HomeBanner();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        homeBannerMapper.updateById(update);
    }

    // ─────────── 城市 ───────────

    @Override
    public List<HomeCity> cityList() {
        return homeCityMapper.selectList(
                new LambdaQueryWrapper<HomeCity>().orderByAsc(HomeCity::getSortOrder));
    }

    @Override
    public void citySave(CitySaveDTO dto) {
        if (dto.getId() == null) {
            HomeCity city = new HomeCity();
            BeanUtils.copyProperties(dto, city);
            if (city.getStatus() == null) city.setStatus(StatusConstant.ENABLE);
            if (city.getSortOrder() == null) city.setSortOrder(0);
            city.setCreateTime(LocalDateTime.now());
            city.setUpdateTime(LocalDateTime.now());
            homeCityMapper.insert(city);
            log.info("管理员新增城市：{}", city.getName());
        } else {
            HomeCity city = homeCityMapper.selectById(dto.getId());
            if (city == null) {
                throw new BaseException("城市不存在");
            }
            BeanUtils.copyProperties(dto, city);
            city.setUpdateTime(LocalDateTime.now());
            homeCityMapper.updateById(city);
            log.info("管理员编辑城市：{}", city.getName());
        }
    }

    @Override
    public void cityDelete(Long id) {
        HomeCity city = homeCityMapper.selectById(id);
        if (city == null) {
            throw new BaseException("城市不存在");
        }
        homeCityMapper.deleteById(id);
    }

    @Override
    public void cityStatus(Long id, Integer status) {
        HomeCity city = homeCityMapper.selectById(id);
        if (city == null) {
            throw new BaseException("城市不存在");
        }
        if (status == null || (status != StatusConstant.ENABLE && status != StatusConstant.DISABLE)) {
            throw new BaseException("状态参数错误");
        }
        HomeCity update = new HomeCity();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        homeCityMapper.updateById(update);
    }
}
