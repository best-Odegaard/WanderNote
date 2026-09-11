package com.gkv.service;

import com.gkv.dto.AdminJournalPageDTO;
import com.gkv.result.PageResult;
import com.gkv.vo.AdminJournalVO;
import com.gkv.vo.JournalDetailVO;

public interface AdminJournalService {

    /** 分页查询游记（含作者信息） */
    PageResult<AdminJournalVO> page(AdminJournalPageDTO dto);

    /** 游记详情（含评论列表） */
    JournalDetailVO detail(Long id);

    /** 删除/下架游记 */
    void delete(Long id);

    /** 修改游记状态 */
    void updateStatus(Long id, Integer status);

    /** 删除评论（连同其回复） */
    void deleteComment(Long commentId);
}
