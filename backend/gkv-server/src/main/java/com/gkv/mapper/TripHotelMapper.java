package com.gkv.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gkv.entity.TripHotel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行程住宿（一个行程一条，trip_id 唯一索引）
 */
@Mapper
public interface TripHotelMapper extends BaseMapper<TripHotel> {
}
