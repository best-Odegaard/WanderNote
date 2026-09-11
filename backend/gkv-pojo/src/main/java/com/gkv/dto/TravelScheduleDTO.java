package com.gkv.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class TravelScheduleDTO {
    @NotBlank(message = "目的地不能为空")
    private String destination;

    @NotNull(message = "出行天数不能为空")
    private Integer travelDays;

    @NotNull(message = "至少选择一项旅行偏好")
    private List<String> travelPreference;

    @NotNull(message = "出行人数不能为空")
    private Integer peopleNum;

    private BigDecimal budget;
    private String startCity;

}
