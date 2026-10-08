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

    /** 中文数字天数：「玩三天」这种说法 DAYS_PATTERN 认不出来 */
    private static final Pattern CN_DAYS_PATTERN = Pattern.compile("([一二三四五六七八九十])\\s*(天|日)");

    /**
     * 自由文本里的时间说法 → dateRange 选项值。
     *
     * 实测量级：用户被问「大概什么时候去？」时，很多人不点选项而是打「这个月月底去」。
     * 旧实现只认选项，这条输入既没被记录、也没被标记「已问过」，
     * 于是同一道题被无限重复问，chips 一直挂在屏幕上（用户反馈的原话就是这个）。
     */
    private static final String[][] DATE_RANGE_PHRASES = {
            {"这周末", "this_weekend"}, {"这个周末", "this_weekend"}, {"本周末", "this_weekend"},
            {"周末", "this_weekend"}, {"周六", "this_weekend"}, {"星期日", "this_weekend"},
            {"下周末", "this_weekend"}, {"下个周末", "this_weekend"},
            {"下周", "next_week"}, {"下个星期", "next_week"}, {"下礼拜", "next_week"},
            {"下个月", "next_month"}, {"下月", "next_month"}, {"月底", "next_month"},
            {"月末", "next_month"}, {"这个月", "next_month"}, {"本月", "next_month"},
            {"这个月月底", "next_month"}, {"月底去", "next_month"}, {"这个月月底去", "next_month"},
            {"还没定", "flexible"}, {"没定", "flexible"}, {"不确定", "flexible"},
            {"再说", "flexible"}, {"待定", "flexible"}, {"看情况", "flexible"},
            {"随便", "flexible"}, {"随时", "flexible"}, {"都行", "flexible"}
    };

    /**
     * 自由文本里「明确回答了某个槽位」的用词 → 选项值。
     *
     * 覆盖的是选项语义清楚的槽位。匹配分两种情形（见 inferFromText）：
     *   · 当前挂着的那道题：命中用词就写值；
     *   · 别的槽位：只有命中明确用词才写值，不做模糊判断。
     *
     * 目的地/出发地不在这里：那两个由城市词典识别（见 applyText），
     * 城市词典是唯一能分清「从广州去成都」的东西，再加一套关键词只会互相打架。
     */
    private static final Map<String, String[][]> SLOT_PHRASES = buildSlotPhrases();

    private static Map<String, String[][]> buildSlotPhrases() {
        Map<String, String[][]> map = new LinkedHashMap<>();
        map.put("dateRange", DATE_RANGE_PHRASES);
        map.put("hotelStyle", new String[][]{
                {"经济", "经济连锁"}, {"连锁", "经济连锁"}, {"快捷", "经济连锁"},
                {"舒适", "舒适型"}, {"中档", "舒适型"}, {"中等", "舒适型"},
                {"高端", "高端度假"}, {"度假", "高端度假"}, {"五星", "高端度假"},
                {"奢侈", "高端度假"}, {"豪华", "高端度假"},
                {"民宿", "民宿特色"}, {"特色", "民宿特色"}, {"客栈", "民宿特色"},
                {"不限", "不限"}, {"随便", "不限"}, {"都行", "不限"}
        });
        map.put("budget", new String[][]{
                {"穷游", "500"}, {"省着", "500"}, {"经济", "500"}, {"便宜", "500"}, {"500", "500"},
                {"一千", "800"}, {"1000", "800"},
                {"两千", "1500"}, {"1500", "1500"}, {"2000", "1500"},
                {"2500", "2500"}, {"不差钱", "2500"}, {"预算充足", "2500"},
                {"不限", "1500"}, {"随便", "1500"}, {"无所谓", "1500"}
        });
        map.put("companion", new String[][]{
                {"一个人", "solo"}, {"独自", "solo"}, {"自己", "solo"},
                {"两个人", "couple"}, {"双人", "couple"}, {"情侣", "couple"}, {"对象", "couple"},
                {"孩子", "family"}, {"亲子", "family"}, {"家人", "family"}, {"带娃", "family"},
                {"朋友", "friends"}, {"同事", "friends"}, {"同学", "friends"}
        });
        map.put("childAge", new String[][]{
                {"三岁", "0-3"}, {"3岁", "3-6"}, {"5岁", "3-6"}, {"8岁", "7-12"}, {"10岁", "7-12"},
                {"12岁", "12+"}, {"15岁", "12+"}
        });
        map.put("pace", new String[][]{
                {"轻松", "轻松"}, {"慢", "轻松"}, {"休闲", "轻松"}, {"不想爬山", "轻松"},
                {"常规", "常规"}, {"正常", "常规"},
                {"暴走", "暴走"}, {"多打卡", "暴走"}, {"赶", "暴走"}
        });
        return map;
    }

    /** 「你想去哪里？」里最多把几个对话提到过的城市提到最前 —— 多了等于没排 */
    private static final int MAX_MENTIONED_CITIES = 3;
    /**
     * 「从X」后面允许紧跟的字，用来判断 X 是不是出发地。
     *
     * 只收「出发/交通」类字眼（从上海出发 / 从北京走 / 从广州飞 / 从深圳自驾 / 从成都坐高铁…），
     * 刻意不放「的」这类字：像「从成都的熊猫基地开始玩」这种句子里的成都是地点不是出发地，
     * 认错比认不出来更糟 —— 认不出只是少填一个槽，认错会污染车票与首日路线。
     */
    private static final String DEPART_TAIL_CHARS = "出走飞起来去回坐乘搭到过开自驾高火赶转";

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
        } else {
            // 「玩三天」：中文数字也要认，否则用户打了完整答案却什么都没记下，
            // 下一轮还会被问「你想玩几天？」
            Matcher cn = CN_DAYS_PATTERN.matcher(text);
            if (cn.find()) {
                String days = chineseNumber(cn.group(1));
                if (days != null) {
                    putIfAbsent(state, "days", days, SOURCE_INFERRED);
                }
            }
        }

        // 先摘出发地：「从X出发」里的 X 和目的地用的是同一本城市词典，
        // 不先拿出来，下面按「第一个出现的城市」取目的地时就会把 X 当成目的地
        // （「我想从广州去成都」→ 目的地判成广州）。
        String departCity = departCityIn(text);
        if (departCity != null) {
            putIfAbsent(state, "departCity", departCity, SOURCE_INFERRED);
        }

        // 目的地用「识别词典」而不是「热门城市选项卡」：后者只有十几个城市，
        // 用户说"去深圳"时认不出来，后面依赖目的地的规则会整条失效。
        //
        // 按**在文本里出现的先后**取第一个，而不是按词典顺序：词典是「热门城市在前」
        // （广州、肇庆、重庆、成都…），用户说「我想从广州去成都」时会命中排在更前面的
        // 广州，目的地直接判错。所以这里遍历词典取「出现位置最小」的那个。
        int destIndex = Integer.MAX_VALUE;
        String destCity = null;
        for (String city : tree.getCityDictionary()) {
            int idx = text.indexOf(city);
            if (idx < 0) continue;
            // 「从上海出发」里的上海已经算出发地了，不能再当一次目的地
            if (city.equals(departCity)) continue;
            if (idx < destIndex) {
                destIndex = idx;
                destCity = city;
            }
        }
        if (destCity != null) {
            putIfAbsent(state, "destination", destCity, SOURCE_INFERRED);
        }

        // 自由文本也要能「答完一道题」：否则用户打字回答了当前问题（不是点选项），
        // 引擎里没有任何记录 —— pickNext 认为这题还没被问过，于是无限重复问同一道，
        // 前端那排 chips 就一直挂在屏幕上（用户实测反馈）。
        inferFromText(state, text);

        inferDerived(state);
        state.updatedAt = System.currentTimeMillis();
    }

    /**
     * 把自由文本当成「槽位问题的回答」。
     *
     * 两条分支：
     *   1) 文本里出现了某个槽位选项的明确说法（如 dateRange 的「月底」）→ 记录成 user 来源的值；
     *   2) 正在等这道题、且文本看着确实是在答（如日期题里的「五一前后吧」）→ 只标记「已问过」，
     *      保留用户原话交给模型去理解，既不丢信息、也不再重复追问。
     *
     * 为什么允许「答的不是当前挂着的那道题」：用户经常不理会在问的问题，直接把自己关心的
     * 事说了（刚被问目的地，回「和朋友一起去」）。旧实现因为「只认当前那道题」把这句话
     * 整条丢掉 —— 同行人没记下，后面还会再问一遍，用户看到的就是"我刚说了它又问"。
     *
     * 认得出明确说法才敢跨题记录：模糊判断（「就在这附近」）容易认错，
     * 宁可只认当前挂着的那道题，也不能瞎写值。
     */
    private void inferFromText(SlotState state, String text) {
        List<String> candidates = new ArrayList<>();
        String pending = state.lastPickedSlot;
        if (pending != null && !pending.isEmpty()) {
            candidates.add(pending);
        }
        // 其余还没填的槽位按提问优先级排（越该先问的越优先），保证一句话同时命中多个时取最该问的那个。
        //
        // 只收「用词明确」的槽位：这些槽位在 SLOT_PHRASES 里有定义，认得出就是认得，
        // 目的地/出发地不在此列 —— 那两个由城市词典识别（见 applyText），
        // 而城市词典是唯一能分清「从广州去成都」的东西，再加一套关键词只会互相打架。
        List<SlotDef> rest = new ArrayList<>();
        for (SlotDef def : tree.getDefs()) {
            if (def.getKey().equals(pending)) continue;
            if (!def.isAskable() || state.values.containsKey(def.getKey())) continue;
            if (state.asked.contains(def.getKey())) continue;
            if (!SLOT_PHRASES.containsKey(def.getKey())) continue;
            if (!dependsSatisfied(state, def)) continue;
            rest.add(def);
        }
        rest.sort(Comparator.comparingInt(SlotDef::getPriority).reversed());
        for (SlotDef def : rest) {
            candidates.add(def.getKey());
        }

        for (String slot : candidates) {
            // 已经答过 / 已经问过就不再动，避免覆盖用户点选的明确值
            if (state.values.containsKey(slot) || state.asked.contains(slot)) continue;
            SlotDef def = tree.get(slot);
            if (def == null || !def.isAskable()) continue;

            String[][] phrases = SLOT_PHRASES.get(slot);
            if (phrases == null) continue;
            String value = matchValue(text, phrases);
            if (value != null) {
                state.values.put(slot, new SlotValue(value, SOURCE_USER));
                state.asked.add(slot);
                log.info("[Slot] 自由文本已回答 {}={}（原话：{}）", slot, value, text);
                return;
            }
            // 只有当前挂着的那道题，才允许「认得出在答这题、但认不出具体值」这种模糊消耗；
            // 其它槽位认不出明确说法就当没提，否则会把随口一句话当成已经答过的题
            if (slot.equals(pending) && looksLikeAnswer(slot, text)) {
                state.asked.add(slot);
                log.info("[Slot] 自由文本视为已答 {}（原话：{}），不再重复追问", slot, text);
                return;
            }
        }
    }

    /** 在文本里按「出现位置最靠前、同位置取最长」挑一个说法，返回对应选项值；没命中返回 null */
    private static String matchValue(String text, String[][] phrases) {
        int bestIdx = Integer.MAX_VALUE;
        String bestValue = null;
        int bestLen = 0;
        for (String[] pair : phrases) {
            int idx = text.indexOf(pair[0]);
            if (idx < 0) continue;
            // 「这个周末」和「周末」都命中时取更长（更具体）的那个说法
            if (idx < bestIdx || (idx == bestIdx && pair[0].length() > bestLen)) {
                bestIdx = idx;
                bestLen = pair[0].length();
                bestValue = pair[1];
            }
        }
        return bestValue;
    }

    /**
     * 判断「用户就是在答这道题」。
     *
     * 这里的取舍很明确：宁可少问一题，也不要反复问同一题 ——
     * 认错只是这一题不再追问（用户的原话仍会进对话历史，模型看得见），
     * 认不出却会让同一排 chips 一直挂在屏上，用户以为自己的回答被吞了。
     *
     * 所以只要文本「看起来像在回答」就消耗掉问题，但**不写值**：
     * 值的判断必须有明确说法（上表），猜出来的值会污染行程。
     */
    private boolean looksLikeAnswer(String slot, String text) {
        if (text.length() < 2) {
            return false;
        }
        // 时间题：只要说得够具体就当他在定时间（「五一前后吧」这类没有"月/日"字面，
        // 但显然是在回答这题）；认不出具体值就连值都不写，只把问题消耗掉
        if ("dateRange".equals(slot)) {
            return text.length() >= 3
                    && !containsAny(text, "不知道", "不清楚", "不确定", "没想好", "你说了算");
        }
        // 其余槽位：一句话说得够具体（不是「嗯」「随便」这种），就当他是在回答这道题
        return text.length() >= 4 && !containsAny(text, "不知道", "不清楚", "不确定", "没想好", "你说了算");
    }

    private static boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 中文数字天数（一到十）；认不出来返回 null */
    private static String chineseNumber(String cn) {
        switch (cn) {
            case "一": return "1";
            case "二": return "2";
            case "三": return "3";
            case "四": return "4";
            case "五": return "5";
            case "六": return "6";
            case "七": return "7";
            case "八": return "8";
            case "九": return "9";
            case "十": return "10";
            default: return null;
        }
    }

    /**
     * 从「从X出发」这类句式里抽出出发城市；抽不到返回 null。
     *
     * 为什么必须单独识别：出发地和目的地共用同一本城市词典。只按「第一个出现的城市」
     * 取，用户说「从广州去成都」时广州会先被当成目的地，后面的车票与首日路线全错。
     *
     * 为什么还要校验 X 后面跟的字：「从成都的熊猫基地开始玩」里的成都是地点不是出发地。
     * 认错比认不出来更糟 —— 认不出只是少填一个槽，用户还能点选项补上；
     * 认错会直接污染车票与首日路线，用户还看不出是哪儿错了。
     */
    private String departCityIn(String text) {
        int from = text.indexOf('从');
        while (from >= 0) {
            int p = from + 1;
            while (p < text.length() && Character.isWhitespace(text.charAt(p))) {
                p++;
            }
            String city = longestCityAt(text, p);
            if (city != null) {
                int next = p + city.length();
                // 「从上海市出发」：先跳过行政区后缀再判断后面的字
                if (next < text.length() && text.charAt(next) == '市') {
                    next++;
                }
                // 句尾直接结束也算（「我打算从上海」）
                if (next >= text.length()
                        || Character.isWhitespace(text.charAt(next))
                        || DEPART_TAIL_CHARS.indexOf(text.charAt(next)) >= 0) {
                    return city;
                }
            }
            from = text.indexOf('从', from + 1);
        }
        return null;
    }

    /**
     * 取 pos 位置开始能匹配到的最长城市名。
     *
     * 取最长而不是命中即返回：词典里有「潮州/潮汕」这类前缀相同的城市，
     * 命中即返回会随词典顺序取到较短的那个。
     */
    private String longestCityAt(String text, int pos) {
        String best = null;
        for (String city : tree.getCityDictionary()) {
            if (!text.startsWith(city, pos)) continue;
            if (best == null || city.length() > best.length()) {
                best = city;
            }
        }
        return best;
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
        // 记下这道题正挂着：用户下一轮用自由文本回答时，引擎要知道他答的是哪道题
        state.lastPickedSlot = def.getKey();
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

        /**
         * 最近一次下发给用户的问题槽位。
         *
         * 用户很可能不打字点选项，而是直接回一句「这个月月底去」——
         * 不知道他答的是哪道题，就没法把这句话记成答案，
         * 结果是同一道题被反复问、前端 chips 一直挂着。
         */
        volatile String lastPickedSlot;

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
