package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * 管理端活动新增/编辑参数（id 为空=新增）
 */
@Data
@ApiModel("活动新增/编辑参数")
public class ActivitySaveDTO {
    @ApiModelProperty("id，新增时为空")
    private Long id;

    @NotBlank(message = "活动标题不能为空")
    @ApiModelProperty("活动标题")
    private String title;

    @ApiModelProperty("封面图URL")
    private String cover;

    @ApiModelProperty("城市")
    private String city;

    @ApiModelProperty("地点")
    private String location;

    @ApiModelProperty("分类：音乐节/展览/户外/美食节/文化体验")
    private String category;

    @ApiModelProperty("开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty("活动介绍")
    private String description;

    @ApiModelProperty("是否热门：0否 1是")
    private Integer isHot;

    @ApiModelProperty("报名人数")
    private Integer enrollCount;

    @ApiModelProperty("排序号")
    private Integer sortOrder;

    @ApiModelProperty("状态：0禁用 1启用")
    private Integer status;
}
