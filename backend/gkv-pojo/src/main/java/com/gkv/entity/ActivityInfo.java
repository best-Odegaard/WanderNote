package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_info")
public class ActivityInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String cover;
    private String city;
    private String location;
    private String category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String description;
    private Integer isHot;
    private Integer enrollCount;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
