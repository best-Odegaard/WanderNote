package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.constant.ProfileConstant;
import com.gkv.dto.BaseInfoDTO;
import com.gkv.dto.ProfileSummaryDTO;
import com.gkv.dto.ProfileTagDTO;
import com.gkv.entity.UserProfile;
import com.gkv.mapper.UserProfileMapper;
import com.gkv.service.UserProfileService;
import com.gkv.utils.ProfileExtractor;
import com.gkv.utils.ProfileNoteBuilder;
import com.gkv.utils.ProfileTagUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 用户画像领域服务实现
 *
 * 并发处理：
 *   - 首轮并发插入 → 唯一键（uk_user_profile_user_id）冲突后回查。
 *   - 同用户并发读改写（累加标签、写回摘要）→ 用分段锁把同一 userId 串行化。
 *     分 64 段定长数组而不是 ConcurrentHashMap，避免锁对象随用户数无限增长。
 *     跨进程（多实例部署）仍可能丢一次 +1，权重本身是长期累积统计量，
 *     少数丢失会自行收敛，为它上分布式锁不值得。
 */
@Service
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {

    /** 分段锁段数（2 的幂，便于按位取模） */
    private static final int LOCK_STRIPES = 64;
    private static final Object[] LOCKS = new Object[LOCK_STRIPES];

    static {
        for (int i = 0; i < LOCKS.length; i++) {
            LOCKS[i] = new Object();
        }
    }

    @Resource
    private UserProfileMapper userProfileMapper;

    @Override
    public UserProfile getOrCreate(Long userId) {
        UserProfile exist = findByUserId(userId);
        if (exist != null) {
            return exist;
        }
        UserProfile created = new UserProfile();
        created.setUserId(userId);
        created.setPreferenceTags("[]");
        created.setFreeTags("[]");
        created.setChatRounds(0);
        created.setInjectEnabled(1);
        created.setLastUpdateSource(UserProfile.SOURCE_RULE);
        created.setCreateTime(LocalDateTime.now());
        created.setUpdateTime(LocalDateTime.now());
        try {
            userProfileMapper.insert(created);
            return created;
        } catch (Exception e) {
            // 并发首轮同时插入：唯一键冲突，回查已经插进去的那一行
            UserProfile recheck = findByUserId(userId);
            if (recheck != null) {
                log.debug("[UserProfile] 并发首建画像行，回查到已有行 userId={}", userId);
                return recheck;
            }
            throw e;
        }
    }

    @Override
    public UserProfile findByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .last("limit 1"));
    }

    @Override
    public void accumulate(Long userId, BaseInfoDTO baseInfo, String userInput) {
        if (userId == null) {
            return;
        }
        ProfileExtractor.ExtractResult hit = ProfileExtractor.extract(baseInfo, userInput);
        synchronized (lockFor(userId)) {
            UserProfile profile = getOrCreate(userId);

            // 1. 受控标签：命中各 +1（同一轮内 Set 已去重，不会重复计数）
            Map<String, Integer> weights = new LinkedHashMap<>();
            for (ProfileTagDTO t : ProfileTagUtil.parse(profile.getPreferenceTags())) {
                weights.put(t.getTag(), t.getWeight());
            }
            for (String tag : hit.getTagHits()) {
                weights.merge(tag, 1, Integer::sum);
            }
            List<ProfileTagDTO> mergedTags = new ArrayList<>();
            for (Map.Entry<String, Integer> e : weights.entrySet()) {
                mergedTags.add(new ProfileTagDTO(e.getKey(), e.getValue()));
            }

            UserProfile update = new UserProfile();
            update.setId(profile.getId());
            update.setPreferenceTags(ProfileTagUtil.toJson(mergedTags));
            if (hit.getPreferDays() != null) {
                update.setPreferDays(hit.getPreferDays());
            }
            if (hit.getBudgetLevel() != null) {
                update.setBudgetLevel(hit.getBudgetLevel());
            }
            if (hit.getPace() != null) {
                update.setPace(hit.getPace());
            }
            if (hit.getCompanions() != null) {
                update.setCompanions(hit.getCompanions());
            }
            // 2. 硬性约束只追加不覆盖，按分号去重合并
            if (!hit.getConstraintHits().isEmpty()) {
                update.setConstraintsText(
                        ProfileExtractor.mergeConstraints(profile.getConstraintsText(), hit.getConstraintHits()));
            }
            // 3. 累计对话轮数 +1
            update.setChatRounds((profile.getChatRounds() == null ? 0 : profile.getChatRounds()) + 1);
            update.setUpdateTime(LocalDateTime.now());
            // 4. 刻意不写 last_update_source：它只表示"画像内容最后一次是谁写的"，
            //    累加不改内容语义，留给摘要重写（model）与人工编辑（manual）去标。
            //    注意 MyBatis-Plus 默认 update-strategy=NOT_NULL，未赋值的字段不会进 SQL。
            userProfileMapper.updateById(update);

            log.info("[UserProfile] 规则累加完成 userId={} 本轮命中标签={} 约束={} 轮数={}",
                    userId, hit.getTagHits(), hit.getConstraintHits(), update.getChatRounds());
        }
    }

    @Override
    public String buildInjectNote(Long userId) {
        if (userId == null) {
            return null;
        }
        return ProfileNoteBuilder.build(findByUserId(userId));
    }

    @Override
    public boolean getInjectEnabled(Long userId) {
        if (userId == null) {
            return true;
        }
        UserProfile profile = findByUserId(userId);
        // 没有画像行 = 还没沉淀，按默认开启返回，前端不用区分"没画像"和"开关开着"
        return profile == null || profile.getInjectEnabled() == null || profile.getInjectEnabled() == 1;
    }

    @Override
    public boolean updateInjectEnabled(Long userId, boolean enabled) {
        synchronized (lockFor(userId)) {
            UserProfile profile = getOrCreate(userId);
            UserProfile update = new UserProfile();
            update.setId(profile.getId());
            update.setInjectEnabled(enabled ? 1 : 0);
            update.setUpdateTime(LocalDateTime.now());
            userProfileMapper.updateById(update);
        }
        log.info("[UserProfile] 用户{}设置画像回灌开关为{}", userId, enabled ? "开" : "关");
        return enabled;
    }

    @Override
    public void saveModelSummary(Long userId, ProfileSummaryDTO summary) {
        if (userId == null || summary == null) {
            return;
        }
        synchronized (lockFor(userId)) {
            UserProfile profile = findByUserId(userId);
            if (profile == null) {
                // 摘要重写是旁路任务，没有画像行说明用户还没聊过，直接跳过
                log.info("[UserProfile] 用户{}没有画像行，跳过摘要写回", userId);
                return;
            }
            UserProfile update = new UserProfile();
            update.setId(profile.getId());

            if (StringUtils.hasText(summary.getSummary_text())) {
                update.setSummaryText(summary.getSummary_text().trim());
                update.setSummaryTime(LocalDateTime.now());
            }

            // 自由标签：过滤掉受控标签，避免和规则维护的权重打架
            if (summary.getFree_tags() != null) {
                List<String> free = new ArrayList<>();
                for (String raw : summary.getFree_tags()) {
                    if (!StringUtils.hasText(raw)) {
                        continue;
                    }
                    String v = raw.trim();
                    if (ProfileConstant.isControlledTag(v) || free.contains(v)) {
                        continue;
                    }
                    free.add(v);
                }
                update.setFreeTags(ProfileNoteBuilder.freeTagsToJson(free));
            }

            // 约束：模型返回空串表示"没有"，这时保留已有值 ——
            // 运营人工写的约束不能被模型的"判断不出"冲掉
            if (StringUtils.hasText(summary.getConstraints())) {
                update.setConstraintsText(summary.getConstraints().trim());
            }

            // 三个固定字段：取值必须在词表内才写
            if (ProfileConstant.isValueIn(ProfileConstant.BUDGET_LEVELS, summary.getBudget_level())) {
                update.setBudgetLevel(summary.getBudget_level().trim());
            }
            if (ProfileConstant.isValueIn(ProfileConstant.PACE_LEVELS, summary.getPace())) {
                update.setPace(summary.getPace().trim());
            }
            if (ProfileConstant.isValueIn(ProfileConstant.COMPANION_LEVELS, summary.getCompanions())) {
                update.setCompanions(summary.getCompanions().trim());
            }
            // 偏好天数：>0 才写
            if (summary.getPrefer_days() != null && summary.getPrefer_days() > 0) {
                update.setPreferDays(summary.getPrefer_days());
            }

            // 受控标签权重一律不动：那是规则的职责（命中次数），模型改会把语义毁掉
            update.setLastUpdateSource(UserProfile.SOURCE_MODEL);
            update.setUpdateTime(LocalDateTime.now());
            userProfileMapper.updateById(update);
            log.info("[UserProfile] 用户{}摘要已由模型重写，来源标记为 model", userId);
        }
    }

    @Override
    public Set<Long> findUserIdsByProfile(String profileTag, String profileMark) {
        LambdaQueryWrapper<UserProfile> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(profileTag)) {
            // 带引号的标签名做子串匹配，与 JSON 字段顺序无关；{0} 是参数占位，不做字符串拼接。
            // 列名用数据库里真实存在的 pref_tags（不是 preference_tags）——
            // 这张表在开发库里已经存在，列名以库为准，见 UserProfile 实体的 @TableField 映射。
            wrapper.apply("pref_tags like {0}", ProfileTagUtil.likePattern(profileTag.trim()));
        }
        if (StringUtils.hasText(profileMark)) {
            wrapper.like(UserProfile::getProfileMark, profileMark.trim());
        }
        return userProfileMapper.selectList(wrapper).stream()
                .map(UserProfile::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Map<Long, UserProfile> mapByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserProfile> list = userProfileMapper.selectList(new LambdaQueryWrapper<UserProfile>()
                .in(UserProfile::getUserId, userIds));
        Map<Long, UserProfile> map = new LinkedHashMap<>();
        for (UserProfile profile : list) {
            map.put(profile.getUserId(), profile);
        }
        return map;
    }

    @Override
    public List<String> controlledTags() {
        return ProfileConstant.TAGS;
    }

    /** 按 userId 取分段锁：同一用户的读改写串行化，不同用户互不阻塞 */
    private static Object lockFor(Long userId) {
        int idx = (userId == null ? 0 : userId.hashCode()) & (LOCK_STRIPES - 1);
        return LOCKS[idx];
    }
}
