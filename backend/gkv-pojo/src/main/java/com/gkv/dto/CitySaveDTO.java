package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 管理端首页城市新增/编辑参数（id 为空=新增）
 */
@Data
@ApiModel("城市新增/编辑参数")
public class CitySaveDTO {
    @ApiModelProperty("id，新增时为空")
    private Long id;

    @NotBlank(message = "城市名称不能为空")
    @ApiModelProperty("城市名称")
    private String name;

    @ApiModelProperty("封面图URL")
    private String cover;

    @ApiModelProperty("评分")
    private BigDecimal rating;

    @ApiModelProperty("排序号")
    private Integer sortOrder;

    @ApiModelProperty("状态：0禁用 1启用")
    private Integer status;
}
