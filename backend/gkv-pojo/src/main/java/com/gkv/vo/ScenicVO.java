package com.gkv.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenicVO {
    private Long id;
    private String name;
    private String cover;
    private String city;
    private Double rating;
    private String category;
    private BigDecimal price;
    private String openTime;
    private String description;
    private List<String> images;
    private String address;
    /** 当前登录用户是否已收藏 */
    private Boolean isCollected;
}
