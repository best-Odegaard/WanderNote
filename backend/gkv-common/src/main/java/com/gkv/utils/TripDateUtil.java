package com.gkv.utils;

import java.time.LocalDate;

/**
 * 与智能体的日期约定：BaseInfo 里 start_day / end_date 是必填项，格式必须是 yyyy-MM-dd，
 * 智能体会校验「日期从 start_day 开始连续递增，共 days 天」。
 *
 * 没有明确出发日期时（例如按城市/游记生成行程、后台解析精选行程），
 * 统一按「今天出发」推算，避免传空被判成参数缺失（智能体侧会直接 422）。
 */
public class TripDateUtil {

    /** 出发日期：今天 */
    public static String todayStartDay() {
        return LocalDate.now().toString();
    }

    /** 返回日期：按出发日期 + 天数推算（天数非法时按 1 天） */
    public static String endDateOf(String startDay, Integer days) {
        int d = (days == null || days < 1) ? 1 : days;
        return LocalDate.parse(startDay).plusDays(d - 1L).toString();
    }
}
