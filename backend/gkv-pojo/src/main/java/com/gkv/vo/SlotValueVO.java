package com.gkv.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个槽位当前的值与来源。
 *
 * source 必须暴露给前端：user=用户点选/输入，inferred=系统推断，
 * default=跳过时用的默认值。三者对用户的含义不同（default 值在生成前应提示可修改）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("槽位取值")
public class SlotValueVO {

    @ApiModelProperty("值")
    private String value;

    @ApiModelProperty("来源：user / inferred / default / imported")
    private String source;
}
