package com.gkv.service;

import com.gkv.dto.FeedbackHandleDTO;
import com.gkv.dto.UserFeedbackPageDTO;
import com.gkv.result.PageResult;
import com.gkv.vo.UserFeedbackVO;

public interface AdminFeedbackService {

    /** 分页查询意见反馈 */
    PageResult<UserFeedbackVO> page(UserFeedbackPageDTO dto);

    /** 反馈详情 */
    UserFeedbackVO detail(Long id);

    /** 回复并标记已处理 */
    void handle(Long id, FeedbackHandleDTO dto);

    /** 关闭反馈 */
    void close(Long id);

    /** 删除反馈 */
    void delete(Long id);
}
