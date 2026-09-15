package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户端画像回灌开关入参
 *
 * 语义提醒：这个开关只关「回灌」（不再把画像灌进 prompt），
 * 不关「累积」（后台仍然继续沉淀标签与摘要）。
 */
@Data
@ApiModel("画像回灌开关入参")
public class UserProfileSwitchDTO {

    @ApiModelProperty("是否启用画像回灌（true 开 / false 关）")
    private Boolean enabled;
}
