package com.gkv.dto;

import lombok.Data;

/**
 * 精选行程「解析链接生成行程」入参
 *
 * 为什么会需要 city / content：
 *   分享出来的外部链接（小红书、抖音等）里通常不含中文城市名，
 *   只靠链接无法判断目的地，所以城市要能手工指定；
 *   而只给城市会让 AI 凭空生成，跟原文无关，
 *   所以再提供一个可选的「行程原文」——粘进来就让 AI 按原文产出结构化行程。
 */
@Data
public class FeaturedParseDTO {

    /** 外部游记链接（可只填链接，仅用于溯源） */
    private String sourceUrl;

    /** 目的地城市；不填时会尝试从链接里识别 */
    private String city;

    /** 行程原文（游记/攻略正文或要点），可选；填了就会按原文生成 */
    private String content;
}
