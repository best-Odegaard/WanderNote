package com.gkv.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 下一个要问用户的问题。
 *
 * 前端拿到它之后渲染成一排可点击的选项，用户点一下即完成一轮回答，
 * 不需要打字、也不需要自己想该提供什么信息。
 */
@Data
@ApiModel("槽位问题")
public class SlotQuestionVO {

    @ApiModelProperty("槽位标识，回答时原样回传")
    private String slot;

    @ApiModelProperty("问题文案")
    private String text;

    @ApiModelProperty("可选的一句话建议")
    private String hint;

    @ApiModelProperty("可点击选项；为空表示该问题需要用户自由输入")
    private List<SlotOptionVO> options;

    @ApiModelProperty("是否允许跳过")
    private Boolean allowSkip;

    @ApiModelProperty("跳过按钮文案")
    private String skipLabel;
}
