package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gkv.context.BaseContext;
import com.gkv.dto.CommentDTO;
import com.gkv.dto.TravelJournalPageDTO;
import com.gkv.dto.TravelJournalPublishDTO;
import com.gkv.entity.TravelJournal;
import com.gkv.entity.TravelJournalCollect;
import com.gkv.entity.TravelJournalComment;
import com.gkv.entity.TravelJournalLike;
import com.gkv.entity.User;
import com.gkv.exception.BaseException;
import com.gkv.mapper.TravelJournalCollectMapper;
import com.gkv.mapper.TravelJournalCommentMapper;
import com.gkv.mapper.TravelJournalLikeMapper;
import com.gkv.mapper.TravelJournalMapper;
import com.gkv.mapper.UserMapper;
import com.gkv.result.PageResult;
import com.gkv.service.TravelJournalService;
import com.gkv.vo.CommentVO;
import com.gkv.vo.TravelJournalVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TravelJournalServiceImpl implements TravelJournalService {
    @Autowired
    private TravelJournalMapper travelJournalMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TravelJournalLikeMapper travelJournalLikeMapper;

    @Autowired
    private TravelJournalCommentMapper travelJournalCommentMapper;

    @Autowired
    private TravelJournalCollectMapper travelJournalCollectMapper;

    @Override
    public void publish(TravelJournalPublishDTO dto) {
        //获取当前登录用户id
        long userId = BaseContext.getCurrentId();

        TravelJournal journal = new TravelJournal();
        journal.setUserId(userId);
        journal.setTitle(dto.getTitle());
        journal.setContent(dto.getContent());
        journal.setImages(JSON.toJSONString(dto.getImageUrls()));
        journal.setLocation(dto.getLocation());
        journal.setTags(dto.getTags() != null ? String.join(",", dto.getTags()) : null);//选填
        journal.setStatus(1);
        journal.setLikeCount(0);
        journal.setCollectCount(0);
        journal.setCreateTime(LocalDateTime.now());
        journal.setUpdateTime(LocalDateTime.now());

        travelJournalMapper.insert(journal);
        log.info("用户{}发布游记成功：游记id：{}", userId, journal.getId());
    }

    @Override
    public PageResult<TravelJournalVO> pageQuery(TravelJournalPageDTO dto) {
        // 构建查询条件
        LambdaQueryWrapper<TravelJournal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelJournal::getStatus, 1); // 只查已发布的

        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.like(TravelJournal::getTitle, dto.getKeyword());
        }
        if (StringUtils.hasText(dto.getTag())) {
            wrapper.like(TravelJournal::getTags, dto.getTag());
        }

        // 按创建时间倒序
        Page<TravelJournal> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        page.addOrder(OrderItem.desc("create_time"));

        Page<TravelJournal> result = travelJournalMapper.selectPage(page, wrapper);

        // 转换为 VO
        List<TravelJournalVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), voList);
    }

    @Override
    public TravelJournalVO getByID(Long id) {
        TravelJournal journal = travelJournalMapper.selectById(id);
        if (journal == null) {
            return null;
        }
        return toVO(journal);
    }

    @Override
    @Transactional
    public void likeJournal(Long journalId) {
        Long userId = BaseContext.getCurrentId();
        log.info("开始处理点赞逻辑，用户id：{}，游记id：{}", userId, journalId);

        LambdaQueryWrapper<TravelJournalLike> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TravelJournalLike::getJournalId, journalId)
                .eq(TravelJournalLike::getUserId, userId);
        TravelJournalLike existLike = travelJournalLikeMapper.selectOne(queryWrapper);

        if (existLike != null) {
            log.info("已点赞，执行取消点赞操作");
            travelJournalLikeMapper.deleteById(existLike.getId());
            LambdaUpdateWrapper<TravelJournal> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(TravelJournal::getId, journalId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)");
            travelJournalMapper.update(null, updateWrapper);
        } else {
            log.info("未点赞，执行点赞操作");
            TravelJournalLike newLike = new TravelJournalLike();
            newLike.setJournalId(journalId);
            newLike.setUserId(userId);
            newLike.setCreateTime(LocalDateTime.now());
            travelJournalLikeMapper.insert(newLike);

            LambdaUpdateWrapper<TravelJournal> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(TravelJournal::getId, journalId)
                    .setSql("like_count = like_count + 1");
            travelJournalMapper.update(null, updateWrapper);
        }
        log.info("点赞操作完成");
    }

    @Override
    @Transactional
    public void addComment(CommentDTO dto) {
        Long userId = BaseContext.getCurrentId();
        TravelJournal journal = travelJournalMapper.selectById(dto.getJournalId());
        if (journal == null) {
            throw new RuntimeException("游记不存在");
        }
        if (dto.getParentCommentId() != null) {
            TravelJournalComment parentComment = travelJournalCommentMapper.selectById(dto.getParentCommentId());
            if (parentComment == null) {
                throw new RuntimeException("父评论不存在");
            }
            if (!parentComment.getJournalId().equals(dto.getJournalId())) {
                throw new RuntimeException("父评论不属于该游记");
            }
        }
        TravelJournalComment comment = new TravelJournalComment();
        comment.setJournalId(dto.getJournalId());
        comment.setUserId(userId);
        comment.setParentCommentId(dto.getParentCommentId());
        comment.setContent(dto.getContent());
        comment.setStatus(0);
        comment.setCreateTime(LocalDateTime.now());

        travelJournalCommentMapper.insert(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        Long userId = BaseContext.getCurrentId();
        TravelJournalComment comment = travelJournalCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new RuntimeException("只能删除自己的评论");
        }
        if (comment.getStatus() == 2) {
            throw new RuntimeException("评论已删除");
        }
        LambdaUpdateWrapper<TravelJournalComment> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TravelJournalComment::getId, commentId).set(TravelJournalComment::getStatus, 2);
        travelJournalCommentMapper.update(null, updateWrapper);

        LambdaQueryWrapper<TravelJournalComment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TravelJournalComment::getParentCommentId, commentId).eq(TravelJournalComment::getStatus, 0);
        List<TravelJournalComment> replies = travelJournalCommentMapper.selectList(queryWrapper);

        if (!replies.isEmpty()) {
            List<Long> replyIds = replies.stream().map(TravelJournalComment::getId).collect(Collectors.toList());
            LambdaUpdateWrapper<TravelJournalComment> replyUpdateWrapper = new LambdaUpdateWrapper<>();
            replyUpdateWrapper.in(TravelJournalComment::getId, replyIds).set(TravelJournalComment::getStatus, 2);
            travelJournalCommentMapper.update(null, replyUpdateWrapper);
            log.info("删除了{}个回复", replies.size());
        }

    }


    /**
     * 将 TravelJournal 实体转为 TravelJournalVO
     */
    private TravelJournalVO toVO(TravelJournal journal) {
        // 查询用户信息
        User user = userMapper.selectById(journal.getUserId());

        // 解析 images JSON 数组
        List<String> imgUrls;
        try {
            imgUrls = JSON.parseArray(journal.getImages(), String.class);
        } catch (Exception e) {
            imgUrls = new ArrayList<>();
        }

        // 解析 tags 逗号分隔
        List<String> tagList;
        if (StringUtils.hasText(journal.getTags())) {
            tagList = Arrays.asList(journal.getTags().split(","));
        } else {
            tagList = new ArrayList<>();
        }

        // 查询当前登录用户是否已点赞/收藏该游记
        Long currentUserId = BaseContext.getCurrentId();
        boolean isLiked = false;
        boolean isCollected = false;
        if (currentUserId != null) {
            LambdaQueryWrapper<TravelJournalLike> likeWrapper = new LambdaQueryWrapper<>();
            likeWrapper.eq(TravelJournalLike::getJournalId, journal.getId())
                    .eq(TravelJournalLike::getUserId, currentUserId);
            isLiked = travelJournalLikeMapper.selectCount(likeWrapper) > 0;

            LambdaQueryWrapper<TravelJournalCollect> collectWrapper = new LambdaQueryWrapper<>();
            collectWrapper.eq(TravelJournalCollect::getJournalId, journal.getId())
                    .eq(TravelJournalCollect::getUserId, currentUserId);
            isCollected = travelJournalCollectMapper.selectCount(collectWrapper) > 0;
        }

        return TravelJournalVO.builder()
                .id(journal.getId())
                .userId(journal.getUserId())
                .title(journal.getTitle())
                .content(journal.getContent())
                .avatar(user != null ? user.getAvatar() : null)
                .nickname(user != null ? user.getNickname() : null)
                .location(journal.getLocation())
                .tags(tagList)
                .imgUrls(imgUrls)
                .likeCount(journal.getLikeCount())
                .collectCount(journal.getCollectCount())
                .isLiked(isLiked)
                .isCollected(isCollected)
                .createTime(journal.getCreateTime())
                .build();
    }

    @Override
    public List<CommentVO> getCommentList(Long journalId) {
        log.info("查询游记评论列表，游记ID：{}", journalId);
        TravelJournal journal = travelJournalMapper.selectById(journalId);
        if (journal == null) {
            throw new RuntimeException("游记不存在");
        }
        LambdaQueryWrapper<TravelJournalComment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TravelJournalComment::getJournalId, journalId)
                    .eq(TravelJournalComment::getStatus, 0)
                    .orderByDesc(TravelJournalComment::getCreateTime);

        List<TravelJournalComment> comments = travelJournalCommentMapper.selectList(queryWrapper);

        if (comments.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> userIds = comments.stream()
                .map(TravelJournalComment::getUserId)
                .distinct()
                .collect(Collectors.toList());

        List<Long> parentCommentIds = comments.stream()
                .map(TravelJournalComment::getParentCommentId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        List<Long> allUserIds = new ArrayList<>(userIds); if (!parentCommentIds.isEmpty()) {
            List<TravelJournalComment> parentComments = travelJournalCommentMapper.selectBatchIds(parentCommentIds);
            List<Long> parentUserIds = parentComments.stream()
                    .map(TravelJournalComment::getUserId)
                    .distinct()
                    .collect(Collectors.toList());
            allUserIds.addAll(parentUserIds);
            allUserIds = allUserIds.stream().distinct().collect(Collectors.toList());
        }

        Map<Long, User> userMap = new java.util.HashMap<>();
        if (!allUserIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(allUserIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(User::getId, user -> user));
        }

        Map<Long, TravelJournalComment> parentCommentMap = new java.util.HashMap<>();
        if (!parentCommentIds.isEmpty()) {
            List<TravelJournalComment> parentComments = travelJournalCommentMapper.selectBatchIds(parentCommentIds);
            parentCommentMap = parentComments.stream()
                    .collect(Collectors.toMap(TravelJournalComment::getId, comment -> comment));
        }

        List<CommentVO> commentVOList = new ArrayList<>();
        for (TravelJournalComment comment : comments) {
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(comment, vo);

            User user = userMap.get(comment.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setAvatar(user.getAvatar());
            }

            if (comment.getParentCommentId() != null) {
                TravelJournalComment parentComment = parentCommentMap.get(comment.getParentCommentId());
                if (parentComment != null) {
                    User parentUser = userMap.get(parentComment.getUserId());
                    if (parentUser != null) {
                        vo.setParentUsername(parentUser.getUsername());
                    }
                }
            }

            commentVOList.add(vo);
        }

        return commentVOList;
    }

    // 删除游记
    @Override
    @Transactional
    public void deleteJournal(Long journalID) {
        Long userId = BaseContext.getCurrentId();
        log.info("删除游记，用户ID：{}，游记ID：{}", userId, journalID);

        TravelJournal journal = travelJournalMapper.selectById(journalID);
        if(journal == null){
            throw new RuntimeException("游记不存在");
        }
        if (!journal.getUserId().equals(userId)){
            throw new RuntimeException("您没有权限删除该游记");
        }
        if (journal.getStatus() == 0){
            throw new RuntimeException("游记已删除");
        }

        LambdaUpdateWrapper<TravelJournal> journalUpdate = new LambdaUpdateWrapper<>();
        journalUpdate.eq(TravelJournal::getId, journalID)
                .set(TravelJournal::getStatus, 0);
        travelJournalMapper.update(null, journalUpdate);

        LambdaUpdateWrapper<TravelJournalComment> commentUpdate = new LambdaUpdateWrapper<>();
        commentUpdate.eq(TravelJournalComment::getJournalId, journalID)
                .set(TravelJournalComment::getStatus, 0);
        travelJournalCommentMapper.update(null, commentUpdate);

        LambdaQueryWrapper<TravelJournalLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(TravelJournalLike::getJournalId, journalID);
        travelJournalLikeMapper.delete(likeWrapper);

        log.info("删除游记成功,游记ID：{}", journalID);
    }

    @Override
    @Transactional
    public void collect(Long journalId) {
        Long userId = BaseContext.getCurrentId();
        TravelJournal journal = travelJournalMapper.selectById(journalId);
        if (journal == null || journal.getStatus() != 1) {
            throw new BaseException("游记不存在");
        }
        LambdaQueryWrapper<TravelJournalCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelJournalCollect::getJournalId, journalId)
                .eq(TravelJournalCollect::getUserId, userId);
        TravelJournalCollect exist = travelJournalCollectMapper.selectOne(wrapper);
        if (exist != null) {
            throw new BaseException("已收藏该游记");
        }
        TravelJournalCollect collect = new TravelJournalCollect();
        collect.setJournalId(journalId);
        collect.setUserId(userId);
        collect.setCreateTime(LocalDateTime.now());
        travelJournalCollectMapper.insert(collect);

        LambdaUpdateWrapper<TravelJournal> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TravelJournal::getId, journalId)
                .setSql("collect_count = collect_count + 1");
        travelJournalMapper.update(null, updateWrapper);
        log.info("用户{}收藏游记成功，游记id：{}", userId, journalId);
    }

    @Override
    @Transactional
    public void uncollect(Long journalId) {
        Long userId = BaseContext.getCurrentId();
        LambdaQueryWrapper<TravelJournalCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelJournalCollect::getJournalId, journalId)
                .eq(TravelJournalCollect::getUserId, userId);
        TravelJournalCollect exist = travelJournalCollectMapper.selectOne(wrapper);
        if (exist == null) {
            throw new BaseException("未收藏该游记");
        }
        travelJournalCollectMapper.deleteById(exist.getId());

        LambdaUpdateWrapper<TravelJournal> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(TravelJournal::getId, journalId)
                .setSql("collect_count = GREATEST(collect_count - 1, 0)");
        travelJournalMapper.update(null, updateWrapper);
        log.info("用户{}取消收藏游记成功，游记id：{}", userId, journalId);
    }

    @Override
    public List<TravelJournalVO> myCollects() {
        Long userId = BaseContext.getCurrentId();
        LambdaQueryWrapper<TravelJournalCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelJournalCollect::getUserId, userId)
                .orderByDesc(TravelJournalCollect::getCreateTime);
        List<TravelJournalCollect> collects = travelJournalCollectMapper.selectList(wrapper);
        if (collects.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> journalIds = collects.stream()
                .map(TravelJournalCollect::getJournalId)
                .distinct()
                .collect(Collectors.toList());
        List<TravelJournal> journals = travelJournalMapper.selectBatchIds(journalIds);
        return journals.stream()
                .filter(j -> j != null && j.getStatus() == 1)
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TravelJournalVO> myJournals() {
        Long userId = BaseContext.getCurrentId();
        LambdaQueryWrapper<TravelJournal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelJournal::getUserId, userId)
                .eq(TravelJournal::getStatus, 1)
                .orderByDesc(TravelJournal::getCreateTime);
        List<TravelJournal> journals = travelJournalMapper.selectList(wrapper);
        if (journals.isEmpty()) {
            return new ArrayList<>();
        }
        return journals.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<TravelJournalVO> hot(Integer limit) {
        int size = limit == null ? 10 : Math.min(limit, 50);
        LambdaQueryWrapper<TravelJournal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TravelJournal::getStatus, 1)
                .orderByDesc(TravelJournal::getLikeCount)
                .orderByDesc(TravelJournal::getCreateTime)
                .last("LIMIT " + size);
        List<TravelJournal> journals = travelJournalMapper.selectList(wrapper);
        if (journals.isEmpty()) {
            return new ArrayList<>();
        }
        return journals.stream().map(this::toVO).collect(Collectors.toList());
    }
}


