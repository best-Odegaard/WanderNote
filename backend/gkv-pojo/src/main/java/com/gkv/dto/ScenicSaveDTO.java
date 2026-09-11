package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端景点新增/编辑参数（id 为空=新增）
 */
@Data
@ApiModel("景点新增/编辑参数")
public class ScenicSaveDTO {
    @ApiModelProperty("id，新增时为空")
    private Long id;

    @NotBlank(message = "景点名称不能为空")
    @ApiModelProperty("景点名称")
    private String name;

    @ApiModelProperty("封面图URL")
    private String cover;

    @ApiModelProperty("城市")
    private String city;

    @ApiModelProperty("评分")
    private BigDecimal rating;

    @ApiModelProperty("分类：自然风光/历史文化/主题乐园/博物馆/古镇")
    private String category;

    @ApiModelProperty("价格")
    private BigDecimal price;

    @ApiModelProperty("开放时间")
    private String openTime;

    @ApiModelProperty("简介")
    private String description;

    @ApiModelProperty("地址")
    private String address;

    @ApiModelProperty("图片URL列表")
    private List<String> images;

    @ApiModelProperty("是否热门：0否 1是")
    private Integer isHot;

    @ApiModelProperty("排序号")
    private Integer sortOrder;

    @ApiModelProperty("状态：0禁用 1启用")
    private Integer status;
}
