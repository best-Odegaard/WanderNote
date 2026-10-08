package com.gkv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gkv.entity.HotelCandidate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 酒店候选库（自建精选，运营维护）
 * 查询条件简单（城市 + 状态 + 排序），直接用 MyBatis-Plus 的条件构造器，不写 XML。
 */
@Mapper
public interface HotelCandidateMapper extends BaseMapper<HotelCandidate> {
}
