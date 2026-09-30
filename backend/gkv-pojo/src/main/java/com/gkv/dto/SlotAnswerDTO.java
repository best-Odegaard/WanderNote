package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户对某个槽位问题的一次回答。
 *
 * 两种用法：
 *   · 点选项：传 value
 *   · 点跳过：skipped=true，引擎按 defaultValue 填充（标记来源 default）或仅记录已问过
 */
@Data
@ApiModel("槽位回答")
public class SlotAnswerDTO {

    @ApiModelProperty(value = "会话 id", required = true)
    private String sessionId;

    @ApiModelProperty(value = "槽位标识", required = true)
    private String slot;

    @ApiModelProperty("用户选择/输入的值")
    private String value;

    @ApiModelProperty("是否跳过该问题")
    private Boolean skipped = false;
}
