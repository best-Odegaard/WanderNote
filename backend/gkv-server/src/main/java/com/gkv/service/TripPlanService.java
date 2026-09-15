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

    /**
     * 只解析外部内容生成行程数据，不落库（管理端「精选行程」用）。
     *
     * @param sourceUrl 外部链接（可空，仅用于溯源）
     * @param city      目的地城市；为空时尝试从链接里识别（多数分享链接识别不到）
     * @param content   行程原文（可空）；填了就让 AI 按原文产出结构化行程，而不是凭空生成
     */
    TripPlanVO parseLink(String sourceUrl, String city, String content);
}
