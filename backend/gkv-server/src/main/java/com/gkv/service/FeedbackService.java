package com.gkv.service;

import com.gkv.dto.UserFeedbackSubmitDTO;

public interface FeedbackService {

    /** 用户提交意见反馈 */
    void submit(UserFeedbackSubmitDTO dto);
}
