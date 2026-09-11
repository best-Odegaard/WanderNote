package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.context.BaseContext;
import com.gkv.dto.FeedbackHandleDTO;
import com.gkv.dto.UserFeedbackPageDTO;
import com.gkv.entity.User;
import com.gkv.entity.UserFeedback;
import com.gkv.exception.BaseException;
import com.gkv.mapper.UserFeedbackMapper;
import com.gkv.mapper.UserMapper;
import com.gkv.result.PageResult;
import com.gkv.service.AdminFeedbackService;
import com.gkv.vo.UserFeedbackVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminFeedbackServiceImpl implements AdminFeedbackService {

    @Autowired
    private UserFeedbackMapper userFeedbackMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<UserFeedbackVO> page(UserFeedbackPageDTO dto) {
        LambdaQueryWrapper<UserFeedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, UserFeedback::getStatus, dto.getStatus())
                .eq(StringUtils.hasText(dto.getType()), UserFeedback::getType, dto.getType())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(UserFeedback::getContent, dto.getKeyword())
                        .or()
                        .like(UserFeedback::getContact, dto.getKeyword()));

        Page<UserFeedback> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        page.addOrder(OrderItem.desc("create_time"));

        Page<UserFeedback> result = userFeedbackMapper.selectPage(page, wrapper);
        List<UserFeedbackVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public UserFeedbackVO detail(Long id) {
        UserFeedback feedback = userFeedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BaseException("反馈不存在");
        }
        return toVO(feedback);
    }

    @Override
    public void handle(Long id, FeedbackHandleDTO dto) {
        UserFeedback feedback = userFeedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BaseException("反馈不存在");
        }
        if (feedback.getStatus() == 2) {
            throw new BaseException("反馈已关闭，不能处理");
        }
        feedback.setReply(dto.getReply());
        feedback.setStatus(1);
        feedback.setAdminId(BaseContext.getCurrentId());
        feedback.setUpdateTime(LocalDateTime.now());
        userFeedbackMapper.updateById(feedback);
        log.info("管理员{}处理反馈{}：{}", BaseContext.getCurrentId(), id, dto.getReply());
    }

    @Override
    public void close(Long id) {
        UserFeedback feedback = userFeedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BaseException("反馈不存在");
        }
        feedback.setStatus(2);
        feedback.setAdminId(BaseContext.getCurrentId());
        feedback.setUpdateTime(LocalDateTime.now());
        userFeedbackMapper.updateById(feedback);
        log.info("管理员{}关闭反馈{}", BaseContext.getCurrentId(), id);
    }

    @Override
    public void delete(Long id) {
        UserFeedback feedback = userFeedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BaseException("反馈不存在");
        }
        userFeedbackMapper.deleteById(id);
        log.info("管理员{}删除反馈{}", BaseContext.getCurrentId(), id);
    }

    /** 实体转 VO（补充提交用户信息、解析图片） */
    private UserFeedbackVO toVO(UserFeedback feedback) {
        UserFeedbackVO vo = new UserFeedbackVO();
        BeanUtils.copyProperties(feedback, vo);

        User user = userMapper.selectById(feedback.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        vo.setImages(parseImages(feedback.getImages()));
        return vo;
    }

    /** images 字段是 JSON 数组字符串，解析为 List；解析失败返回空列表 */
    private List<String> parseImages(String images) {
        if (!StringUtils.hasText(images)) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(images, String.class);
        } catch (Exception e) {
            log.warn("解析反馈图片列表失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
