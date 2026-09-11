package com.gkv.dto;

import lombok.Data;

/**
 * 活动列表查询参数（字段对齐前端 ActivityQuery）
 */
@Data
public class ActivityPageDTO {
    private String city;
    private String category;
    /** 开始日期筛选（yyyy-MM-dd，可选） */
    private String startDate;
    /** 结束日期筛选（yyyy-MM-dd，可选） */
    private String endDate;
    private Integer page = 1;
    private Integer pageSize = 10;
}
