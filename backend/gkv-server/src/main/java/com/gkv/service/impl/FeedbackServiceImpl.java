package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.gkv.context.BaseContext;
import com.gkv.dto.UserFeedbackSubmitDTO;
import com.gkv.entity.UserFeedback;
import com.gkv.exception.BaseException;
import com.gkv.mapper.UserFeedbackMapper;
import com.gkv.service.FeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
public class FeedbackServiceImpl implements FeedbackService {

    @Autowired
    private UserFeedbackMapper userFeedbackMapper;

    @Override
    public void submit(UserFeedbackSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("请先登录");
        }
        if (!StringUtils.hasText(dto.getContent()) || dto.getContent().trim().length() > 2000) {
            throw new BaseException("反馈内容不能为空且不超过2000字");
        }

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setType(StringUtils.hasText(dto.getType()) ? dto.getType() : "其他");
        feedback.setContent(dto.getContent().trim());
        feedback.setImages(dto.getImages() != null && !dto.getImages().isEmpty()
                ? JSON.toJSONString(dto.getImages()) : null);
        feedback.setContact(StringUtils.hasText(dto.getContact()) ? dto.getContact().trim() : null);
        feedback.setStatus(0);
        feedback.setCreateTime(LocalDateTime.now());
        feedback.setUpdateTime(LocalDateTime.now());

        userFeedbackMapper.insert(feedback);
        log.info("用户{}提交意见反馈：id={}, type={}", userId, feedback.getId(), feedback.getType());
    }
}
