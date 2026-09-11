package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("travel_journal_like")
public class TravelJournalLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long journalId;
    private Long userId;
    private LocalDateTime createTime;
}
