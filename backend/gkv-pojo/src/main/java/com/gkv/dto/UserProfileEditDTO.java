package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 管理端人工修正画像入参
 *
 * 语义是「整份覆盖，允许清空」：字段传 null 或空集合都表示把该项清掉。
 * 刻意不包含 userId 与 injectEnabled：
 *   - userId 由路径参数决定，不接受 body 覆盖；
 *   - 回灌开关是用户端的开关，运营改它会和用户自己的选择打架。
 */
@Data
@ApiModel("人工修正画像入参")
public class UserProfileEditDTO {

    @ApiModelProperty("受控偏好标签及权重（整份覆盖，传空数组表示清空）")
    private List<ProfileTagDTO> preferenceTags;

    @ApiModelProperty("自由标签（整份覆盖）")
    private List<String> freeTags;

    @ApiModelProperty("硬性约束，多个用中文分号「；」分隔")
    private String constraintsText;

    @ApiModelProperty("预算档位：经济/适中/高档（不在词表内视为清空）")
    private String budgetLevel;

    @ApiModelProperty("出行节奏：悠闲/常规/暴走（不在词表内视为清空）")
    private String pace;

    @ApiModelProperty("同行人：独自/情侣/朋友/家庭（不在词表内视为清空）")
    private String companions;

    @ApiModelProperty("偏好天数（<=0 或空视为清空）")
    private Integer preferDays;

    @ApiModelProperty("AI 画像摘要")
    private String summaryText;
}
