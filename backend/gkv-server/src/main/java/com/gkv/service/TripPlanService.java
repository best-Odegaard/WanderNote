package com.gkv.service;

import com.gkv.dto.TripImportLinkDTO;
import com.gkv.vo.TripPlanVO;

import java.util.List;

public interface TripPlanService {
    TripPlanVO importFromLink(TripImportLinkDTO dto);

    TripPlanVO getById(Long id);

    /** 我的行程列表（当前登录用户） */
    List<TripPlanVO> listMine();

    /** 保存行程：无 id 则新增，有 id 则更新 */
    TripPlanVO save(TripPlanVO vo);

    /** 更新行程 */
    TripPlanVO update(Long id, TripPlanVO vo);

    /** 删除行程 */
    void delete(Long id);
}
