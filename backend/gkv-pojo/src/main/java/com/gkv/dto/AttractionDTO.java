package com.gkv.dto;

import lombok.Data;
import java.util.List;

@Data
public class AttractionDTO {
    private String visit_time_range;
    private String spot_name;
    private String open_time;
    private String ticket;
    private String location;
    private String feature_tag;
    private List<String> photos_json;
}
