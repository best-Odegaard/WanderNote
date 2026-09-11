package com.gkv.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gkv.entity.TravelJournal;
import com.gkv.entity.TravelJournalComment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TravelJournalCommentMapper extends BaseMapper<TravelJournalComment> {
    List<TravelJournalComment> selectList(LambdaQueryWrapper<TravelJournal> queryWrapper);
}

