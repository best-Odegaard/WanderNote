package com.gkv.dto;

import lombok.Data;
import java.util.List;

@Data
public class DayScheduleDTO {
    private String date;
    private List<AttractionDTO> schedule;
    private Integer total_spot;
    private Double total_distance;
}
