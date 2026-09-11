package com.gkv.vo;

import com.gkv.dto.PlanResponseDTO;
import com.gkv.dto.TripPlanFrameDTO;
import lombok.Data;

/**
 * 行程生成任务状态（前端轮询）
 * status: PROCESSING / DONE / ERROR / CANCELED
 * stage:  retrieving / generating_frame / generating_detail / done
 */
@Data
public class PlanTaskVO {
    private String taskId;
    private String status;
    private String stage;
    private String message;
    /** 已等待秒数 */
    private Integer elapsedSec;
    /** 阶段一产物：行程框架（骨架预览） */
    private TripPlanFrameDTO frame;
    /** 阶段二产物：完整行程 */
    private PlanResponseDTO plan;
    /** 错误信息 */
    private String errorMsg;
}
