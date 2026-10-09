package com.gkv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gkv.entity.TripTicket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行程票务（一个行程每个方向一条，(trip_id, direction) 唯一索引）
 */
@Mapper
public interface TripTicketMapper extends BaseMapper<TripTicket> {
}
