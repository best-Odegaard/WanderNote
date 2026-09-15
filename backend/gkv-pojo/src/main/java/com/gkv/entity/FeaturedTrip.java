package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 精选行程（首页轮播展示的「一整个城市的完整行程」）
 *
 * tripJson 内嵌完整行程（逐日明细），不引用 trip_plan 表，
 * 这样精选内容自包含，不会因为别的行程被删而碎掉。
 */
@Data
@TableName("featured_trip")
public class FeaturedTrip {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String subtitle;
    private String city;
    private Integer days;
    private String cover;
    /** 完整行程 JSON（结构与前端 TripPlan 对齐） */
    private String tripJson;
    /** 内容来源链接（外部游记，供溯源） */
    private String sourceUrl;
    private Integer sortOrder;
    /** 状态：0下架 1上架 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
