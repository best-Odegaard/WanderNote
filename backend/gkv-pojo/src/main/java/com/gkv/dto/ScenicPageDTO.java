package com.gkv.dto;

import lombok.Data;

/**
 * 景点列表查询参数（字段对齐前端 ScenicQuery：page/pageSize）
 */
@Data
public class ScenicPageDTO {
    private String keyword;
    private String city;
    private String category;
    /** 排序：rating-按评分降序 price-按价格升序，默认 sort_order 升序 */
    private String sortBy;
    private Integer page = 1;
    private Integer pageSize = 10;
}
