package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 摘要模型返回值的 JSON 契约
 *
 * 字段名就是写给模型的契约里的 key（下划线），fastjson 按字段名直接映射，
 * 所以这里刻意用下划线命名而不是驼峰，避免中间再叠一层转换。
 *
 * 契约（明确写进 prompt，要求只输出 JSON、不要解释、不要代码块包裹）：
 * {
 *   "summary_text": "≤200 字画像摘要",
 *   "free_tags": ["自由标签"],
 *   "constraints": "硬性约束，无则空字符串",
 *   "budget_level": "经济/适中/高档 之一，判断不了填空串",
 *   "pace": "悠闲/常规/暴走 之一",
 *   "companions": "独自/情侣/朋友/家庭 之一",
 *   "prefer_days": 整数，判断不了填 0
 * }
 *
 * 字段为 null 表示模型没给这一项，调用侧不覆盖库里的旧值；
 * 字段为空串/空数组表示模型明确说"没有"，调用侧按清空处理（约束除外）。
 */
@Data
@ApiModel("摘要模型输出契约")
public class ProfileSummaryDTO {

    @ApiModelProperty("画像摘要，≤200 字")
    private String summary_text;

    @ApiModelProperty("自由标签")
    private List<String> free_tags;

    @ApiModelProperty("硬性约束，无则空字符串")
    private String constraints;

    @ApiModelProperty("预算档位：经济/适中/高档")
    private String budget_level;

    @ApiModelProperty("出行节奏：悠闲/常规/暴走")
    private String pace;

    @ApiModelProperty("同行人：独自/情侣/朋友/家庭")
    private String companions;

    @ApiModelProperty("偏好天数，判断不了填 0")
    private Integer prefer_days;
}
