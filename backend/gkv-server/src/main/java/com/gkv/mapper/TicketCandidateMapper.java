package com.gkv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gkv.entity.TicketCandidate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订票候选库（自建精选班次，运营维护；火车与飞机共用一张表）
 */
@Mapper
public interface TicketCandidateMapper extends BaseMapper<TicketCandidate> {
}
