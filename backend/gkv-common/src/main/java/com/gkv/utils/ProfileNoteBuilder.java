package com.gkv.utils;

import com.gkv.dto.ProfileTagDTO;
import com.gkv.entity.UserProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * 画像回灌文本构建器（对话与规划共用）
 *
 * 这是"回灌"链路的唯一出口，也是"关掉开关就停止回灌"的唯一改动点：
 * 开关语义是「只关回灌，不关累积」—— 关掉之后后台仍然继续沉淀标签与摘要，
 * 只是这段文本不再灌进 prompt。如果需求方要的是"关掉就完全停止记录"，
 * 只需要在这里返回 null，外加规则累加处早退，改动就这两处。
 *
 * 整个构建过程不允许抛异常：任一环节出错返回 null，绝不能影响用户对话。
 */
public final class ProfileNoteBuilder {

    /** 回灌文本里"高频偏好"取前几个 */
    private static final int TOP_TAG_LIMIT = 5;

    private static final String HEADER =
            "【该用户的历史画像（来自以往对话，可直接参考，不要重复追问已知信息）】";

    private ProfileNoteBuilder() {
    }

    /**
     * @return 可直接塞进 base_info.profile_note 的文本；开关关闭 / 无画像 / 画像为空 一律返回 null
     */
    public static String build(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        // 开关关闭 → 不回灌（注意：这里返回 null 不会阻止规则累加继续写库）
        if (profile.getInjectEnabled() == null || profile.getInjectEnabled() != 1) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();

            List<ProfileTagDTO> topTags = ProfileTagUtil.topN(
                    ProfileTagUtil.parse(profile.getPreferenceTags()), TOP_TAG_LIMIT);
            if (!topTags.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (ProfileTagDTO tag : topTags) {
                    parts.add(tag.getTag() + "(权重" + tag.getWeight() + ")");
                }
                appendLine(sb, "高频偏好：", String.join("、", parts));
            }

            List<String> freeTags = parseFreeTags(profile.getFreeTags());
            if (!freeTags.isEmpty()) {
                appendLine(sb, "其他已知特点：", String.join("、", freeTags));
            }

            List<String> structured = new ArrayList<>();
            if (notBlank(profile.getBudgetLevel())) {
                structured.add("预算档位 " + profile.getBudgetLevel());
            }
            if (notBlank(profile.getPace())) {
                structured.add("出行节奏 " + profile.getPace());
            }
            if (notBlank(profile.getCompanions())) {
                structured.add("同行人 " + profile.getCompanions());
            }
            if (profile.getPreferDays() != null && profile.getPreferDays() > 0) {
                structured.add("偏好天数 " + profile.getPreferDays() + "天");
            }
            if (!structured.isEmpty()) {
                appendLine(sb, "结构化偏好：", String.join("，", structured));
            }

            if (notBlank(profile.getConstraintsText())) {
                appendLine(sb, "硬性约束（必须遵守）：",
                        String.join("、", ProfileExtractor.splitConstraints(profile.getConstraintsText())));
            }

            if (notBlank(profile.getSummaryText())) {
                appendLine(sb, "画像摘要：", profile.getSummaryText());
            }

            if (sb.length() == 0) {
                // 画像行在但内容全空（比如刚建行还没累加到任何东西）—— 没什么可回灌的
                return null;
            }
            return HEADER + "\n" + sb;
        } catch (Exception e) {
            // 画像构建失败不能影响对话，静默降级
            return null;
        }
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (!notBlank(value)) {
            return;
        }
        sb.append("- ").append(label).append(value).append('\n');
    }

    /** 自由标签列的宽松解析（脏数据降级成空集合） */
    @SuppressWarnings("unchecked")
    public static List<String> parseFreeTags(String json) {
        List<String> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            List<Object> raw = com.alibaba.fastjson.JSON.parseObject(json, List.class);
            if (raw == null) {
                return list;
            }
            for (Object item : raw) {
                if (item == null) {
                    continue;
                }
                String v = String.valueOf(item).trim();
                if (!v.isEmpty() && !list.contains(v)) {
                    list.add(v);
                }
            }
        } catch (Exception e) {
            return list;
        }
        return list;
    }

    /** 自由标签列表 → JSON 数组字符串 */
    public static String freeTagsToJson(List<String> tags) {
        com.alibaba.fastjson.JSONArray arr = new com.alibaba.fastjson.JSONArray();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null || tag.trim().isEmpty()) {
                    continue;
                }
                arr.add(tag.trim());
            }
        }
        return arr.toJSONString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
