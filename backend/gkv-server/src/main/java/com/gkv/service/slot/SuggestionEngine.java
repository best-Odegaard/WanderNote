package com.gkv.service.slot;

import com.gkv.vo.SlotStateVO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「猜你想问」引擎：每轮对话后给用户几个能点的追问。
 *
 * 为什么要它：用户聊完一轮常常"还想问点什么但组织不出来"，让他对着空白输入框硬想
 * 就是流失点。给 2-3 个具体的候选问题，点一下直接发出，对话就续上了。
 *
 * 与 {@link SlotEngine} 的分工（刻意分开，别混）：
 *   SlotEngine      收**参数**——目的地/天数/预算…每轮只问一个，答案落进槽位
 *   SuggestionEngine 给**话题**——有什么好吃、怎么去方便…每轮给几个，点了只是发一句话
 * 两者互不写入对方的状态：猜你想问不改变槽位，只影响下一轮 user_input。
 *
 * 纯规则、零大模型调用：要跟着每轮回复一起下发，不能额外增加一次模型往返。
 */
@Component
@Slf4j
public class SuggestionEngine {

    private static final String RULES_PATH = "slot/suggested-questions.yml";

    /** 模型候选追问块的标记（由 chat_prompt.txt 约定） */
    public static final String FOLLOWUP_OPEN = "<followups>";
    public static final String FOLLOWUP_CLOSE = "</followups>";

    /** 候选追问最多取几条 */
    private static final int MAX_MODEL_FOLLOWUPS = 3;

    /**
     * 从模型回复里解析出候选追问，并返回**剥离后的正文**。
     *
     * 为什么要剥离而不是直接透传：
     * 1) 正文才是给用户看的，标记块会被渲染成可点按钮；
     * 2) 剥离后的正文要入库，否则下一轮作为历史回传给模型时，
     *    模型会以为自己上一轮就该带这么一段，越滚越乱。
     */
    public static FollowUpParse extractFollowUps(String rawReply) {
        String raw = rawReply == null ? "" : rawReply;
        String lower = raw.toLowerCase();
        int open = lower.indexOf(FOLLOWUP_OPEN);
        if (open < 0) {
            // 模型没按格式给（或本轮不该给）：正文原样返回，候选交给规则兜底
            return new FollowUpParse(cleanTail(raw), Collections.emptyList());
        }

        String body = raw.substring(0, open);
        int close = lower.indexOf(FOLLOWUP_CLOSE, open + FOLLOWUP_OPEN.length());
        String block = close < 0
                // 少见情况：模型只写了开标记就断了。按"从开标记到结尾都是候选"处理，
                // 宁可少给候选，也不能让半截标记留在正文里给用户看到
                ? raw.substring(open + FOLLOWUP_OPEN.length())
                : raw.substring(open + FOLLOWUP_OPEN.length(), close);

        List<String> questions = new ArrayList<>();
        for (String line : block.split("\\r?\\n")) {
            String q = line.trim();
            // 顺手清掉模型偶尔加的项目符号
            q = q.replaceFirst("^[-*•·]\\s*", "").replaceFirst("^\\d+[.、)]\\s*", "").trim();
            if (q.isEmpty() || q.length() > 40) continue;
            if (questions.contains(q)) continue;
            questions.add(q);
            if (questions.size() >= MAX_MODEL_FOLLOWUPS) break;
        }

        // 正文里若还残留分隔线（模型习惯在标记块前加 ---），一并去掉
        return new FollowUpParse(cleanTail(body), questions);
    }

    /** 去掉正文结尾多余的空白与分隔线 */
    private static String cleanTail(String text) {
        if (text == null) return "";
        String s = text.replaceAll("(?m)^\\s*[-—=*]{3,}\\s*$", "");
        // 用 trim() 而不是 strip()：线上后端跑的是 Java 8，strip() 是 Java 11 才有的方法，
        // 编译期看不出问题（本地 JDK 17），运行期抛 NoSuchMethodError 把整条流式链路打断。
        return s.trim();
    }

    /** 解析结果：剥离后的正文 + 模型给出的候选追问（可能为空） */
    @Getter
    public static class FollowUpParse {
        private final String reply;
        private final List<String> questions;

        public FollowUpParse(String reply, List<String> questions) {
            this.reply = reply;
            this.questions = questions;
        }
    }
    /**
     * 合并「规则候选」与「模型候选」：**规则优先，模型只用来补足到上限**。
     *
     * 为什么规则优先（而不是最初那版模型优先）：
     *   1) 规则引擎是唯一知道当前槽位的组件 —— 它按 destination 给当地话题，
     *      还会避开"当前正在问的那个问题"；而模型的 <followups> 只被 prompt 要求
     *      「紧扣刚说的那条回复」，没有任何机制校验它与槽位一致；
     *   2) 模型看不到用户点选项的那些回合（槽位链路不调模型、也不落库），
     *      上下文天然滞后一轮，容易继续顺着更早聊过的城市出题 ——
     *      实测现象就是「已经选了成都，却还在推第一轮提过的大理/厦门」。
     *
     * 模型候选仍有价值（它紧扣本轮回复，通常比通用规则具体），
     * 所以在规则不足上限时按序补位，并做去重。
     *
     * @param ruleSuggestions 规则引擎给的候选，可为 null/空
     * @param modelFollowUps  模型 <followups> 给的候选，可为 null/空
     * @param maxItems        每轮候选上限
     */
    public static List<String> mergeSuggestions(List<String> ruleSuggestions,
                                                List<String> modelFollowUps,
                                                int maxItems) {
        List<String> merged = new ArrayList<>();
        appendDistinct(merged, ruleSuggestions, maxItems);
        appendDistinct(merged, modelFollowUps, maxItems);
        return merged;
    }

    /** 按序追加：跳过 null/空白、去掉重复、到上限即停 */
    private static void appendDistinct(List<String> target, List<String> source, int maxItems) {
        if (source == null) return;
        for (String q : source) {
            if (target.size() >= maxItems) return;
            if (q == null) continue;
            String text = q.trim();
            if (text.isEmpty() || target.contains(text)) continue;
            target.add(text);
        }
    }


    @Getter
    private int maxItems = 3;

    @Getter
    private int minItems = 2;

    /**
     * 每条规则最多贡献几条候选。
     *
     * 必须限制：不限制时第一条命中的规则会把名额占满，用户看到的全是同一个话题的
     * 几种说法（例如三条都在问"玩几天"），后面更具体的城市/同行人建议永远轮不到。
     */
    @Getter
    private int maxPerRule = 1;

    @Getter
    private List<SuggestionRule> rules = Collections.emptyList();

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void load() {
        try (InputStreamReader reader = new InputStreamReader(
                new ClassPathResource(RULES_PATH).getInputStream(), StandardCharsets.UTF_8)) {
            Map<String, Object> root = new Yaml().load(reader);
            if (root == null) {
                log.warn("[Suggest] {} 内容为空，猜你想问将不可用", RULES_PATH);
                return;
            }
            Object max = root.get("maxItems");
            if (max instanceof Number) {
                this.maxItems = ((Number) max).intValue();
            }
            Object min = root.get("minItems");
            if (min instanceof Number) {
                this.minItems = ((Number) min).intValue();
            }
            Object perRule = root.get("maxPerRule");
            if (perRule instanceof Number) {
                this.maxPerRule = Math.max(1, ((Number) perRule).intValue());
            }

            List<SuggestionRule> parsed = new ArrayList<>();
            Object rawRules = root.get("rules");
            if (rawRules instanceof List) {
                for (Object item : (List<Object>) rawRules) {
                    if (!(item instanceof Map)) continue;
                    SuggestionRule rule = parseRule((Map<String, Object>) item);
                    if (rule != null) parsed.add(rule);
                }
            }
            this.rules = Collections.unmodifiableList(parsed);
            log.info("[Suggest] 已加载 {} 条猜你想问规则，每轮最多 {} 条", parsed.size(), maxItems);
        } catch (Exception e) {
            // 降级：猜你想问只是锦上添花，加载失败不能影响对话
            log.error("[Suggest] 加载 {} 失败，猜你想问降级为不可用: {}", RULES_PATH, e.getMessage(), e);
        }
    }

    /**
     * 兼容重载：没有对话历史可数时，用槽位的 askedCount 当轮次。
     * 正常链路请用 {@link #suggest(SlotStateVO, int)} 并传入真实对话轮次。
     */
    public List<String> suggest(SlotStateVO state) {
        if (state == null) {
            return Collections.emptyList();
        }
        return suggest(state, state.getAskedCount() == null ? 0 : state.getAskedCount());
    }

    /**
     * 生成本轮的「猜你想问」。
     *
     * @param state 当前槽位快照（目的地、天数、同行人等都在里面）
     * @param round 对话轮次（用户说过几句话），用于轮换候选项。
     *              **必须传真实对话轮次，不能用 askedCount**：askedCount 只统计"槽位问题被回答了几次"，
     *              用户打字聊天（不点选项）时它一直是 0，候选就永远停在第一条 ——
     *              实测就是第 1、2 轮都冒出同一句「深圳有什么必吃？」。
     * @return 去重后的候选问题，最多 {@link #maxItems} 条；规则全部未命中时返回兜底池
     */
    public List<String> suggest(SlotStateVO state, int round) {
        if (state == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> slots = new LinkedHashMap<>();
        if (state.getSlots() != null) {
            state.getSlots().forEach((k, v) -> {
                if (v != null && v.getValue() != null && !v.getValue().trim().isEmpty()) {
                    slots.put(k, v.getValue().trim());
                }
            });
        }
        // 当前待回答的槽位问题文案：猜你想问要避开它，否则同一屏出现两个"看起来一样"的问题
        String pendingQuestion = state.getNextQuestion() == null ? null : state.getNextQuestion().getText();

        Set<String> picked = new LinkedHashSet<>();
        Set<String> ruleHit = new LinkedHashSet<>();
        String destination = slots.get("destination");
        for (SuggestionRule rule : rules) {
            if (picked.size() >= maxItems) break;
            if (!rule.matches(slots, round)) continue;

            List<String> candidates = rule.getQuestions();
            int size = candidates.size();
            if (size == 0) continue;
            // 按轮次轮换取起点。
            //
            // 为什么必须轮换：`maxPerRule=1` 时若每轮都从第 0 条取，像 city_generic 这种
            // 条件不变、一直命中的规则，会连着几轮推荐**同一句话**（实测就是这么被发现的：
            // 第 1 轮和第 2 轮都冒出「深圳有什么必吃？」），看着像"没更新"。
            // 用 round 取模轮换既避免了重复，又保持确定性 —— 不要用随机洗牌，
            // 那会让同一状态下每轮结果不同，测试和排查都没法复现。
            int offset = round % size;

            int fromThisRule = 0;
            for (int i = 0; i < size; i++) {
                if (picked.size() >= maxItems) break;
                if (fromThisRule >= maxPerRule) break;
                String q = candidates.get((offset + i) % size);
                if (q == null || q.trim().isEmpty()) continue;
                // {city} 占位符换成实际目的地：这样任何城市都能得到当地相关的问题，
                // 不必为每个城市手写一条规则
                String text = destination == null ? q.trim() : q.trim().replace("{city}", destination);
                if (pendingQuestion != null && text.equals(pendingQuestion.trim())) continue;
                if (picked.add(text)) {
                    fromThisRule++;
                    ruleHit.add(rule.getId());
                }
            }
        }
        log.debug("[Suggest] 命中规则 {}，给出 {} 条", ruleHit, picked.size());

        // 命中不足下限时不再硬凑：宁少给几条，也不重复或塞无关问题
        List<String> result = new ArrayList<>(picked);
        if (result.size() < minItems) {
            log.debug("[Suggest] 本轮仅命中 {} 条（下限 {}）", result.size(), minItems);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private SuggestionRule parseRule(Map<String, Object> raw) {
        SuggestionRule rule = new SuggestionRule();
        rule.setId(strOf(raw.get("id"), "rule"));

        Object city = raw.get("whenCity");
        if (city != null) {
            rule.setWhenCity(String.valueOf(city).trim());
        }

        if (Boolean.TRUE.equals(raw.get("whenDestinationKnown"))) {
            rule.setWhenDestinationKnown(true);
        }

        Object missing = raw.get("whenMissing");
        if (missing instanceof List) {
            List<String> keys = new ArrayList<>();
            for (Object k : (List<Object>) missing) {
                if (k != null) keys.add(String.valueOf(k).trim());
            }
            rule.setWhenMissing(keys);
        }

        Object eq = raw.get("whenSlotEquals");
        if (eq instanceof Map) {
            Map<String, String> map = new LinkedHashMap<>();
            ((Map<String, Object>) eq).forEach((k, v) -> {
                if (v != null) map.put(k, String.valueOf(v).trim());
            });
            rule.setWhenSlotEquals(map);
        }

        Object in = raw.get("whenSlotIn");
        if (in instanceof Map) {
            Map<String, List<String>> map = new LinkedHashMap<>();
            ((Map<String, Object>) in).forEach((k, v) -> {
                List<String> values = new ArrayList<>();
                if (v instanceof List) {
                    for (Object o : (List<Object>) v) {
                        if (o != null) values.add(String.valueOf(o).trim());
                    }
                }
                map.put(k, values);
            });
            rule.setWhenSlotIn(map);
        }

        Object round = raw.get("whenRoundAtLeast");
        if (round instanceof Number) {
            rule.setWhenRoundAtLeast(((Number) round).intValue());
        }

        List<String> questions = new ArrayList<>();
        Object rawQuestions = raw.get("questions");
        if (rawQuestions instanceof List) {
            for (Object q : (List<Object>) rawQuestions) {
                if (q != null && !String.valueOf(q).trim().isEmpty()) {
                    questions.add(String.valueOf(q).trim());
                }
            }
        }
        rule.setQuestions(questions);

        if (questions.isEmpty()) {
            log.warn("[Suggest] 跳过没有 questions 的规则: {}", rule.getId());
            return null;
        }
        return rule;
    }

    private static String strOf(Object v, String fallback) {
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fallback : s;
    }
}
