package com.gkv.service;

import com.gkv.dto.CommentDTO;
import com.gkv.dto.TravelJournalPageDTO;
import com.gkv.dto.TravelJournalPublishDTO;
import com.gkv.result.PageResult;
import com.gkv.vo.CommentVO;
import com.gkv.vo.TravelJournalVO;

import java.util.List;

public interface TravelJournalService  {
    void publish(TravelJournalPublishDTO dto);
    PageResult<TravelJournalVO> pageQuery(TravelJournalPageDTO dto);
    TravelJournalVO getByID(Long id);

    void likeJournal(Long journalId);

    void addComment(CommentDTO dto);

    void deleteComment(Long commentId);

    List<CommentVO> getCommentList(Long journalId);

    void deleteJournal(Long id);

    /** 收藏游记 */
    void collect(Long journalId);

    /** 取消收藏游记 */
    void uncollect(Long journalId);

    /** 我的收藏游记列表 */
    List<TravelJournalVO> myCollects();

    /** 我的游记列表（本人发布） */
    List<TravelJournalVO> myJournals();

    /** 热门游记（按点赞数倒序） */
    List<TravelJournalVO> hot(Integer limit);
}
