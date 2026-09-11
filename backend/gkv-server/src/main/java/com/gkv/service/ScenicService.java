package com.gkv.service;

import com.gkv.dto.ScenicPageDTO;
import com.gkv.result.PageResult;
import com.gkv.vo.ScenicVO;

import java.util.List;

public interface ScenicService {

    /** 景点分页列表（支持关键词/城市/分类筛选与排序） */
    PageResult<ScenicVO> pageQuery(ScenicPageDTO dto, Long userId);

    /** 景点详情（带当前用户收藏状态） */
    ScenicVO getById(Long id, Long userId);

    /** 热门景点（is_hot=1，按 sort_order 升序） */
    List<ScenicVO> hot(Integer limit, Long userId);

    /** 关键词搜索景点 */
    List<ScenicVO> search(String keyword, Long userId);

    /** 收藏景点（幂等） */
    void collect(Long id, Long userId);

    /** 取消收藏 */
    void uncollect(Long id, Long userId);
}
