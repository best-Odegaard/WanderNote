package com.gkv.service.slot;

import com.gkv.vo.SlotOptionVO;
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

/**
 * 槽位问题树：从 classpath:slot/question-tree.yml 加载，进程内缓存。
 *
 * 为什么放配置文件而不是硬编码：
 *   · 问题文案 / 选项 / 提问顺序是运营与产品会反复调的东西，不该每次都改代码发版；
 *   · 加载失败不影响主链路（对话照常），只是没有 chips 可点，因此这里全部降级处理。
 */
@Component
@Slf4j
public class QuestionTree {

    private static final String TREE_PATH = "slot/question-tree.yml";

    @Getter
    private List<SlotDef> defs = Collections.emptyList();

    @Getter
    private List<String> hotCities = Collections.emptyList();

    /**
     * 从自由文本里识别城市的词典。
     *
     * 与 hotCities 分开：hotCities 要少（是给用户挑的选项卡），这个要多（是识别
     * "我想去深圳"这类句子的词典）。没配时回落成 hotCities，行为与旧版一致。
     */
    @Getter
    private List<String> cityDictionary = Collections.emptyList();

    @Getter
    private int maxAskedCount = 6;

    private Map<String, SlotDef> byKey = Collections.emptyMap();

    public SlotDef get(String key) {
        return key == null ? null : byKey.get(key);
    }

    /** 参与完整度计算的总权重 */
    public int totalWeight() {
        int sum = 0;
        for (SlotDef def : defs) {
            sum += Math.max(0, def.getWeight());
        }
        return sum;
    }

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void load() {
        try (InputStreamReader reader = new InputStreamReader(
                new ClassPathResource(TREE_PATH).getInputStream(), StandardCharsets.UTF_8)) {

            Map<String, Object> root = new Yaml().load(reader);
            if (root == null) {
                log.warn("[SlotTree] {} 内容为空，槽位问答将不可用", TREE_PATH);
                return;
            }

            Object maxAsked = root.get("maxAskedCount");
            if (maxAsked instanceof Number) {
                this.maxAskedCount = ((Number) maxAsked).intValue();
            }

            List<String> cities = new ArrayList<>();
            Object rawCities = root.get("hotCities");
            if (rawCities instanceof List) {
                for (Object c : (List<Object>) rawCities) {
                    if (c != null) cities.add(String.valueOf(c).trim());
                }
            }
            this.hotCities = Collections.unmodifiableList(cities);

            // 识别词典 = 热门城市 ∪ 额外城市。
            //
            // 必须是「并集」而不是「替换」：cityDictionary 里写的是**额外**城市，
            // 如果直接替换，热门城市（肇庆/广州…）就会从词典里消失，
            // 用户说"想去肇庆"反而识别不出来 —— 加了词典却把原有能力弄丢。
            // 而且每新增一个热门城市都得记得同时写两处，正是要避免的那类坑。
            List<String> extras = new ArrayList<>();
            Object rawDict = root.get("cityDictionary");
            if (rawDict instanceof List) {
                for (Object c : (List<Object>) rawDict) {
                    if (c != null) extras.add(String.valueOf(c).trim());
                }
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>(cities);
            merged.addAll(extras);
            this.cityDictionary = Collections.unmodifiableList(new ArrayList<>(merged));

            List<SlotDef> parsed = new ArrayList<>();
            Map<String, SlotDef> index = new LinkedHashMap<>();
            Object rawSlots = root.get("slots");
            if (rawSlots instanceof List) {
                for (Object item : (List<Object>) rawSlots) {
                    if (!(item instanceof Map)) continue;
                    SlotDef def = parseSlot((Map<String, Object>) item);
                    if (def == null) continue;
                    parsed.add(def);
                    index.put(def.getKey(), def);
                }
            }
            this.defs = Collections.unmodifiableList(parsed);
            this.byKey = Collections.unmodifiableMap(index);
            log.info("[SlotTree] 已加载 {} 个槽位，总权重={}，最大提问轮次={}，识别词典 {} 个城市",
                    parsed.size(), totalWeight(), maxAskedCount, cityDictionary.size());
        } catch (Exception e) {
            // 降级：对话链路不依赖问题树，加载失败只是没有 chips
            log.error("[SlotTree] 加载 {} 失败，槽位问答降级为不可用: {}", TREE_PATH, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private SlotDef parseSlot(Map<String, Object> raw) {
        Object keyObj = raw.get("key");
        if (keyObj == null || String.valueOf(keyObj).trim().isEmpty()) {
            log.warn("[SlotTree] 跳过缺少 key 的槽位定义: {}", raw);
            return null;
        }
        SlotDef def = new SlotDef();
        def.setKey(String.valueOf(keyObj).trim());
        def.setWeight(intOf(raw.get("weight"), 0));
        def.setPriority(intOf(raw.get("priority"), def.getWeight()));
        def.setQuestion(strOf(raw.get("question"), ""));
        def.setHint(strOf(raw.get("hint"), null));
        def.setUseHotCities(Boolean.TRUE.equals(raw.get("useHotCities")));
        def.setSkippable(!Boolean.FALSE.equals(raw.get("skippable")));
        def.setSkipLabel(strOf(raw.get("skipLabel"), "跳过"));
        def.setDefaultValue(strOf(raw.get("defaultValue"), null));
        def.setAskable(!Boolean.FALSE.equals(raw.get("askable")));

        List<SlotOptionVO> options = new ArrayList<>();
        Object rawOptions = raw.get("options");
        if (rawOptions instanceof List) {
            for (Object o : (List<Object>) rawOptions) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> om = (Map<String, Object>) o;
                String label = strOf(om.get("label"), null);
                String value = strOf(om.get("value"), null);
                if (label == null && value == null) continue;
                options.add(new SlotOptionVO(label != null ? label : value, value != null ? value : label));
            }
        }
        def.setOptions(options);

        Object rawDepends = raw.get("dependsOn");
        if (rawDepends instanceof Map) {
            Map<String, List<String>> depends = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) rawDepends).entrySet()) {
                List<String> allowed = new ArrayList<>();
                if (e.getValue() instanceof List) {
                    for (Object v : (List<Object>) e.getValue()) {
                        if (v != null) allowed.add(String.valueOf(v));
                    }
                }
                depends.put(e.getKey(), allowed);
            }
            def.setDependsOn(depends);
        }
        return def;
    }

    private static int intOf(Object v, int fallback) {
        return v instanceof Number ? ((Number) v).intValue() : fallback;
    }

    private static String strOf(Object v, String fallback) {
        if (v == null) return fallback;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fallback : s;
    }
}
