package com.gkv.service;

import com.gkv.dto.FeaturedParseDTO;
import com.gkv.dto.FeaturedTripSaveDTO;
import com.gkv.entity.FeaturedTrip;

import java.util.List;

/**
 * 精选行程（管理端）
 */
public interface AdminFeaturedTripService {

    /** 全部精选行程（含下架） */
    List<FeaturedTrip> list();

    /** 新增/编辑（有 id 为编辑） */
    void save(FeaturedTripSaveDTO dto);

    void delete(Long id);

    /** 上架/下架（status：0下架 1上架） */
    void status(Long id, Integer status);

    /**
     * 解析外部内容生成行程草稿（不落库）。
     * @param dto 含 sourceUrl / city / content
     */
    FeaturedTripSaveDTO parseLink(FeaturedParseDTO dto);
}
