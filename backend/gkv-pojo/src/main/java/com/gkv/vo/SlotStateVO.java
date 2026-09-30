package com.gkv.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话的槽位快照 —— 对话链路每一轮都下发，前端据此渲染：
 *   · chips（nextQuestion）
 *   · 需求完整度进度条（completeness）
 *   · 渐显的能力入口（ready）
 */
@Data
@ApiModel("槽位快照")
public class SlotStateVO {

    @ApiModelProperty("会话 id")
    private String sessionId;

    @ApiModelProperty("各槽位当前值与来源，key=槽位标识")
    private Map<String, SlotValueVO> slots = new LinkedHashMap<>();

    @ApiModelProperty("需求完整度 0~1")
    private BigDecimal completeness = BigDecimal.ZERO;

    @ApiModelProperty("能力就绪标记")
    private SlotReadyVO ready = new SlotReadyVO();

    @ApiModelProperty("下一个问题；为 null 表示问完了或不再提问")
    private SlotQuestionVO nextQuestion;

    @ApiModelProperty("已提问轮次")
    private Integer askedCount = 0;
}
