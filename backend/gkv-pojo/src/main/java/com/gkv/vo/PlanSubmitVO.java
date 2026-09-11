package com.gkv.vo;

import com.gkv.dto.PlanResponseDTO;
import lombok.Data;

/**
 * 提交行程生成任务的返回结果
 * - fromCache=true 时直接返回缓存的完整行程，无需轮询
 * - fromCache=false 时返回 taskId，前端轮询 /travel/plan/status/{taskId}
 */
@Data
public class PlanSubmitVO {
    private String taskId;
    private Boolean fromCache = false;
    private PlanResponseDTO plan;
}
