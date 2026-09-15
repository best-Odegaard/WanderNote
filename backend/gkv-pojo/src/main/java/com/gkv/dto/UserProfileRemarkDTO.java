package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 管理端更新运营备注与标记入参
 *
 * 与画像内容分开保存：备注只属于运营视角，用户端不可见，
 * 和「人工修正画像」混在一个提交里会让"改了什么"变得不可追踪。
 */
@Data
@ApiModel("更新运营备注与标记入参")
public class UserProfileRemarkDTO {

    @ApiModelProperty("运营备注")
    private String remark;

    @ApiModelProperty("运营标记")
    private String profileMark;
}
