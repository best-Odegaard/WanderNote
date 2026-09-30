package com.gkv.service.slot;

import com.gkv.dto.SlotAnswerDTO;
import com.gkv.vo.SlotOptionVO;
import com.gkv.vo.SlotQuestionVO;
import com.gkv.vo.SlotReadyVO;
import com.gkv.vo.SlotStateVO;
import com.gkv.vo.SlotValueVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 槽位引擎 —— 「让用户不需要想问题」的核心。
 *
 * 三条设计约束：
 * 1) 纯规则、零大模型调用。点一下选项就该在 100ms 内出下一个问题，
 *    走 LLM 只会带来延迟和不确定性。自由文本才走 /travel/chat。
 * 2) 每轮只问一个问题，最多 maxAskedCount 轮，随时可跳过、可提前生成。
 * 3) 能推断的不问（如 people 由 companion 推出），已问过的不再问，依赖不满足的不问。
 *
 * 状态存在进程内 Map：会话级、临时、可丢失（丢了用户重新答一次即可）。
 * 后续需要跨端恢复时再落库（trip_slot_state），当前不引入 DB 依赖。
 */
@Service
@Slf4j
public class SlotEngine {

    public static final String SOURCE_USER = "user";
    public static final String SOURCE_INFERRED = "inferred";
    public static final String SOURCE_DEFAULT = "default";

    /** 完整度阈值：达到后才能选酒店 / 生成完整路线 */
    private static final BigDecimal HOTEL_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal ROUTE_THRESHOLD = new BigDecimal("0.60");

    /** 进程内会话上限，防止长期运行内存无限增长 */
    private static final int MAX_SESSIONS = 5000;

    /** 从自由文本里识别天数的模式：支持「3天」「三天」这类常见说法 */
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s*(天|日)");

    /** 「你想去哪里？」里最多把几个对话提到过的城市提到最前 —— 多了等于没排 */
    private static final int MAX_MENTIONED_CITIES = 3;

    private final QuestionTree tree;
    private final Map<String, SlotState> store = new ConcurrentHashMap<>();

    public SlotEngine(QuestionTree tree) {
        this.tree = tree;
    }

    // ===================== 对外能力 =====================

    /** 记录一次回答（点选项 / 跳过），返回最新快照 */
    public SlotStateVO answer(SlotAnswerDTO req) {
        SlotState state = getOrCreate(req.getSessionId());
        String slot = req.getSlot();
        if (slot == null || slot.trim().isEmpty()) {
            return snapshot(req.getSessionId());
        }
        slot = slot.trim();
        state.asked.add(slot);

        if (Boolean.TRUE.equals(req.getSkipped())) {
            SlotDef def = tree.get(slot);
            if (def != null && def.getDefaultValue() != null) {
                state.values.put(slot, new SlotValue(def.getDefaultValue(), SOURCE_DEFAULT));
            }
            log.info("[Slot] session={} 跳过 {}（默认值={}）", req.getSessionId(), slot,
                    def == null ? null : def.getDefaultValue());
        } else {
            String value = req.getValue() == null ? "" : req.getValue().trim();
            if (!value.isEmpty()) {
                state.values.put(slot, new SlotValue(value, SOURCE_USER));
                log.info("[Slot] session={} 回答 {}={}", req.getSessionId(), slot, value);
            }
        }

        inferDerived(state);
        state.updatedAt = System.currentTimeMillis();
        return snapshot(req.getSessionId());
    }

    /**
     * 从用户的自由文本里做廉价抽取（正则 + 词典），补上能确定的槽位。
     * 只填空缺，不覆盖用户已经明确给过的值。
     */
    public void applyText(String sessionId, String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) return;
        SlotState state = getOrCreate(sessionId);
        String text = userInput.trim();

        Matcher m = DAYS_PATTERN.matcher(text);
        if (m.find()) {
            putIfAbsent(state, "days", m.group(1), SOURCE_INFERRED);
        }

        // 用「识别词典」而不是「热门城市选项卡」：后者只有十几个城市，
        // 用户说"去深圳"时认不出来，后面依赖目的地的规则会整条失效。
        for (String city : tree.getCityDictionary()) {
            if (text.contains(city)) {
                putIfAbsent(state, "destination", city, SOURCE_INFERRED);
                break;
            }
        }

        inferDerived(state);
        state.updatedAt = System.currentTimeMillis();
    }

    /** 生成完整度 / 就绪标记 / 下一个问题 */
    public SlotStateVO snapshot(String sessionId) {
        SlotState state = getOrCreate(sessionId);
        SlotStateVO vo = new SlotStateVO();
        vo.setSessionId(sessionId);

        Map<String, SlotValueVO> slots = new LinkedHashMap<>();
        state.values.forEach((k, v) -> slots.put(k, new SlotValueVO(v.value, v.source)));
        vo.setSlots(slots);
        vo.setAskedCount(state.asked.size());

        BigDecimal completeness = completeness(state);
        vo.setCompleteness(completeness);
        vo.setReady(ready(state, completeness));
        vo.setNextQuestion(pickNext(state));
        return vo;
    }

    /**
     * 把已确认的槽位拼成一段文本注入 prompt。
     * 目的：模型不要再追问用户已经答过的信息（否则用户会觉得"我不是刚说过吗"）。
     */
    public String buildInjectNote(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return "";
        SlotState state = store.get(sessionId);
        if (state == null || state.values.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("已通过选项确认的需求（请勿重复追问）：");
        for (Map.Entry<String, SlotValue> e : state.values.entrySet()) {
            SlotDef def = tree.get(e.getKey());
            String label = def != null && def.getQuestion() != null ? def.getQuestion() : e.getKey();
            sb.append(label.replace("？", "").replace("?", ""))
                    .append('=')
                    .append(e.getValue().value);
            if (SOURCE_DEFAULT.equals(e.getValue().source)) {
                sb.append("（用户跳过，用的默认值，可建议修改）");
            } else if (SOURCE_INFERRED.equals(e.getValue().source)) {
                sb.append("（系统推断）");
            }
            sb.append("；");
        }
        return sb.toString();
    }

    /** 会话结束时清理（当前未接线，留给后续登出/新建行程时调用） */
    public void clear(String sessionId) {
        if (sessionId != null) store.remove(sessionId);
    }

    // ===================== 内部逻辑 =====================

    private SlotState getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            // 无会话 id 时给一个不落 Map 的临时态，避免 null key
            return new SlotState();
        }
        evictIfNeeded();
        return store.computeIfAbsent(sessionId, k -> new SlotState());
    }

    private void evictIfNeeded() {
        if (store.size() < MAX_SESSIONS) return;
        store.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().updatedAt))
                .limit(Math.max(1, MAX_SESSIONS / 10))
                .map(Map.Entry::getKey)
                .forEach(store::remove);
        log.info("[Slot] 会话数超过 {}，已清理最旧的 10%", MAX_SESSIONS);
    }

    private void putIfAbsent(SlotState state, String slot, String value, String source) {
        if (value == null || value.isEmpty()) return;
        if (state.values.containsKey(slot)) return;
        state.values.put(slot, new SlotValue(value, source));
    }

    /** 推断派生槽位：目前只做 people ← companion */
    private void inferDerived(SlotState state) {
        if (state.values.containsKey("people")) return;
        SlotValue companion = state.values.get("companion");
        if (companion == null || companion.value == null) return;
        String people;
        switch (companion.value) {
            case "solo":
                people = "1";
                break;
            case "couple":
                people = "2";
                break;
            case "family":
                people = "3";
                break;
            case "friends":
                people = "2";
                break;
            default:
                return;
        }
        state.values.put("people", new SlotValue(people, SOURCE_INFERRED));
    }

    private BigDecimal completeness(SlotState state) {
        int total = tree.totalWeight();
        if (total <= 0) return BigDecimal.ZERO;
        int filled = 0;
        for (SlotDef def : tree.getDefs()) {
            if (def.getWeight() > 0 && state.values.containsKey(def.getKey())) {
                filled += def.getWeight();
            }
        }
        return BigDecimal.valueOf(filled)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private SlotReadyVO ready(SlotState state, BigDecimal completeness) {
        SlotReadyVO ready = new SlotReadyVO();
        boolean hasDestination = state.values.containsKey("destination");
        boolean hasDays = state.values.containsKey("days");
        boolean hasHotelStyle = state.values.containsKey("hotelStyle");
        boolean hasDepartCity = state.values.containsKey("departCity");

        ready.setFrame(hasDestination && hasDays);
        ready.setHotel(completeness.compareTo(HOTEL_THRESHOLD) >= 0 && hasHotelStyle);
        ready.setTransport(completeness.compareTo(ROUTE_THRESHOLD) >= 0 && hasDepartCity);
        ready.setFullRoute(completeness.compareTo(ROUTE_THRESHOLD) >= 0 && hasHotelStyle);
        return ready;
    }

    private SlotQuestionVO pickNext(SlotState state) {
        if (state.asked.size() >= tree.getMaxAskedCount()) return null;

        List<SlotDef> candidates = new ArrayList<>();
        for (SlotDef def : tree.getDefs()) {
            if (!def.isAskable()) continue;
            if (state.values.containsKey(def.getKey())) continue;
            if (state.asked.contains(def.getKey())) continue;
            if (!dependsSatisfied(state, def)) continue;
            candidates.add(def);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingInt(SlotDef::getPriority).reversed());

        SlotDef def = candidates.get(0);
        SlotQuestionVO q = new SlotQuestionVO();
        q.setSlot(def.getKey());
        q.setText(def.getQuestion());
        q.setHint(def.getHint());
        q.setAllowSkip(def.isSkippable());
        q.setSkipLabel(def.getSkipLabel());

        if (def.isUseHotCities()) {
            List<SlotOptionVO> options = new ArrayList<>();
            // 先把"这一轮 AI 刚提到过的城市"排前面。
            //
            // 实测反馈的场景：AI 推荐了成都/厦门/西安，紧接着问「你想去哪里？」，
            // 选项却是固定的 广州/肇庆/重庆/成都/… —— 用户要在这堆里自己找那三个，
            // 对话上下文直接断了。
            for (String city : state.mentionedCities) {
                if (options.stream().noneMatch(o -> city.equals(o.getValue()))) {
                    options.add(new SlotOptionVO(city, city));
                }
            }
            for (String city : tree.getHotCities()) {
                if (options.stream().noneMatch(o -> city.equals(o.getValue()))) {
                    options.add(new SlotOptionVO(city, city));
                }
            }
            q.setOptions(options);
        } else if (def.getOptions() != null && !def.getOptions().isEmpty()) {
            q.setOptions(def.getOptions());
        } else {
            // 无选项 → 前端退化为让用户自由输入
            q.setOptions(null);
        }
        return q;
    }

    private boolean dependsSatisfied(SlotState state, SlotDef def) {
        if (def.getDependsOn() == null || def.getDependsOn().isEmpty()) return true;
        for (Map.Entry<String, List<String>> e : def.getDependsOn().entrySet()) {
            SlotValue dep = state.values.get(e.getKey());
            if (dep == null || dep.value == null) return false;
            if (e.getValue() != null && !e.getValue().isEmpty() && !e.getValue().contains(dep.value)) {
                return false;
            }
        }
        return true;
    }

    // ===================== 对话里提到的城市（用于选项排序） =====================

    /**
     * 按出现顺序抽出文本里提到的城市，最多 {@value #MAX_MENTIONED_CITIES} 个。
     *
     * 用**识别词典**而不是热门选项卡：AI 可能推荐大理、泉州、景德镇这类不在选项卡里的城市，
     * 它们同样应该被排到选项前面（否则用户看到推荐却点不到）。
     */
    public List<String> extractCityMentions(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> hits = new ArrayList<>();
        for (int i = 0; i < text.length() && hits.size() < MAX_MENTIONED_CITIES; i++) {
            for (String city : tree.getCityDictionary()) {
                if (text.startsWith(city, i) && !hits.contains(city)) {
                    hits.add(city);
                    // 同一个位置只认一个城市，避免"上海"和"上海"之类的重复扫描
                    break;
                }
            }
        }
        return hits;
    }

    /**
     * 记录本轮 AI 回复里提到的城市，用于给「你想去哪里？」的选项排序。
     *
     * 每轮**覆盖**（不累积）：排序要跟着最新一轮的回复走，
     * 否则会一直停在很早以前提过的城市上。
     */
    public void noteCityMentions(String sessionId, List<String> cities) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        SlotState state = store.computeIfAbsent(sessionId, k -> new SlotState());
        // 不用 List.copyOf：那是 Java 10+ 的 API，线上后端只有 Java 8，会运行期报 NoSuchMethodError
        state.mentionedCities = (cities == null || cities.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(cities));
    }

    // ===================== 内部状态 =====================

    private static class SlotState {
        final Map<String, SlotValue> values = new LinkedHashMap<>();
        final Set<String> asked = new HashSet<>();

        /**
         * 最近一轮 AI 回复里提到的城市（按出现顺序）。
         *
         * 用途：给「你想去哪里？」的选项排序 —— AI 刚推荐了成都/厦门/西安，
         * 紧接着问用户想去哪，这三个当然应该排在最前面，而不是让用户在一堆
         * 与刚才那段话无关的城市里自己找。
         * 每轮覆盖（不累积）：排序要跟着最新一轮的回复走，否则会一直停在很早以前提过的城市。
         */
        volatile List<String> mentionedCities = Collections.emptyList();

        volatile long updatedAt = System.currentTimeMillis();
    }

    private static class SlotValue {
        final String value;
        final String source;

        SlotValue(String value, String source) {
            this.value = value;
            this.source = source;
        }
    }
}
