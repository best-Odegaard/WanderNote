package com.gkv.vo;

import com.gkv.dto.ProfileTagDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端画像详情 VO（列表与详情共用）
 *
 * 无画像时返回的是「带用户基础信息的空结构」，不是 404：
 * 管理端要能一眼看出"这个人还没沉淀出画像"，而不是把一个查不到当成接口出错。
 */
@Data
@ApiModel("管理端画像详情")
public class UserProfileVO {

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("登录账号")
    private String username;

    @ApiModelProperty("昵称")
    private String nickname;

    @ApiModelProperty("手机号")
    private String phone;

    @ApiModelProperty("受控偏好标签及权重（有序，权重倒序）")
    private List<ProfileTagDTO> preferenceTags;

    @ApiModelProperty("自由标签")
    private List<String> freeTags;

    @ApiModelProperty("硬性约束，多个用中文分号「；」分隔")
    private String constraintsText;

    @ApiModelProperty("预算档位：经济/适中/高档")
    private String budgetLevel;

    @ApiModelProperty("出行节奏：悠闲/常规/暴走")
    private String pace;

    @ApiModelProperty("同行人：独自/情侣/朋友/家庭")
    private String companions;

    @ApiModelProperty("偏好天数")
    private Integer preferDays;

    @ApiModelProperty("AI 画像摘要")
    private String summaryText;

    @ApiModelProperty("摘要最后生成时间")
    private LocalDateTime summaryTime;

    @ApiModelProperty("累计对话轮数")
    private Integer chatRounds;

    @ApiModelProperty("最后更新来源：rule/model/manual")
    private String lastUpdateSource;

    @ApiModelProperty("画像回灌开关：0关 1开")
    private Integer injectEnabled;

    @ApiModelProperty("运营备注")
    private String remark;

    @ApiModelProperty("运营标记")
    private String profileMark;

    @ApiModelProperty("画像更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("是否已沉淀出画像（无画像行时为 false）")
    private Boolean hasProfile;
}
