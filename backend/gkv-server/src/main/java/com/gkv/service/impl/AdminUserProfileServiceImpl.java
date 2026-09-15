package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gkv.constant.ProfileConstant;
import com.gkv.dto.ProfileTagDTO;
import com.gkv.dto.UserProfileEditDTO;
import com.gkv.dto.UserProfileRemarkDTO;
import com.gkv.entity.User;
import com.gkv.entity.UserProfile;
import com.gkv.exception.BaseException;
import com.gkv.mapper.UserMapper;
import com.gkv.mapper.UserProfileMapper;
import com.gkv.service.AdminUserProfileService;
import com.gkv.service.UserProfileService;
import com.gkv.utils.ProfileExtractor;
import com.gkv.utils.ProfileNoteBuilder;
import com.gkv.utils.ProfileTagUtil;
import com.gkv.vo.UserProfileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class AdminUserProfileServiceImpl implements AdminUserProfileService {

    /** 自由标签数量上限（防止运营一次性粘进来一整段文字） */
    private static final int FREE_TAG_LIMIT = 20;

    @Resource
    private UserProfileMapper userProfileMapper;

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private UserMapper userMapper;

    @Override
    public UserProfileVO detail(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        UserProfile profile = userProfileService.findByUserId(userId);
        return toVO(user, profile);
    }

    @Override
    public UserProfileVO edit(Long userId, UserProfileEditDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        UserProfile profile = userProfileService.getOrCreate(userId);

        // 受控标签：非受控/重复的在这里被丢掉，并统一按 权重倒序 + 词表顺序 重排
        List<ProfileTagDTO> tags = ProfileTagUtil.normalize(dto.getPreferenceTags());

        // 自由标签：去空去重、限个数
        List<String> freeTags = new ArrayList<>();
        if (dto.getFreeTags() != null) {
            for (String raw : dto.getFreeTags()) {
                if (!StringUtils.hasText(raw)) {
                    continue;
                }
                String v = raw.trim();
                if (freeTags.contains(v)) {
                    continue;
                }
                freeTags.add(v);
                if (freeTags.size() >= FREE_TAG_LIMIT) {
                    break;
                }
            }
        }

        // 约束：借 mergeConstraints 把混用的中英文分号统一成中文分号
        String constraints = ProfileExtractor.mergeConstraints(dto.getConstraintsText(), null);

        // 三个固定字段：取值必须在词表内，不在词表内视为清空（管理端是下拉框，正常不会触发）
        String budgetLevel = validOrNull(ProfileConstant.BUDGET_LEVELS, dto.getBudgetLevel());
        String pace = validOrNull(ProfileConstant.PACE_LEVELS, dto.getPace());
        String companions = validOrNull(ProfileConstant.COMPANION_LEVELS, dto.getCompanions());

        // 偏好天数：<=0 视为清空
        Integer preferDays = (dto.getPreferDays() != null && dto.getPreferDays() > 0) ? dto.getPreferDays() : null;

        String summaryText = StringUtils.hasText(dto.getSummaryText()) ? dto.getSummaryText().trim() : null;

        // 用 LambdaUpdateWrapper 逐列显式 set 而不是 updateById：
        // updateById 默认 update-strategy=NOT_NULL 会跳过 null 字段，
        // 那样「允许清空」就做不到（清空动作恰恰就是把列写成 NULL）。
        LambdaUpdateWrapper<UserProfile> wrapper = new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getId, profile.getId())
                .set(UserProfile::getPreferenceTags, ProfileTagUtil.toJson(tags))
                .set(UserProfile::getFreeTags, ProfileNoteBuilder.freeTagsToJson(freeTags))
                .set(UserProfile::getConstraintsText, StringUtils.hasText(constraints) ? constraints : null)
                .set(UserProfile::getBudgetLevel, budgetLevel)
                .set(UserProfile::getPace, pace)
                .set(UserProfile::getCompanions, companions)
                .set(UserProfile::getPreferDays, preferDays)
                .set(UserProfile::getSummaryText, summaryText)
                // 摘要被清空时，把"摘要最后生成时间"一起清掉：
                // 留着一个没有摘要的时间戳，管理端会看到一个对不上的字段。
                // 反过来（人工写了摘要）不动这个时间 —— 它记的是"最后一次 AI 生成时间"，
                // 而"人来改过"这件事已经由 last_source=manual 表达了。
                .set(summaryText == null, UserProfile::getSummaryTime, null)
                // 人工编辑把来源标成 manual，让管理端能区分"人来改的还是机器写的"
                .set(UserProfile::getLastUpdateSource, UserProfile.SOURCE_MANUAL)
                .set(UserProfile::getUpdateTime, LocalDateTime.now());
        userProfileMapper.update(null, wrapper);

        log.info("管理员{}人工修正用户{}的画像，来源标记为 manual", com.gkv.context.BaseContext.getCurrentId(), userId);
        return toVO(user, userProfileService.findByUserId(userId));
    }

    @Override
    public UserProfileVO updateRemark(Long userId, UserProfileRemarkDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        UserProfile profile = userProfileService.getOrCreate(userId);

        String remark = StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null;
        String mark = StringUtils.hasText(dto.getProfileMark()) ? dto.getProfileMark().trim() : null;

        LambdaUpdateWrapper<UserProfile> wrapper = new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getId, profile.getId())
                .set(UserProfile::getRemark, remark)
                .set(UserProfile::getProfileMark, mark)
                .set(UserProfile::getUpdateTime, LocalDateTime.now());
        userProfileMapper.update(null, wrapper);

        log.info("管理员{}更新用户{}画像运营备注/标记", com.gkv.context.BaseContext.getCurrentId(), userId);
        return toVO(user, userProfileService.findByUserId(userId));
    }

    @Override
    public List<String> tags() {
        return Collections.unmodifiableList(new ArrayList<>(ProfileConstant.TAGS));
    }

    private String validOrNull(List<String> levels, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String v = value.trim();
        return ProfileConstant.isValueIn(levels, v) ? v : null;
    }

    /** 组装 VO：无画像时字段置空，但用户基础信息一定有（不返回 404） */
    private UserProfileVO toVO(User user, UserProfile profile) {
        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        if (profile == null) {
            vo.setPreferenceTags(new ArrayList<>());
            vo.setFreeTags(new ArrayList<>());
            vo.setChatRounds(0);
            // 没有画像行 = 还没沉淀，开关按默认开启展示
            vo.setInjectEnabled(1);
            vo.setHasProfile(false);
            return vo;
        }
        vo.setPreferenceTags(ProfileTagUtil.parse(profile.getPreferenceTags()));
        vo.setFreeTags(ProfileNoteBuilder.parseFreeTags(profile.getFreeTags()));
        vo.setConstraintsText(profile.getConstraintsText());
        vo.setBudgetLevel(profile.getBudgetLevel());
        vo.setPace(profile.getPace());
        vo.setCompanions(profile.getCompanions());
        vo.setPreferDays(profile.getPreferDays());
        vo.setSummaryText(profile.getSummaryText());
        vo.setSummaryTime(profile.getSummaryTime());
        vo.setChatRounds(profile.getChatRounds() == null ? 0 : profile.getChatRounds());
        vo.setLastUpdateSource(profile.getLastUpdateSource());
        vo.setInjectEnabled(profile.getInjectEnabled() == null ? 1 : profile.getInjectEnabled());
        vo.setRemark(profile.getRemark());
        vo.setProfileMark(profile.getProfileMark());
        vo.setUpdateTime(profile.getUpdateTime());
        vo.setHasProfile(true);
        return vo;
    }
}
