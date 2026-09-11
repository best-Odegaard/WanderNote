package com.gkv.service;

import com.gkv.dto.BannerSaveDTO;
import com.gkv.dto.CitySaveDTO;
import com.gkv.entity.HomeBanner;
import com.gkv.entity.HomeCity;

import java.util.List;

public interface AdminHomeService {

    /** Banner 列表 */
    List<HomeBanner> bannerList();

    /** 新增/编辑 Banner */
    void bannerSave(BannerSaveDTO dto);

    /** 删除 Banner */
    void bannerDelete(Long id);

    /** 启用/禁用 Banner */
    void bannerStatus(Long id, Integer status);

    /** 城市列表 */
    List<HomeCity> cityList();

    /** 新增/编辑城市 */
    void citySave(CitySaveDTO dto);

    /** 删除城市 */
    void cityDelete(Long id);

    /** 启用/禁用城市 */
    void cityStatus(Long id, Integer status);
}
