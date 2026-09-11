package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端意见反馈分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("意见反馈分页查询参数")
public class UserFeedbackPageDTO extends PageQuery {
    @ApiModelProperty("状态：0待处理 1已处理 2已关闭")
    private Integer status;

    @ApiModelProperty("类型筛选")
    private String type;

    @ApiModelProperty("关键词（内容/联系方式）")
    private String keyword;
}
