package com.gkv.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 能力就绪标记。
 *
 * 由后端槽位引擎计算，前端只负责按标记渐显入口 —— 判定规则必须只有一份，
 * 否则前后端各判一套，迟早出现「按钮亮了但生成失败」。
 */
@Data
@ApiModel("能力就绪标记")
public class SlotReadyVO {

    @ApiModelProperty("可生成行程骨架（目的地 + 天数已知）")
    private Boolean frame = false;

    @ApiModelProperty("可选酒店（完整度达阈值且已知住宿偏好）")
    private Boolean hotel = false;

    @ApiModelProperty("可查车票（完整度达阈值且已知出发地）")
    private Boolean transport = false;

    @ApiModelProperty("可生成完整路线（完整度达阈值且已知住宿偏好）")
    private Boolean fullRoute = false;
}
