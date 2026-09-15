package com.gkv.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.gkv.constant.ProfileConstant;
import com.gkv.dto.ProfileTagDTO;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 画像标签列的序列化工具
 *
 * 为什么必须走这里而不是 JSON.toJSONString(list)：
 *   fastjson 默认特性里带 SortField，bean 序列化会按字段名排序，"碰巧"是 tag/weight。
 *   但那是库的默认行为、不是我们的契约，一旦换库或换特性顺序就变了，
 *   而管理端是按 JSON 子串（LIKE '%"自然风光"%'）筛标签的 —— 顺序不稳就会时灵时不灵。
 *   所以这里显式构造有序 JSONObject，把字段顺序写死成 tag → weight。
 */
@Slf4j
public final class ProfileTagUtil {

    private ProfileTagUtil() {
    }

    /** 解析标签 JSON；脏数据一律降级成空集合，不抛异常 */
    public static List<ProfileTagDTO> parse(String json) {
        List<ProfileTagDTO> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return result;
        }
        try {
            JSONArray arr = JSON.parseArray(json);
            if (arr == null) {
                return result;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String tag = ProfileConstant.normalizeTag(obj.getString("tag"));
                Integer weight = obj.getInteger("weight");
                if (tag != null) {
                    result.add(new ProfileTagDTO(tag, weight == null || weight < 1 ? 1 : weight));
                }
            }
        } catch (Exception e) {
            log.warn("[ProfileTag] 标签 JSON 解析失败，按空集合处理: {}", json);
        }
        return sort(result);
    }

    /**
     * 校验 + 去重 + 排序（权重倒序，同权重按受控词表顺序）
     *
     * 不在受控词表内的标签直接丢弃：受控标签列只装受控标签，
     * 想塞别的内容请走自由标签。管理端用的是下拉框，正常不会触发这条分支。
     */
    public static List<ProfileTagDTO> normalize(List<ProfileTagDTO> input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (ProfileTagDTO item : input) {
            if (item == null) {
                continue;
            }
            String tag = ProfileConstant.normalizeTag(item.getTag());
            if (tag == null) {
                log.warn("[ProfileTag] 丢弃非受控标签: {}", item.getTag());
                continue;
            }
            Integer weight = item.getWeight();
            int w = (weight == null || weight < 1) ? 1 : weight;
            Integer exist = merged.get(tag);
            // 重复标签取较大权重：人工编辑时同一个标签出现两次，保留更有信息量的那个
            merged.put(tag, exist == null ? w : Math.max(exist, w));
        }
        List<ProfileTagDTO> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : merged.entrySet()) {
            list.add(new ProfileTagDTO(e.getKey(), e.getValue()));
        }
        return sort(list);
    }

    /** 排序：权重倒序；同权重按受控词表顺序，保证结果稳定可复现 */
    public static List<ProfileTagDTO> sort(List<ProfileTagDTO> tags) {
        List<ProfileTagDTO> list = new ArrayList<>(tags == null ? new ArrayList<>() : tags);
        list.sort(Comparator
                .comparingInt((ProfileTagDTO t) -> t.getWeight() == null ? 0 : -t.getWeight())
                .thenComparingInt(t -> {
                    int idx = ProfileConstant.TAGS.indexOf(t.getTag());
                    return idx < 0 ? Integer.MAX_VALUE : idx;
                }));
        return list;
    }

    /** 序列化成固定字段顺序的 JSON 字符串 */
    public static String toJson(List<ProfileTagDTO> tags) {
        JSONArray arr = new JSONArray();
        for (ProfileTagDTO tag : sort(tags)) {
            // JSONObject(true) = LinkedHashMap 支撑，key 顺序即 put 顺序
            JSONObject obj = new JSONObject(true);
            obj.put("tag", tag.getTag());
            obj.put("weight", tag.getWeight() == null ? 1 : tag.getWeight());
            arr.add(obj);
        }
        return arr.toJSONString();
    }

    /** 权重最高的前 n 个标签（回灌文本用） */
    public static List<ProfileTagDTO> topN(List<ProfileTagDTO> tags, int n) {
        List<ProfileTagDTO> sorted = sort(tags);
        return sorted.size() <= n ? sorted : new ArrayList<>(sorted.subList(0, n));
    }

    /**
     * 标签筛选的 LIKE 模式
     *
     * 用带引号的标签名做子串，这样与 JSON 字段顺序无关（filter by "tag":"自然风光"），
     * 受控标签不含引号，不需要转义。注意调用侧必须走参数化查询，不要拼字符串。
     */
    public static String likePattern(String tag) {
        return "%\"" + tag + "\"%";
    }

    /**
     * 画像文本的短指纹（8 位十六进制）
     *
     * 用途：行程结果缓存的 key 指纹。加了回灌之后，同样的
     * 「城市+天数+偏好+预算」在不同画像下应该产出不同结果，
     * 指纹不进 key 的话缓存命中会让回灌静默失效 —— 功能"看起来做好了"但经常不生效。
     */
    public static String fingerprint(String profileNote) {
        if (profileNote == null || profileNote.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(profileNote.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // 摘要算法不可用属于极端环境问题，退化成 hashCode 也要保证 key 会变
            return Integer.toHexString(profileNote.hashCode());
        }
    }
}
