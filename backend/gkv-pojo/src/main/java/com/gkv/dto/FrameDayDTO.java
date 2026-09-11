package com.gkv.dto;

import lombok.Data;

import java.util.List;

/**
 * 行程框架中的单天安排（只含景点名单）
 */
@Data
public class FrameDayDTO {
    private String date;
    private List<String> spot_names;
}
