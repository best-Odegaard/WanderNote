package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 角色新增/编辑参数（id 为空=新增）
 */
@Data
@ApiModel("角色新增/编辑参数")
public class RoleSaveDTO {
    @ApiModelProperty("id，新增时为空")
    private Long id;

    @NotBlank(message = "角色标识不能为空")
    @ApiModelProperty("角色标识，如 super_admin")
    private String roleKey;

    @NotBlank(message = "角色名称不能为空")
    @ApiModelProperty("角色名称")
    private String roleName;

    @ApiModelProperty("权限点，逗号分隔")
    private String perms;

    @ApiModelProperty("角色描述")
    private String description;
}
