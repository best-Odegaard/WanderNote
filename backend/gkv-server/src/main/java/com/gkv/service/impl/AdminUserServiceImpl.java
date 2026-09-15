package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.constant.StatusConstant;
import com.gkv.dto.AdminUserPageDTO;
import com.gkv.dto.ProfileTagDTO;
import com.gkv.entity.User;
import com.gkv.entity.UserProfile;
import com.gkv.exception.BaseException;
import com.gkv.mapper.UserMapper;
import com.gkv.result.PageResult;
import com.gkv.service.AdminUserService;
import com.gkv.service.UserProfileService;
import com.gkv.utils.ProfileTagUtil;
import com.gkv.vo.AdminUserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    /**
     * 导出行数上限。
     * 导出接口不分页，但没有上限的话运营一句"全部导出"就能把整个用户表拉进内存、
     * 再让浏览器去拼一个几十万行的 CSV。上限取一个"够用且不会出事"的量级。
     */
    private static final int EXPORT_LIMIT = 2000;

    /** 列表里展示的画像标签个数（前端只展示前 3 个，这里多给一个避免前端再截） */
    private static final int LIST_TAG_LIMIT = 3;

    @Autowired
    private UserMapper userMapper;

    @Resource
    private UserProfileService userProfileService;

    @Override
    public PageResult<AdminUserVO> page(AdminUserPageDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        Set<Long> profileMatched = applyFilter(wrapper, dto);
        if (profileMatched != null && profileMatched.isEmpty()) {
            // 按画像条件筛出来是空集：直接返回空页，不要退化成"没加这个条件"的全量查询
            return new PageResult<>(0, new ArrayList<>());
        }

        Page<User> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        page.addOrder(OrderItem.desc("create_time"));

        Page<User> result = userMapper.selectPage(page, wrapper);
        List<AdminUserVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        fillProfiles(records);
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public List<AdminUserVO> export(AdminUserPageDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        Set<Long> profileMatched = applyFilter(wrapper, dto);
        if (profileMatched != null && profileMatched.isEmpty()) {
            return new ArrayList<>();
        }
        // last("limit N")：N 是常量，不涉及外部输入拼接
        wrapper.orderByDesc(User::getCreateTime).last("limit " + EXPORT_LIMIT);
        List<AdminUserVO> records = userMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        fillProfiles(records);
        log.info("管理员{}导出用户列表，条件：keyword={}, status={}, profileTag={}, profileMark={}，行数={}",
                com.gkv.context.BaseContext.getCurrentId(), dto.getKeyword(), dto.getStatus(),
                dto.getProfileTag(), dto.getProfileMark(), records.size());
        return records;
    }

    @Override
    public AdminUserVO detail(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        AdminUserVO vo = toVO(user);
        fillProfiles(Collections.singletonList(vo));
        return vo;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        if (status == null || (status != StatusConstant.ENABLE && status != StatusConstant.DISABLE)) {
            throw new BaseException("状态参数错误");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
        log.info("管理员{}修改用户{}状态为{}", com.gkv.context.BaseContext.getCurrentId(), id, status);
    }

    /**
     * 拼装筛选条件。
     *
     * @return 按画像条件命中的 user_id 集合；没有画像筛选条件时返回 null（表示"无需收敛"）。
     *         返回空集合表示"命中 0 个用户"，调用侧必须直接返回空页。
     */
    private Set<Long> applyFilter(LambdaQueryWrapper<User> wrapper, AdminUserPageDTO dto) {
        wrapper.eq(dto.getStatus() != null, User::getStatus, dto.getStatus())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(User::getUsername, dto.getKeyword())
                        .or()
                        .like(User::getNickname, dto.getKeyword())
                        .or()
                        .like(User::getPhone, dto.getKeyword()));

        boolean hasProfileFilter = StringUtils.hasText(dto.getProfileTag())
                || StringUtils.hasText(dto.getProfileMark());
        if (!hasProfileFilter) {
            return null;
        }
        Set<Long> userIds = userProfileService.findUserIdsByProfile(dto.getProfileTag(), dto.getProfileMark());
        if (!userIds.isEmpty()) {
            wrapper.in(User::getId, userIds);
        }
        return userIds;
    }

    /**
     * 批量补齐画像字段。
     * 一次查完当页所有用户的画像，不要在循环里查库（N+1 会随 pageSize 放大）。
     */
    private void fillProfiles(List<AdminUserVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> userIds = records.stream().map(AdminUserVO::getId).collect(Collectors.toList());
        Map<Long, UserProfile> profileMap = userProfileService.mapByUserIds(userIds);
        for (AdminUserVO vo : records) {
            UserProfile profile = profileMap.get(vo.getId());
            if (profile == null) {
                // 没有画像的用户：标签置空集合（不是 null），前端就不用做空值分支
                vo.setProfileTags(new ArrayList<>());
                continue;
            }
            List<ProfileTagDTO> tags = ProfileTagUtil.parse(profile.getPreferenceTags());
            vo.setProfileTags(tags.size() > LIST_TAG_LIMIT ? new ArrayList<>(tags.subList(0, LIST_TAG_LIMIT)) : tags);
            vo.setProfileSummary(profile.getSummaryText());
            vo.setProfileChatRounds(profile.getChatRounds());
            vo.setProfileUpdateTime(profile.getUpdateTime());
            vo.setProfileSource(profile.getLastUpdateSource());
            vo.setProfileRemark(profile.getRemark());
            vo.setProfileMark(profile.getProfileMark());
        }
    }

    /** 实体转 VO（不含密码） */
    private AdminUserVO toVO(User user) {
        AdminUserVO vo = new AdminUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
