package com.gkv.dto;

import lombok.Data;
import java.util.List;

@Data
public class TripPlanDTO {
    private String title;
    private List<DayScheduleDTO> day_list;
    private String total_walk;
}
