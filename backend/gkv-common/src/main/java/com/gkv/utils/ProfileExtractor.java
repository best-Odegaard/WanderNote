package com.gkv.utils;

import com.gkv.constant.ProfileConstant;
import com.gkv.dto.BaseInfoDTO;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 画像规则抽取器（每轮对话调用一次，同步、不调模型、足够便宜）
 *
 * 优先级与去重规则：
 *   1. 先吃结构化字段（问卷/表单里已有的天数、预算、人数、已有偏好标签），再用文本关键词补充。
 *      结构化字段比文本挖掘可靠得多。
 *   2. 同一轮对话内同一标签只记 1 次（用 Set 去重），否则"结构化字段 + 文本"会重复计数，
 *      权重就不再是"命中轮数"了。
 *   3. 文本命中预算/节奏/同行人时覆盖结构化字段推出来的值（用户嘴说的更接近真实意图）。
 *   4. 硬性约束只追加不覆盖，合并去重交给调用方（它要读到库里已有的值）。
 */
public final class ProfileExtractor {

    /** 与 TravelController 的预算校验保持同一套数字提取规则 */
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    private ProfileExtractor() {
    }

    /** 一轮对话的抽取结果 */
    public static class ExtractResult {
        /** 本轮命中的受控标签（已去重） */
        private final LinkedHashSet<String> tagHits = new LinkedHashSet<>();
        /** 本轮命中的硬性约束（已去重） */
        private final LinkedHashSet<String> constraintHits = new LinkedHashSet<>();
        private String budgetLevel;
        private String pace;
        private String companions;
        private Integer preferDays;

        public LinkedHashSet<String> getTagHits() {
            return tagHits;
        }

        public LinkedHashSet<String> getConstraintHits() {
            return constraintHits;
        }

        public String getBudgetLevel() {
            return budgetLevel;
        }

        public String getPace() {
            return pace;
        }

        public String getCompanions() {
            return companions;
        }

        public Integer getPreferDays() {
            return preferDays;
        }

        public boolean isEmpty() {
            return tagHits.isEmpty() && constraintHits.isEmpty()
                    && budgetLevel == null && pace == null && companions == null && preferDays == null;
        }
    }

    /**
     * 抽取一轮对话的画像增量。
     *
     * @param baseInfo  前端带的结构化出行信息，可为 null
     * @param userInput 本轮用户输入
     */
    public static ExtractResult extract(BaseInfoDTO baseInfo, String userInput) {
        ExtractResult result = new ExtractResult();

        // ---------- 1. 结构化字段 ----------
        if (baseInfo != null) {
            // 已有偏好标签直接计入（前端历史数据用的是"美食""摄影"这类短标签，走别名归一化）
            List<String> hobby = baseInfo.getHobby();
            if (hobby != null) {
                for (String raw : hobby) {
                    String tag = ProfileConstant.normalizeTag(raw);
                    if (tag != null) {
                        result.tagHits.add(tag);
                    }
                }
            }
            // 天数直接覆盖
            if (baseInfo.getDays() != null && baseInfo.getDays() > 0) {
                result.preferDays = baseInfo.getDays();
            }
            // 预算字符串 → 金额 → 档位
            Double amount = parseAmount(baseInfo.getBudget());
            if (amount != null && amount >= 0) {
                result.budgetLevel = ProfileConstant.budgetLevelOf(amount);
            }
            // 人数文本 → 同行人
            result.companions = companionsFromStructured(baseInfo.getPeople_num());
        }

        // ---------- 2. 文本关键词补充 ----------
        StringBuilder textBuilder = new StringBuilder();
        if (userInput != null) {
            textBuilder.append(userInput).append(' ');
        }
        if (baseInfo != null && baseInfo.getContext_note() != null) {
            // 外部带入的游记/景点正文也算文本信号
            textBuilder.append(baseInfo.getContext_note());
        }
        String text = textBuilder.toString();

        result.tagHits.addAll(ProfileConstant.matchTags(text));

        // 文本命中则覆盖结构化字段推出的值
        String budgetByText = ProfileConstant.matchSingleValue(text, ProfileConstant.BUDGET_KEYWORDS);
        if (budgetByText != null) {
            result.budgetLevel = budgetByText;
        }
        String paceByText = ProfileConstant.matchSingleValue(text, ProfileConstant.PACE_KEYWORDS);
        if (paceByText != null) {
            result.pace = paceByText;
        }
        String companionsByText = ProfileConstant.matchSingleValue(text, ProfileConstant.COMPANION_KEYWORDS);
        if (companionsByText != null) {
            result.companions = companionsByText;
        }

        result.constraintHits.addAll(ProfileConstant.matchConstraints(text));

        return result;
    }

    /**
     * 结构化人数 → 同行人。
     *
     * 只做关键词映射 + 一个保守的数字兜底：前端传的是纯数字人数（"2"），
     * 数字 2 既可能是情侣也可能是朋友，判不出就不判；"1" 是唯一无歧义的。
     */
    private static String companionsFromStructured(String peopleNum) {
        if (peopleNum == null || peopleNum.trim().isEmpty()) {
            return null;
        }
        String v = peopleNum.trim();
        String byKeyword = ProfileConstant.matchSingleValue(v, ProfileConstant.COMPANION_KEYWORDS);
        if (byKeyword != null) {
            return byKeyword;
        }
        if (v.matches("\\d+")) {
            try {
                if (Integer.parseInt(v) == 1) {
                    return "独自";
                }
            } catch (NumberFormatException ignore) {
                // 位数溢出等极端情况，判不出就不判
            }
        }
        return null;
    }

    /**
     * 从预算字符串里取金额。
     * 支持 "2000" / "人均1000" / "500元以内" / "2000-3000"（取区间下界）。
     * 取不到返回 null。
     */
    public static Double parseAmount(String budget) {
        if (budget == null || budget.trim().isEmpty()) {
            return null;
        }
        Matcher m = AMOUNT_PATTERN.matcher(budget.trim());
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    /** 把"已有约束 + 本轮命中"合并去重，统一用中文分号连接 */
    public static String mergeConstraints(String existing, LinkedHashSet<String> hits) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null && !existing.trim().isEmpty()) {
            for (String part : existing.split(ProfileConstant.CONSTRAINT_SPLIT_REGEX)) {
                String v = part.trim();
                if (!v.isEmpty()) {
                    merged.add(v);
                }
            }
        }
        if (hits != null) {
            for (String hit : hits) {
                if (hit != null && !hit.trim().isEmpty()) {
                    merged.add(hit.trim());
                }
            }
        }
        return String.join(ProfileConstant.CONSTRAINT_SEPARATOR, merged);
    }

    /** 约束文本 → 列表（供回灌文本与管理端展示使用，兼容中英文分号） */
    public static java.util.List<String> splitConstraints(String constraints) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (constraints == null || constraints.trim().isEmpty()) {
            return list;
        }
        for (String part : constraints.split(ProfileConstant.CONSTRAINT_SPLIT_REGEX)) {
            String v = part.trim();
            if (!v.isEmpty()) {
                list.add(v);
            }
        }
        return list;
    }

    /** 供调试/日志用：把关键词表打平成一行（不要在业务逻辑里用） */
    static String describe(Map<String, List<String>> dictionary) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : dictionary.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(' ');
        }
        return sb.toString();
    }
}
