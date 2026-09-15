package com.gkv.dto;

import lombok.Data;

/**
 * 精选行程保存 DTO（后台新增/编辑）
 */
@Data
public class FeaturedTripSaveDTO {

    /** 有 id 为编辑，无 id 为新增 */
    private Long id;

    private String title;
    private String subtitle;
    private String city;
    private Integer days;
    private String cover;

    /** 完整行程 JSON（后台可直接编辑；也可由「解析链接」接口生成后回填） */
    private String tripJson;

    /** 内容来源链接（外部游记） */
    private String sourceUrl;

    /**
     * 解析链接时自动抓到的网页正文，只在「解析生成行程」的返回里带，保存接口忽略该字段。
     * 管理端拿它填进「行程原文」，让人看到这次生成到底用了什么内容。
     */
    private String sourceText;

    private Integer sortOrder;
    /** 状态：0下架 1上架 */
    private Integer status;
}
