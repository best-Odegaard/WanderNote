package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 意见反馈处理参数（回复并标记已处理）
 */
@Data
@ApiModel("意见反馈处理参数")
public class FeedbackHandleDTO {
    @ApiModelProperty("回复内容")
    private String reply;
}
