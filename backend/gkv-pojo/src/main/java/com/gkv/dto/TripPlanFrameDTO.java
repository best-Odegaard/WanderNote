package com.gkv.dto;

import lombok.Data;

import java.util.List;

/**
 * 行程框架（阶段一输出，仅含每日景点名单，用于前端秒出骨架预览）
 */
@Data
public class TripPlanFrameDTO {
    private String title;
    private String total_walk;
    private List<FrameDayDTO> day_list;
}
