package com.gkv.dto;

import lombok.Data;
import java.util.List;

@Data
public class BaseInfoDTO {
        private String departure_city;
        private String destination_city;
        private String start_day;
        private String end_date;
        private Integer days;
        private List<String> hobby;
        private String people_num;
        private String budget;
        /** 外部带入的行程上下文（如游记/景点），透传给智能体作为首轮提示词 */
        private String context_note;
        /**
         * 用户历史画像回灌文本。只由后端服务端注入，前端不传。
         * 服务端每次都用自己查库构建的值覆盖，客户端传什么都不作数。
         */
        private String profile_note;
}