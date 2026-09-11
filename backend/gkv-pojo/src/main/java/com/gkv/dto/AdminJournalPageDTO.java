package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端游记分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("管理端游记分页查询参数")
public class AdminJournalPageDTO extends PageQuery {
    @ApiModelProperty("关键词（标题）")
    private String keyword;

    @ApiModelProperty("状态：0已删除/下架 1正常")
    private Integer status;
}
