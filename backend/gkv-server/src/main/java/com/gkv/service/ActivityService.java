package com.gkv.service;

import com.gkv.dto.ActivityPageDTO;
import com.gkv.result.PageResult;
import com.gkv.vo.ActivityVO;

import java.util.List;

public interface ActivityService {

    /** 活动分页列表（支持城市/分类/日期筛选） */
    PageResult<ActivityVO> pageQuery(ActivityPageDTO dto, Long userId);

    /** 活动详情（带当前用户收藏状态） */
    ActivityVO getById(Long id, Long userId);

    /** 热门活动（is_hot=1，即将开始优先） */
    List<ActivityVO> hot(Integer limit, Long userId);

    /** 收藏活动（幂等） */
    void collect(Long id, Long userId);

    /** 取消收藏 */
    void uncollect(Long id, Long userId);

    /** 活动报名（报名人数 +1） */
    void enroll(Long id, Long userId);
}
