package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * App 端提交意见反馈参数
 */
@Data
@ApiModel("提交意见反馈参数")
public class UserFeedbackSubmitDTO {
    @ApiModelProperty("类型：功能建议/内容纠错/投诉举报/其他")
    private String type;

    @NotBlank(message = "反馈内容不能为空")
    @ApiModelProperty("反馈内容")
    private String content;

    @ApiModelProperty("图片URL列表")
    private List<String> images;

    @ApiModelProperty("联系方式（手机号/邮箱等，选填）")
    private String contact;
}
