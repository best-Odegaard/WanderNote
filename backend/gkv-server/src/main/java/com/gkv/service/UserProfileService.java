package com.gkv.service;

import com.gkv.dto.BaseInfoDTO;
import com.gkv.dto.ProfileSummaryDTO;
import com.gkv.entity.UserProfile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 用户画像领域服务（对话沉淀链路的读写入口）
 *
 * 所有方法都遵循一个前提：画像的任何失败都不能让用户对话失败。
 * 调用侧（ChatServiceImpl / 摘要重写任务）仍需自己包一层 try/catch，
 * 这里是第二道防线，不是唯一防线。
 */
public interface UserProfileService {

    /** 取画像行，没有则建（并发首轮插入靠唯一键冲突后回查兜住） */
    UserProfile getOrCreate(Long userId);

    /** 只查不建，用于开关读取、回灌构建、摘要重写（没有画像就跳过） */
    UserProfile findByUserId(Long userId);

    /**
     * 规则累加（每轮对话一次，同步、不调模型）
     *
     * 动作：受控标签命中权重 +1、结构化字段覆盖、文本命中覆盖、
     * 硬性约束追加去重、累计对话轮数 +1。刻意不改 last_update_source。
     */
    void accumulate(Long userId, BaseInfoDTO baseInfo, String userInput);

    /**
     * 构建画像回灌文本（对话与规划共用）
     *
     * @return 开关关闭 / 无画像 / 画像为空 一律 null
     */
    String buildInjectNote(Long userId);

    /** 用户端开关：没有画像行时返回默认值 true（读取失败保持默认开启） */
    boolean getInjectEnabled(Long userId);

    /** 用户端开关：更新（没有画像行会顺带建行） */
    boolean updateInjectEnabled(Long userId, boolean enabled);

    /** 摘要模型写回（校验取值、过滤受控标签、不动受控标签权重、来源标 model） */
    void saveModelSummary(Long userId, ProfileSummaryDTO summary);

    /** 管理端筛选：按画像标签 / 标记查出命中的 user_id 集合 */
    Set<Long> findUserIdsByProfile(String profileTag, String profileMark);

    /** 管理端列表：一次批量补齐当页用户的画像（不要在循环里查库） */
    Map<Long, UserProfile> mapByUserIds(Collection<Long> userIds);

    /** 受控标签词表（管理端筛选下拉用） */
    List<String> controlledTags();
}
