package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意见反馈实体
 */
@Data
@TableName("user_feedback")
public class UserFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** 类型：功能建议/内容纠错/投诉举报/其他 */
    private String type;
    private String content;
    /** 图片（JSON数组字符串） */
    private String images;
    private String contact;
    /** 状态：0待处理 1已处理 2已关闭 */
    private Integer status;
    private String reply;
    private Long adminId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
