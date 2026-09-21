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

    /**
     * 景点图片 URL —— 由智能体在 detail 阶段调高德 POI 接口补全（见 travel_agent/utils/amap_client.py）。
     * 字段名与智能体返回保持一致的蛇形命名，fastjson 按名匹配即可自动绑定。
     * 取不到时为空串，不影响行程。
     */
    private String image_url;

    /**
     * 票价兜底提示（参考值，非准确门票价），同样由智能体调高德补全。
     * 取不到时为空串。
     */
    private String ticket_hint;
}
