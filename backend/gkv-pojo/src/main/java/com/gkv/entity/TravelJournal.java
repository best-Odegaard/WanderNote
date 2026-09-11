package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("travel_journal")
public class TravelJournal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String images;
    private String tags;
    private String location;
    private Integer status;
    private Integer likeCount;
    private Integer collectCount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;


}
