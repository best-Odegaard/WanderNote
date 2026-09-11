package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scenic_spot")
public class ScenicSpot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String cover;
    private String city;
    private BigDecimal rating;
    private String category;
    private BigDecimal price;
    private String openTime;
    private String description;
    private String address;
    private String images;
    private Integer isHot;
    private Integer viewCount;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
