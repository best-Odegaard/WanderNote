package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 管理端首页 Banner 新增/编辑参数（id 为空=新增）
 */
@Data
@ApiModel("Banner新增/编辑参数")
public class BannerSaveDTO {
    @ApiModelProperty("id，新增时为空")
    private Long id;

    @ApiModelProperty("标题")
    private String title;

    @ApiModelProperty("副标题")
    private String subtitle;

    @ApiModelProperty("emoji")
    private String emoji;

    @ApiModelProperty("图片URL")
    private String imageUrl;

    @ApiModelProperty("跳转链接")
    private String linkUrl;

    @ApiModelProperty("排序号")
    private Integer sortOrder;

    @ApiModelProperty("状态：0禁用 1启用")
    private Integer status;
}
