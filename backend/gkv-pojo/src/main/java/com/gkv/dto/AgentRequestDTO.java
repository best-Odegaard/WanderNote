package com.gkv.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentRequestDTO {
    private String departure_city;
    private String destination_city;
    private String start_day;
    private String end_date;
    private Integer days;
    private List<String> hobby;
    private String people_num;
    private String budget;
}
