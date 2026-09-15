package com.gkv.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.gkv.constant.ProfileConstant;
import com.gkv.dto.ProfileSummaryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 画像摘要模型客户端（独立 provider，与对话/规划用的模型完全分开）
 *
 * 为什么走 OpenAI 兼容格式的 /chat/completions：
 *   最通用，任何兼容厂商只改 base_url / key / model 三个配置项，不用动代码。
 *
 * 降级设计（这是整条旁路的核心）：
 *   - 三个配置项任一为空 → 直接返回 null 并打 warn，不抛异常。
 *     保证缺配置时对话、生成行程、标签累加照常跑，只是没有 AI 摘要。
 *   - 调用异常（网络、401、模型不存在）一律 catch 住返回 null，不向上抛：
 *     这是在业务成功之后跑的旁路任务，不能让旁路失败影响用户体验。
 */
@Component
@Slf4j
public class ProfileSummaryClient {

    /** OpenAI 兼容的 chat/completions 完整地址 */
    @Value("${profile.summary.url:}")
    private String summaryUrl;

    @Value("${profile.summary.api-key:}")
    private String summaryApiKey;

    @Value("${profile.summary.model:}")
    private String summaryModel;

    /**
     * 独立的 RestTemplate：不复用全局那个（读超时 900s，是给整条规划管线留的）。
     * 摘要是旁路短任务，不能挂着 15 分钟不放。
     */
    private final RestTemplate restTemplate;

    public ProfileSummaryClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 是否已配置齐全（供上层决定要不要打"未配置跳过"日志） */
    public boolean isConfigured() {
        return notBlank(summaryUrl) && notBlank(summaryApiKey) && notBlank(summaryModel);
    }

    /**
     * 调用摘要模型。
     *
     * @param existingProfileJson 已有画像的 JSON（让模型知道哪些信息已确认，除非本次明确推翻否则原样保留）
     * @param conversation        近期对话文本（调用侧已做条数与单条长度截断，控制 token）
     * @return 解析结果；未配置 / 调用失败 / 空响应 一律返回 null
     */
    public ProfileSummaryDTO summarize(String existingProfileJson, String conversation) {
        if (!isConfigured()) {
            log.warn("[ProfileSummary] 摘要模型未配置（profile.summary.url/api-key/model 存在空值），跳过摘要重写");
            return null;
        }
        String raw;
        try {
            JSONObject body = new JSONObject(true);
            body.put("model", summaryModel);
            JSONArray messages = new JSONArray();
            messages.add(message("system", buildSystemPrompt()));
            messages.add(message("user", buildUserPrompt(existingProfileJson, conversation)));
            body.put("messages", messages);
            body.put("temperature", 0.2);
            body.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + summaryApiKey);

            String respStr = restTemplate.postForObject(
                    summaryUrl, new HttpEntity<>(body.toJSONString(), headers), String.class);
            raw = extractContent(respStr);
        } catch (Exception e) {
            // 网络超时、401、模型不存在……全部只打 warn，不冒泡到业务链路
            log.warn("[ProfileSummary] 摘要模型调用失败，本次跳过摘要重写: {}", e.getMessage());
            return null;
        }

        if (raw == null || raw.trim().isEmpty()) {
            log.warn("[ProfileSummary] 摘要模型返回空内容，跳过摘要重写");
            return null;
        }
        return parseLoose(raw.trim());
    }

    /**
     * 宽松解析模型输出：剥掉 markdown 代码块、容忍前后有解释文字（取第一个 { 到最后一个 }）。
     * 解析失败时退化成「把整段文本当摘要保存」，不丢弃 —— 有摘要总比没有强。
     * 注意：退化时结构化字段全为 null，调用侧因此不会去改预算/节奏/同行人/天数。
     */
    private ProfileSummaryDTO parseLoose(String text) {
        String jsonPart = text;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            jsonPart = text.substring(start, end + 1);
        }
        try {
            ProfileSummaryDTO dto = JSON.parseObject(jsonPart, ProfileSummaryDTO.class);
            if (dto != null && notBlank(dto.getSummary_text())) {
                return dto;
            }
            log.warn("[ProfileSummary] 模型输出缺 summary_text，退化为整段文本当摘要");
        } catch (Exception e) {
            log.warn("[ProfileSummary] 模型输出不是合法 JSON，退化为整段文本当摘要: {}", e.getMessage());
        }
        ProfileSummaryDTO fallback = new ProfileSummaryDTO();
        fallback.setSummary_text(text);
        return fallback;
    }

    /** 从 OpenAI 兼容响应里取 choices[0].message.content */
    private String extractContent(String respStr) {
        if (respStr == null || respStr.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject resp = JSON.parseObject(respStr);
            if (resp == null) {
                return null;
            }
            JSONArray choices = resp.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("[ProfileSummary] 响应里没有 choices: {}", brief(respStr));
                return null;
            }
            JSONObject first = choices.getJSONObject(0);
            JSONObject message = first == null ? null : first.getJSONObject("message");
            return message == null ? null : message.getString("content");
        } catch (Exception e) {
            log.warn("[ProfileSummary] 响应解析失败: {}", brief(respStr));
            return null;
        }
    }

    private String buildSystemPrompt() {
        return "你是用户画像抽取引擎。请从「用户的对话记录」中提炼出这个人的旅行偏好画像，"
                + "只输出一个 JSON 对象，不要输出任何解释文字，不要用 markdown 代码块包裹。\n"
                + "\n"
                + "输出结构（字段名必须完全一致）：\n"
                + "{\n"
                + "  \"summary_text\": \"不超过 200 字的中文画像摘要，第二人称（你）或第三人称均可，只描述偏好特征\",\n"
                + "  \"free_tags\": [\"自由标签，例如 '爱拍日落'，最多 8 个\"],\n"
                + "  \"constraints\": \"硬性约束，多条用中文分号；分隔，无则空字符串\",\n"
                + "  \"budget_level\": \"经济/适中/高档 之一，判断不了填空字符串\",\n"
                + "  \"pace\": \"悠闲/常规/暴走 之一，判断不了填空字符串\",\n"
                + "  \"companions\": \"独自/情侣/朋友/家庭 之一，判断不了填空字符串\",\n"
                + "  \"prefer_days\": 整数天数，判断不了填 0\n"
                + "}\n"
                + "\n"
                + "规则：\n"
                + "1. budget_level / pace / companions 只能取给定值之一，禁止自创；判断不了就填空字符串。\n"
                + "2. free_tags 里不要出现这些受控标签（它们由规则单独维护，写了会互相覆盖）："
                + String.join("、", ProfileConstant.TAGS) + "。\n"
                + "3. 硬性约束只写用户明确提出的限制（如 不吃辣、不爬山、带老人），不要靠推测。\n"
                + "4. 用户画像里已确认的信息，除非本次对话明确推翻，否则原样保留。\n"
                + "5. 信息不足时宁可留空，不要编造。";
    }

    private String buildUserPrompt(String existingProfileJson, String conversation) {
        return "【当前已有画像（JSON，字段含义见系统提示；可能为空对象）】\n"
                + (notBlank(existingProfileJson) ? existingProfileJson : "{}")
                + "\n\n【用户近期的对话记录】\n"
                + (notBlank(conversation) ? conversation : "（无）")
                + "\n\n请输出更新后的画像 JSON。";
    }

    private JSONObject message(String role, String content) {
        JSONObject msg = new JSONObject(true);
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String brief(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }

    /** 供上层拼"已有画像 JSON"用（字段为 null 的项不出现，避免模型把 null 当成"用户没有"） */
    public static String toProfileJson(Map<String, Object> fields) {
        JSONObject obj = new JSONObject(true);
        if (fields != null) {
            for (Map.Entry<String, Object> e : fields.entrySet()) {
                if (e.getValue() == null) {
                    continue;
                }
                if (e.getValue() instanceof List) {
                    List<?> list = (List<?>) e.getValue();
                    if (list.isEmpty()) {
                        continue;
                    }
                }
                if (e.getValue() instanceof String && ((String) e.getValue()).trim().isEmpty()) {
                    continue;
                }
                obj.put(e.getKey(), e.getValue());
            }
        }
        return obj.toJSONString();
    }
}
