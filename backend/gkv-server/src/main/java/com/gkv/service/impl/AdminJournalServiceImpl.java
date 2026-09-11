package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.context.BaseContext;
import com.gkv.dto.AdminJournalPageDTO;
import com.gkv.entity.TravelJournal;
import com.gkv.entity.TravelJournalComment;
import com.gkv.entity.User;
import com.gkv.exception.BaseException;
import com.gkv.mapper.TravelJournalCommentMapper;
import com.gkv.mapper.TravelJournalMapper;
import com.gkv.mapper.UserMapper;
import com.gkv.result.PageResult;
import com.gkv.service.AdminJournalService;
import com.gkv.vo.AdminJournalVO;
import com.gkv.vo.CommentVO;
import com.gkv.vo.JournalDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminJournalServiceImpl implements AdminJournalService {

    @Autowired
    private TravelJournalMapper travelJournalMapper;

    @Autowired
    private TravelJournalCommentMapper travelJournalCommentMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<AdminJournalVO> page(AdminJournalPageDTO dto) {
        LambdaQueryWrapper<TravelJournal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, TravelJournal::getStatus, dto.getStatus())
                .like(StringUtils.hasText(dto.getKeyword()), TravelJournal::getTitle, dto.getKeyword());

        Page<TravelJournal> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        page.addOrder(OrderItem.desc("create_time"));

        Page<TravelJournal> result = travelJournalMapper.selectPage(page, wrapper);
        List<AdminJournalVO> records = result.getRecords().stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public JournalDetailVO detail(Long id) {
        TravelJournal journal = travelJournalMapper.selectById(id);
        if (journal == null) {
            throw new BaseException("游记不存在");
        }
        JournalDetailVO vo = new JournalDetailVO();
        BeanUtils.copyProperties(journal, vo);

        User user = userMapper.selectById(journal.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }

        // 评论列表（含已删除的，方便管理端查看/恢复判断）
        List<TravelJournalComment> comments = travelJournalCommentMapper.selectList(
                new LambdaQueryWrapper<TravelJournalComment>()
                        .eq(TravelJournalComment::getJournalId, id)
                        .orderByDesc(TravelJournalComment::getCreateTime));
        vo.setComments(comments.stream().map(this::toCommentVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public void delete(Long id) {
        TravelJournal journal = travelJournalMapper.selectById(id);
        if (journal == null) {
            throw new BaseException("游记不存在");
        }
        updateStatus(id, 0);
        log.info("管理员{}删除/下架游记{}", BaseContext.getCurrentId(), id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        TravelJournal journal = travelJournalMapper.selectById(id);
        if (journal == null) {
            throw new BaseException("游记不存在");
        }
        LambdaUpdateWrapper<TravelJournal> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TravelJournal::getId, id).set(TravelJournal::getStatus, status);
        travelJournalMapper.update(null, updateWrapper);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        TravelJournalComment comment = travelJournalCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BaseException("评论不存在");
        }
        // 评论与回复均标记为已删除
        LambdaUpdateWrapper<TravelJournalComment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TravelJournalComment::getId, commentId).set(TravelJournalComment::getStatus, 2);
        travelJournalCommentMapper.update(null, updateWrapper);

        List<TravelJournalComment> replies = travelJournalCommentMapper.selectList(
                new LambdaQueryWrapper<TravelJournalComment>()
                        .eq(TravelJournalComment::getParentCommentId, commentId)
                        .eq(TravelJournalComment::getStatus, 0));
        if (!replies.isEmpty()) {
            List<Long> replyIds = replies.stream().map(TravelJournalComment::getId).collect(Collectors.toList());
            LambdaUpdateWrapper<TravelJournalComment> replyWrapper = new LambdaUpdateWrapper<>();
            replyWrapper.in(TravelJournalComment::getId, replyIds).set(TravelJournalComment::getStatus, 2);
            travelJournalCommentMapper.update(null, replyWrapper);
            log.info("管理员{}删除评论{}及其{}条回复", BaseContext.getCurrentId(), commentId, replies.size());
        }
    }

    /** 列表项转 VO（含作者信息与评论数） */
    private AdminJournalVO toListVO(TravelJournal journal) {
        AdminJournalVO vo = new AdminJournalVO();
        BeanUtils.copyProperties(journal, vo);

        User user = userMapper.selectById(journal.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }
        Long commentCount = travelJournalCommentMapper.selectCount(
                new LambdaQueryWrapper<TravelJournalComment>()
                        .eq(TravelJournalComment::getJournalId, journal.getId()));
        vo.setCommentCount(commentCount);
        return vo;
    }

    /** 评论转 VO（补充评论人信息） */
    private CommentVO toCommentVO(TravelJournalComment comment) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        vo.setParentCommentIds(comment.getParentCommentId());

        User user = userMapper.selectById(comment.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        if (comment.getParentCommentId() != null) {
            TravelJournalComment parent = travelJournalCommentMapper.selectById(comment.getParentCommentId());
            if (parent != null) {
                User parentUser = userMapper.selectById(parent.getUserId());
                if (parentUser != null) {
                    vo.setParentUsername(parentUser.getUsername());
                }
            }
        }
        return vo;
    }
}
