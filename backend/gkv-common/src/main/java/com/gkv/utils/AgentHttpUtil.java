package com.gkv.utils;

import com.alibaba.fastjson.JSON;
import com.gkv.dto.AgentRequestDTO;
import com.gkv.dto.ChatMessageDTO;
import com.gkv.dto.ChatRequestDTO;
import com.gkv.dto.ChatResponseDTO;
import com.gkv.dto.PlanResponseDTO;
import com.gkv.dto.TripPlanFrameDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.List;

@Component
public class AgentHttpUtil {
    @Resource
    private RestTemplate restTemplate;

    @Value("${agent.plan-url:http://localhost:8002/api/plan}")
    private String planUrl;

    @Value("${agent.chat-url:http://localhost:8002/api/chat}")
    private String chatUrl;

    /**
     * 表单式生成行程（保留 he1 原有 aiPlan 链路使用）
     */
    public PlanResponseDTO callAgent(AgentRequestDTO req) {
        // 设置请求头，指定 UTF-8 编码
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

        // 构建 Python Agent 期望的请求格式
        // Python Agent 期望: { session_id, base_info: {...}, user_input, chat_history }
        String wrappedJson = buildPythonAgentRequest(req);

        // 创建带请求头的 HTTP 实体
        HttpEntity<String> request = new HttpEntity<>(wrappedJson, headers);

        // 发送 POST 请求
        String respStr = restTemplate.postForObject(planUrl, request, String.class);

        // 解析响应
        return JSON.parseObject(respStr, PlanResponseDTO.class);
    }

    /**
     * 长对话聊天（dev2 新增）
     * agent /api/chat 返回流式纯文本（StreamingResponse，非JSON），
     * 需要流式读取并拼接为完整回复文本。
     */
    public ChatResponseDTO callChat(ChatRequestDTO req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

        String wrappedJson = buildAgentRequest(req.getSession_id(), req.getBase_info(), req.getUser_input(), req.getChat_history());
        HttpEntity<String> request = new HttpEntity<>(wrappedJson, headers);

        // 流式读取响应体并拼接完整文本
        String replyText = restTemplate.execute(
                chatUrl,
                HttpMethod.POST,
                clientHttpRequest -> {
                    clientHttpRequest.getHeaders().setContentType(MediaType.APPLICATION_JSON_UTF8);
                    clientHttpRequest.getBody().write(wrappedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                },
                res -> {
                    StringBuilder sb = new StringBuilder();
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(res.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                    }
                    return sb.toString();
                });

        ChatResponseDTO dto = new ChatResponseDTO();
        dto.setSession_id(req.getSession_id());
        dto.setReply(replyText != null ? replyText.trim() : "");
        return dto;
    }

    /**
     * 长对话后生成详细行程计划（dev2 新增）
     */
    public PlanResponseDTO callPlan(ChatRequestDTO req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

        String wrappedJson = buildAgentRequest(req.getSession_id(), req.getBase_info(), req.getUser_input(), req.getChat_history());
        HttpEntity<String> request = new HttpEntity<>(wrappedJson, headers);

        String respStr = restTemplate.postForObject(planUrl, request, String.class);
        return JSON.parseObject(respStr, PlanResponseDTO.class);
    }

    /**
     * 【已废弃，勿再使用】阶段一：生成行程框架
     * agent /api/plan?mode=frame 返回 {"frame": {...}}
     *
     * 智能体升级后 /api/plan 已经没有 mode 参数（未知查询参数会被静默忽略），
     * 调用它实际会执行整条规划管线、返回 {plan_data: ...}（没有 frame 字段），
     * 与 {@link #callPlanDetail} 搭配使用等于把同一条管线跑两遍，时间和模型额度都翻倍。
     * 生成行程请统一用 {@link #callPlan(ChatRequestDTO)}。
     */
    @Deprecated
    public TripPlanFrameDTO callPlanFrame(ChatRequestDTO req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

        String wrappedJson = buildAgentRequest(req.getSession_id(), req.getBase_info(), req.getUser_input(), req.getChat_history());
        HttpEntity<String> request = new HttpEntity<>(wrappedJson, headers);

        String respStr = restTemplate.postForObject(planUrl + "?mode=frame", request, String.class);
        com.alibaba.fastjson.JSONObject respObj = JSON.parseObject(respStr);
        return respObj == null || respObj.getJSONObject("frame") == null
                ? new TripPlanFrameDTO()
                : respObj.getJSONObject("frame").toJavaObject(TripPlanFrameDTO.class);
    }

    /**
     * 【已废弃，勿再使用】阶段二：基于已确认框架生成完整行程详情
     * agent /api/plan?mode=detail 返回 {"plan_data": {...}}
     *
     * 原因同 {@link #callPlanFrame}：mode 参数已失效，这次调用会再跑一整遍管线。
     */
    @Deprecated
    public PlanResponseDTO callPlanDetail(ChatRequestDTO req, TripPlanFrameDTO frame) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

        String wrappedJson = buildAgentRequest(req.getSession_id(), req.getBase_info(), req.getUser_input(), req.getChat_history(), frame);
        HttpEntity<String> request = new HttpEntity<>(wrappedJson, headers);

        String respStr = restTemplate.postForObject(planUrl + "?mode=detail", request, String.class);
        return JSON.parseObject(respStr, PlanResponseDTO.class);
    }

    /**
     * 将 AgentRequestDTO 转换为 Python Agent 期望的格式
     * Python Agent 期望的格式:
     * {
     *   "session_id": "xxx",
     *   "base_info": {
     *     "departure_city": "...",
     *     "destination_city": "...",
     *     ...
     *   },
     *   "user_input": null,
     *   "chat_history": []
     * }
     */
    private String buildPythonAgentRequest(AgentRequestDTO req) {
        // 使用 FastJSON 手动构建嵌套结构
        com.alibaba.fastjson.JSONObject wrapper = new com.alibaba.fastjson.JSONObject();

        // 生成唯一的 session_id（使用时间戳）
        wrapper.put("session_id", "session_" + System.currentTimeMillis());

        // 构建 base_info 对象
        com.alibaba.fastjson.JSONObject baseInfo = new com.alibaba.fastjson.JSONObject();
        baseInfo.put("departure_city", req.getDeparture_city());
        baseInfo.put("destination_city", req.getDestination_city());
        baseInfo.put("start_day", req.getStart_day() != null ? req.getStart_day() : "");
        baseInfo.put("end_date", req.getEnd_date() != null ? req.getEnd_date() : "");
        baseInfo.put("days", req.getDays());
        baseInfo.put("hobby", req.getHobby());
        baseInfo.put("people_num", req.getPeople_num());
        baseInfo.put("budget", req.getBudget());

        wrapper.put("base_info", baseInfo);
        wrapper.put("user_input", "");
        wrapper.put("chat_history", new com.alibaba.fastjson.JSONArray());

        return wrapper.toJSONString();
    }

    /**
     * 构建长对话式 Agent 请求体（chat / generatePlan 共用）
     */
    private String buildAgentRequest(String sessionId, com.gkv.dto.BaseInfoDTO baseInfo, String userInput, List<ChatMessageDTO> chatHistory) {
        com.alibaba.fastjson.JSONObject wrapper = new com.alibaba.fastjson.JSONObject();
        wrapper.put("session_id", sessionId);

        com.alibaba.fastjson.JSONObject baseInfoObj = new com.alibaba.fastjson.JSONObject();
        if (baseInfo != null) {
            baseInfoObj.put("departure_city", baseInfo.getDeparture_city());
            baseInfoObj.put("destination_city", baseInfo.getDestination_city());
            baseInfoObj.put("start_day", baseInfo.getStart_day() != null ? baseInfo.getStart_day() : "");
            baseInfoObj.put("end_date", baseInfo.getEnd_date() != null ? baseInfo.getEnd_date() : "");
            baseInfoObj.put("days", baseInfo.getDays());
            baseInfoObj.put("hobby", baseInfo.getHobby());
            baseInfoObj.put("people_num", baseInfo.getPeople_num());
            baseInfoObj.put("budget", baseInfo.getBudget());
            baseInfoObj.put("context_note", baseInfo.getContext_note() != null ? baseInfo.getContext_note() : "");
            // 用户历史画像回灌文本。由服务端在「提交任务的请求线程内」构建并覆盖写入，
            // 前端传什么都不作数（上层取值时会先覆盖 baseInfo.profile_note）。
            // 注意：智能体侧的入参模型必须显式声明这个字段 —— pydantic 默认会静默丢弃
            // 未声明的额外字段，否则前端看着传了，prompt 里其实什么都没有。
            baseInfoObj.put("profile_note", baseInfo.getProfile_note() != null ? baseInfo.getProfile_note() : "");
        }
        wrapper.put("base_info", baseInfoObj);
        wrapper.put("user_input", userInput != null ? userInput : "");

        com.alibaba.fastjson.JSONArray historyArray = new com.alibaba.fastjson.JSONArray();
        if (chatHistory != null) {
            for (ChatMessageDTO msg : chatHistory) {
                com.alibaba.fastjson.JSONObject msgObj = new com.alibaba.fastjson.JSONObject();
                msgObj.put("role", msg.getRole());
                msgObj.put("content", msg.getContent());
                historyArray.add(msgObj);
            }
        }
        wrapper.put("chat_history", historyArray);

        return wrapper.toJSONString();
    }

    /**
     * 构建长对话式 Agent 请求体（阶段二专用，携带已确认的行程框架）
     */
    private String buildAgentRequest(String sessionId, com.gkv.dto.BaseInfoDTO baseInfo, String userInput, List<ChatMessageDTO> chatHistory, TripPlanFrameDTO frame) {
        com.alibaba.fastjson.JSONObject wrapper = JSON.parseObject(buildAgentRequest(sessionId, baseInfo, userInput, chatHistory));
        if (frame != null) {
            wrapper.put("frame", JSON.toJSON(frame));
        }
        return wrapper.toJSONString();
    }
}