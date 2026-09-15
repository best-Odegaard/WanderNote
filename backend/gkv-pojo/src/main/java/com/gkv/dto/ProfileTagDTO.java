package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 画像偏好标签（受控标签 + 命中次数权重）
 *
 * 刻意建成独立的类而不是裸 Map：裸 Map 走 JSONObject(HashMap) 序列化时 key 顺序不确定，
 * 一旦顺序变了，管理端按标签做 JSON 子串筛选（LIKE '%"自然风光"%'）会时灵时不灵。
 * 这个类只有 tag / weight 两个字段，序列化时用固定顺序（见 ProfileTagUtil）。
 *
 * 也刻意不叫 VO：它是实体 JSON 列的元素类型，输入输出两侧共用同一个结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("画像偏好标签")
public class ProfileTagDTO {

    @ApiModelProperty("受控标签名，必须在受控词表内")
    private String tag;

    @ApiModelProperty("权重（命中次数）")
    private Integer weight;

    public ProfileTagDTO(String tag, int weight) {
        this.tag = tag;
        this.weight = weight;
    }
}
