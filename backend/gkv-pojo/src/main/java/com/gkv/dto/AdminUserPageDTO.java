package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端用户分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("管理端用户分页查询参数")
public class AdminUserPageDTO extends PageQuery {
    @ApiModelProperty("关键词（用户名/昵称/手机号）")
    private String keyword;

    @ApiModelProperty("状态：0禁用 1启用")
    private Integer status;

    @ApiModelProperty("画像标签筛选（受控标签名，精确匹配）")
    private String profileTag;

    @ApiModelProperty("画像标记筛选（模糊匹配）")
    private String profileMark;
}
