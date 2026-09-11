package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 管理员新增/编辑参数（id 为空=新增）
 */
@Data
@ApiModel("管理员新增/编辑参数")
public class AdminSaveDTO {
    @ApiModelProperty("id，新增时为空")
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @ApiModelProperty("登录账号")
    private String username;

    @ApiModelProperty("密码（编辑时留空表示不修改）")
    private String password;

    @ApiModelProperty("昵称")
    private String nickname;

    @ApiModelProperty("角色id")
    private Long roleId;

    @ApiModelProperty("状态：0禁用 1启用")
    private Integer status;
}
