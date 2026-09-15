package com.gkv.service;

import com.gkv.dto.UserProfileEditDTO;
import com.gkv.dto.UserProfileRemarkDTO;
import com.gkv.vo.UserProfileVO;

import java.util.List;

/**
 * 管理端画像服务
 *
 * 语义约定：
 *   - 人工编辑是「整份覆盖，允许清空」，且不做锁定 —— 运营改完即时生效，
 *     用户下次生成行程时会被 AI 重写覆盖。这是需求方明确选择的语义（不做锁定），
 *     所以页面上要写清提示，避免运营误以为改动会一直保留。
 *   - 详情接口在无画像时返回带用户基础信息的空结构，不返回 404。
 */
public interface AdminUserProfileService {

    /** 画像详情（无画像时返回带用户基础信息的空结构） */
    UserProfileVO detail(Long userId);

    /** 人工修正画像（整份覆盖，允许清空；不允许改 user_id 与回灌开关） */
    UserProfileVO edit(Long userId, UserProfileEditDTO dto);

    /** 更新运营备注与标记 */
    UserProfileVO updateRemark(Long userId, UserProfileRemarkDTO dto);

    /** 受控标签词表（筛选下拉用） */
    List<String> tags();
}
