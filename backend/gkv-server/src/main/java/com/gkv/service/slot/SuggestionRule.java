package com.gkv.service.slot;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条「猜你想问」规则（由 suggested-questions.yml 加载）。
 *
 * 条件字段刻意做成结构化的（whenCity / whenMissing / whenSlotEquals / whenSlotIn /
 * whenRoundAtLeast），而不是初稿里的表达式字符串（`!slots.departCity`、`slots.budget == 'low'`）——
 * 表达式要配一个解析器，出错只在运行时暴露；结构化字段编译期就能看清有哪些条件类型，
 * 配置写错了也只会"不命中"，不会把整轮对话搞崩。需要新条件类型时再加字段即可。
 */
@Data
public class SuggestionRule {

    /** 规则标识，仅用于日志与排查 */
    private String id;

    /** 目的地等于该城市时命中 */
    private String whenCity;

    /** 只要知道了目的地（任何城市）就命中；问题里可用 {city} 占位 */
    private boolean whenDestinationKnown;

    /** 列出的槽位**都还没值**时命中 */
    private List<String> whenMissing = new ArrayList<>();

    /** 指定槽位等于指定值时命中 */
    private Map<String, String> whenSlotEquals = new LinkedHashMap<>();

    /** 指定槽位取值落在给定集合内时命中 */
    private Map<String, List<String>> whenSlotIn = new LinkedHashMap<>();

    /** 已提问轮次 >= N 时命中 */
    private Integer whenRoundAtLeast;

    /** 候选问题 */
    private List<String> questions = new ArrayList<>();

    /** 是否命中当前对话状态 */
    public boolean matches(Map<String, String> slots, int round) {
        if (whenCity != null && !whenCity.equals(slots.get("destination"))) {
            return false;
        }
        if (whenDestinationKnown && !slots.containsKey("destination")) {
            return false;
        }
        for (String key : whenMissing) {
            if (slots.containsKey(key)) {
                return false;
            }
        }
        for (Map.Entry<String, String> e : whenSlotEquals.entrySet()) {
            String actual = slots.get(e.getKey());
            if (actual == null || !actual.equals(e.getValue())) {
                return false;
            }
        }
        for (Map.Entry<String, List<String>> e : whenSlotIn.entrySet()) {
            String actual = slots.get(e.getKey());
            if (actual == null || e.getValue() == null || !e.getValue().contains(actual)) {
                return false;
            }
        }
        return whenRoundAtLeast == null || round >= whenRoundAtLeast;
    }
}
