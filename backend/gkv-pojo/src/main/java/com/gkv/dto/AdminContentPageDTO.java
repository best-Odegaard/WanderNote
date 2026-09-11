package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端景点/活动分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("管理端内容分页查询参数")
public class AdminContentPageDTO extends PageQuery {
    @ApiModelProperty("关键词（名称/标题）")
    private String keyword;

    @ApiModelProperty("城市")
    private String city;

    @ApiModelProperty("分类")
    private String category;

    @ApiModelProperty("状态：0禁用 1启用")
    private Integer status;
}
