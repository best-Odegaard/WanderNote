package com.gkv.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 槽位问题的一个可选答案（前端渲染成可点击的 chip）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("槽位选项")
public class SlotOptionVO {

    @ApiModelProperty("展示文案，如「2 天」")
    private String label;

    @ApiModelProperty("提交值，如「2」")
    private String value;
}
