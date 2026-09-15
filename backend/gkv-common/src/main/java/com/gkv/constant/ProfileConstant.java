package com.gkv.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * AI 用户画像受控词表与关键词映射表
 *
 * 这是整个画像功能"确定性"的来源，所以刻意把它做成一个独立常量模块，
 * 不散落在 service 里 —— 规则累加、模型输出校验、管理端下拉、前端标签
 * 四处引用的必须是同一份定义，任何一处另创一套都会让权重语义悄悄漂移。
 *
 * 维护关键词时注意（踩过的坑）：
 *   1. 关键词不要太短：2 个汉字起，否则"山"会命中"山东""山城"。
 *   2. 同一组内的关键词之间不能有包含关系（"预算充足"同时属于两个档位会让档位判断抖动）。
 *      例如「悠闲」组只允许出现"放松"，不能再出现"深度放松"。
 *   3. 不同单值维度之间也不能互相包含：预算档位里曾经写"适中"、节奏里有"适中节奏"，
 *      一句"适中节奏"会同时命中两个维度，档位跟着节奏一起抖。
 *      所以预算关键词一律带"预算"语境（预算适中 / 中等预算 / 预算一般）。
 *      ProfileSelfCheck 里有一组断言把这个不变量钉住了，改词表时请一起跑。
 *   4. 跨组包含只能靠声明顺序 + 首次命中即停来解决：
 *      "女朋友"同时命中「情侣」和「朋友」，靠 COMPANION_KEYWORDS 的声明顺序
 *      （情侣在朋友之前）保证判成情侣。
 */
public final class ProfileConstant {

    private ProfileConstant() {
    }

    /* ==================== 受控偏好标签（9 个，与前端 TRAVEL_PREFERENCES 一致） ==================== */

    /** 受控标签全集，顺序即"同权重时的稳定排序顺序" */
    public static final List<String> TAGS;

    /** 标签 → 命中关键词 */
    public static final Map<String, List<String>> TAG_KEYWORDS;

    /** 前端旧短标签/常见变体 → 受控标签（前端历史数据用的是「美食」「摄影」这类短标签） */
    public static final Map<String, String> TAG_ALIASES;

    static {
        List<String> tags = new ArrayList<>(Arrays.asList(
                "经典必玩", "吃吃喝喝", "小众探索", "拍照出片", "citywalk",
                "自然风光", "历史古建", "逛街购物", "文艺展览"));
        TAGS = Collections.unmodifiableList(tags);

        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put("经典必玩", Arrays.asList("必玩", "必去", "经典景点", "精华游", "招牌景点", "网红景点", "地标建筑", "第一次去", "首次到访"));
        keywords.put("吃吃喝喝", Arrays.asList("美食", "小吃", "探店", "餐厅", "夜市", "火锅", "特色菜", "甜品", "咖啡", "好吃的"));
        keywords.put("小众探索", Arrays.asList("小众", "人少", "冷门", "秘境", "避开人流", "清静", "隐秘去处", "本地人才知道"));
        keywords.put("拍照出片", Arrays.asList("拍照", "出片", "摄影", "机位", "写真", "好拍", "大片感", "滤镜"));
        keywords.put("citywalk", Arrays.asList("citywalk", "city walk", "城市漫步", "压马路", "闲逛", "漫步街区", "逛老街", "边走边逛"));
        keywords.put("自然风光", Arrays.asList("自然风光", "山水", "爬山", "徒步", "湖泊", "海边", "森林", "公园", "瀑布", "日出"));
        keywords.put("历史古建", Arrays.asList("历史", "古建", "古迹", "古镇", "博物馆", "寺庙", "老建筑", "城墙", "文化底蕴"));
        keywords.put("逛街购物", Arrays.asList("购物", "逛街", "商场", "奥特莱斯", "免税店", "买手店", "集市", "特产店", "百货"));
        keywords.put("文艺展览", Arrays.asList("展览", "美术馆", "艺术", "文艺", "书店", "话剧", "音乐节", "文创", "看展"));
        TAG_KEYWORDS = Collections.unmodifiableMap(keywords);

        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("美食", "吃吃喝喝");
        aliases.put("美食探店", "吃吃喝喝");
        aliases.put("摄影", "拍照出片");
        aliases.put("历史文化", "历史古建");
        aliases.put("文化历史", "历史古建");
        aliases.put("购物娱乐", "逛街购物");
        aliases.put("自然景观", "自然风光");
        TAG_ALIASES = Collections.unmodifiableMap(aliases);
    }

    /* ==================== 预算档位（与问卷档位同一套语义） ==================== */

    public static final String BUDGET_ECONOMY = "经济";
    public static final String BUDGET_MODERATE = "适中";
    public static final String BUDGET_HIGH = "高档";

    /** 经济档上限（含） */
    public static final int BUDGET_ECONOMY_MAX = 500;
    /** 适中档上限（含），超过即高档 */
    public static final int BUDGET_MODERATE_MAX = 2000;

    public static final List<String> BUDGET_LEVELS =
            Collections.unmodifiableList(Arrays.asList(BUDGET_ECONOMY, BUDGET_MODERATE, BUDGET_HIGH));

    public static final Map<String, List<String>> BUDGET_KEYWORDS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // 关键词一律带上"预算"语境：不能只写"适中""常规"，
        // 否则会命中"适中节奏""常规"这些节奏维度的词，让档位判断跟着节奏抖。
        m.put(BUDGET_ECONOMY, Arrays.asList("穷游", "经济实惠", "便宜", "省钱", "学生党", "低预算", "平价", "预算不多"));
        m.put(BUDGET_MODERATE, Arrays.asList("预算适中", "舒适", "品质", "中等预算", "预算一般", "预算正常"));
        // "预算充足" 只放在高档：同一关键词出现在两个档位会让档位判断不稳定
        m.put(BUDGET_HIGH, Arrays.asList("豪华", "高端", "高档", "奢华", "五星", "不差钱", "预算充足", "顶级"));
        BUDGET_KEYWORDS = Collections.unmodifiableMap(m);
    }

    /* ==================== 出行节奏 ==================== */

    public static final List<String> PACE_LEVELS =
            Collections.unmodifiableList(Arrays.asList("悠闲", "常规", "暴走"));

    public static final Map<String, List<String>> PACE_KEYWORDS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // 只保留"放松"，不再列"深度放松"：同组内关键词互相包含会让命中数量失真
        m.put("悠闲", Arrays.asList("悠闲", "慢节奏", "轻松", "不赶时间", "放松", "休闲", "少走", "一天一个点", "慢慢逛"));
        m.put("常规", Arrays.asList("常规", "中等节奏", "正常节奏", "适中节奏", "一半打卡"));
        m.put("暴走", Arrays.asList("暴走", "快节奏", "多走多看", "特种兵", "高强度", "一天多个点", "走得多", "尽量多逛"));
        PACE_KEYWORDS = Collections.unmodifiableMap(m);
    }

    /* ==================== 同行人（顺序即"首次命中即停"的优先级） ==================== */

    public static final List<String> COMPANION_LEVELS =
            Collections.unmodifiableList(Arrays.asList("独自", "情侣", "朋友", "家庭"));

    public static final Map<String, List<String>> COMPANION_KEYWORDS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("独自", Arrays.asList("一个人", "独自", "单人", "solo", "自己去", "独行"));
        // 必须排在「朋友」之前："女朋友"/"男朋友" 同时包含"朋友"
        m.put("情侣", Arrays.asList("情侣", "女朋友", "男朋友", "夫妻", "二人世界", "约会", "蜜月", "老婆", "老公"));
        m.put("朋友", Arrays.asList("朋友", "同学", "同事", "结伴", "闺蜜", "兄弟"));
        m.put("家庭", Arrays.asList("家庭", "亲子", "带孩子", "带娃", "父母", "老人", "一家三口", "小朋友", "全家"));
        COMPANION_KEYWORDS = Collections.unmodifiableMap(m);
    }

    /* ==================== 硬性约束 ==================== */

    /** 约束分隔符（中文分号）。解析时同时兼容英文分号，写入统一用中文分号 */
    public static final String CONSTRAINT_SEPARATOR = "；";

    /** 约束解析用的分隔正则（兼容中英文分号） */
    public static final String CONSTRAINT_SPLIT_REGEX = "[;；]";

    /** 约束规范名 → 命中关键词 */
    public static final Map<String, List<String>> CONSTRAINT_KEYWORDS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("不吃辣", Arrays.asList("不吃辣", "忌辣", "不能吃辣", "一点辣都不行", "怕辣"));
        m.put("有食物忌口", Arrays.asList("忌口", "不吃海鲜", "不吃牛肉", "不吃猪肉", "不吃羊肉", "素食", "忌食"));
        m.put("有食物过敏", Collections.singletonList("过敏"));
        m.put("不能爬山", Arrays.asList("不爬山", "不想爬山", "不能爬山", "爬不动山"));
        m.put("腿脚不便", Arrays.asList("腿脚不便", "腿脚不好", "走路不便", "行动不便"));
        m.put("需要无障碍设施", Arrays.asList("无障碍", "轮椅"));
        m.put("带老人同行", Arrays.asList("带老人", "和老人", "老人同行", "带父母", "陪父母"));
        m.put("带小孩同行", Arrays.asList("带小孩", "带孩子", "带娃", "带小朋友"));
        m.put("带宠物同行", Arrays.asList("带宠物", "带狗", "带猫", "宠物同行"));
        m.put("容易晕车", Collections.singletonList("晕车"));
        m.put("恐高", Collections.singletonList("恐高"));
        m.put("不饮酒", Arrays.asList("不喝酒", "忌酒", "不饮酒"));
        CONSTRAINT_KEYWORDS = Collections.unmodifiableMap(m);
    }

    /* ==================== 工具方法 ==================== */

    /** 是否为受控标签（供模型输出校验与人工编辑校验） */
    public static boolean isControlledTag(String tag) {
        return tag != null && TAGS.contains(tag.trim());
    }

    /**
     * 把任意写法归一化成受控标签：
     * 先精确匹配受控标签，再走别名表（大小写不敏感，"CityWalk" 也能命中 "citywalk"）。
     * 归一化不了返回 null。
     */
    public static String normalizeTag(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (TAGS.contains(v)) {
            return v;
        }
        for (String tag : TAGS) {
            if (tag.equalsIgnoreCase(v)) {
                return tag;
            }
        }
        for (Map.Entry<String, String> e : TAG_ALIASES.entrySet()) {
            if (e.getKey().equalsIgnoreCase(v)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 关键词是否命中文本（大小写不敏感） */
    public static boolean hits(String text, String keyword) {
        return text != null && keyword != null
                && text.toLowerCase().contains(keyword.toLowerCase());
    }

    /** 文本命中的全部受控标签（按词表顺序，天然去重） */
    public static LinkedHashSet<String> matchTags(String text) {
        LinkedHashSet<String> hit = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return hit;
        }
        for (Map.Entry<String, List<String>> e : TAG_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (hits(text, kw)) {
                    hit.add(e.getKey());
                    break;
                }
            }
        }
        return hit;
    }

    /**
     * 文本命中的单值维度取值（预算/节奏/同行人）
     *
     * 声明顺序 + 首次命中即停：跨组包含（"女朋友" 同时含"朋友"）时判成先声明的那个，
     * 避免一条文本同时落到两个取值上让字段抖动。
     */
    public static String matchSingleValue(String text, Map<String, List<String>> dictionary) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> e : dictionary.entrySet()) {
            for (String kw : e.getValue()) {
                if (hits(text, kw)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    /** 文本命中的硬性约束（规范名，按声明顺序去重） */
    public static LinkedHashSet<String> matchConstraints(String text) {
        LinkedHashSet<String> hit = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return hit;
        }
        for (Map.Entry<String, List<String>> e : CONSTRAINT_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (hits(text, kw)) {
                    hit.add(e.getKey());
                    break;
                }
            }
        }
        return hit;
    }

    /** 预算金额 → 档位。<=500 经济 / <=2000 适中 / >2000 高档 */
    public static String budgetLevelOf(double amount) {
        if (amount <= BUDGET_ECONOMY_MAX) {
            return BUDGET_ECONOMY;
        }
        if (amount <= BUDGET_MODERATE_MAX) {
            return BUDGET_MODERATE;
        }
        return BUDGET_HIGH;
    }

    /** 取值是否在给定维度的词表内（供模型输出校验与人工编辑校验） */
    public static boolean isValueIn(List<String> levels, String value) {
        return value != null && levels.contains(value.trim());
    }
}
