package com.gkv.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.dto.ProfileSummaryDTO;
import com.gkv.dto.ProfileTagDTO;
import com.gkv.entity.ChatHistory;
import com.gkv.entity.UserProfile;
import com.gkv.mapper.ChatHistoryMapper;
import com.gkv.utils.ProfileNoteBuilder;
import com.gkv.utils.ProfileSummaryClient;
import com.gkv.utils.ProfileTagUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 画像摘要重写（业务成功后的旁路任务）
 *
 * 触发时机：生成行程成功落库之后（TripPlanServiceImpl.save），且本条业务数据带 AI 会话 id。
 * 没会话 id 说明不是 AI 对话产出的，不该沉淀。
 *
 * 三个刻意的设计：
 *   1. 独立线程池，与行程生成线程池（PlanTaskService，4 线程、单任务 3~8 分钟）分开，
 *      避免长任务把摘要任务排到后面去；线程名带 profile-rewrite- 前缀便于排查。
 *   2. 只取最近 N 条对话、单条截断，控制 token。
 *   3. 失败只打 warn —— 任务早就成功返回给用户了，旁路失败不能让体验受影响，
 *      更不能因为旁路异常把行程保存回滚掉。
 */
@Service
@Slf4j
public class ProfileRewriteService implements DisposableBean {

    /** 摘要任务线程池大小：低频旁路，2 个线程足够 */
    private static final int POOL_SIZE = 2;
    /** 参与摘要的最近对话条数上限 */
    private static final int MAX_MESSAGES = 30;
    /** 单条消息内容截断长度 */
    private static final int MAX_CONTENT_CHARS = 500;

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private ChatHistoryMapper chatHistoryMapper;

    @Resource
    private ProfileSummaryClient profileSummaryClient;

    private final AtomicInteger threadSeq = new AtomicInteger(1);

    private final ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "profile-rewrite-" + threadSeq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * 触发一次摘要重写（异步，立刻返回）
     *
     * 必须在「提交任务的请求线程内」调用：后台线程拿不到登录上下文（ThreadLocal 是空的），
     * 所以 userId 要在这里显式传进来，不能在任务里再取。
     */
    public void triggerAsync(Long userId, String chatSessionId) {
        if (userId == null || !StringUtils.hasText(chatSessionId)) {
            log.info("[ProfileRewrite] userId={} 无 AI 会话 id，不触发画像摘要重写", userId);
            return;
        }
        try {
            executor.submit(() -> rewrite(userId, chatSessionId));
        } catch (Exception e) {
            // 线程池已关闭（应用正在停机）等极端情况，不能影响主链路
            log.warn("[ProfileRewrite] 提交摘要重写任务失败，跳过: {}", e.getMessage());
        }
    }

    /** 异步任务内部：查画像行 → 拉近期对话 → 调摘要模型 → 写回 */
    private void rewrite(Long userId, String chatSessionId) {
        try {
            UserProfile profile = userProfileService.findByUserId(userId);
            if (profile == null) {
                log.info("[ProfileRewrite] 用户{}没有画像行，跳过摘要重写", userId);
                return;
            }
            String conversation = loadRecentConversation(userId, chatSessionId);
            if (!StringUtils.hasText(conversation)) {
                log.info("[ProfileRewrite] 会话{}没有可用对话记录，跳过摘要重写", chatSessionId);
                return;
            }
            if (!profileSummaryClient.isConfigured()) {
                log.warn("[ProfileRewrite] 摘要模型未配置（profile.summary.url/api-key/model 存在空值），跳过摘要重写");
                return;
            }
            ProfileSummaryDTO summary = profileSummaryClient.summarize(buildExistingProfileJson(profile), conversation);
            if (summary == null) {
                // 客户端内部已经把失败原因打出来了，这里只标记本次没写回
                log.warn("[ProfileRewrite] 用户{}本次未获得摘要结果，保持原画像不变", userId);
                return;
            }
            userProfileService.saveModelSummary(userId, summary);
        } catch (Exception e) {
            // 旁路任务：任何异常都只打日志，绝不冒泡
            log.warn("[ProfileRewrite] 摘要重写失败（不影响已完成的业务）: {}", e.getMessage(), e);
        }
    }

    /** 取该会话最近 N 条对话，按时间正序拼成文本 */
    private String loadRecentConversation(Long userId, String chatSessionId) {
        List<ChatHistory> records = chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getSessionId, chatSessionId)
                .eq(ChatHistory::getUserId, userId)
                .orderByDesc(ChatHistory::getId)
                .last("limit " + MAX_MESSAGES));
        if (records == null || records.isEmpty()) {
            return null;
        }
        // 查到的是倒序（最近的在最前），反转回时间正序再喂给模型
        List<ChatHistory> ordered = new ArrayList<>(records);
        Collections.reverse(ordered);

        StringBuilder sb = new StringBuilder();
        for (ChatHistory record : ordered) {
            if (record == null || !StringUtils.hasText(record.getContent())) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(record.getRole()) ? "助手" : "用户";
            String content = record.getContent().trim();
            if (content.length() > MAX_CONTENT_CHARS) {
                content = content.substring(0, MAX_CONTENT_CHARS) + "…（已截断）";
            }
            sb.append(role).append("：").append(content).append('\n');
        }
        return sb.toString();
    }

    /**
     * 把已有画像拼成 JSON 交给模型（让它知道哪些信息已确认，除非本次明确推翻否则原样保留）。
     * null / 空数组 / 空串的项不出现，避免模型把"没有"误读成"用户否定了这一项"。
     */
    private String buildExistingProfileJson(UserProfile profile) {
        JSONObject obj = new JSONObject(true);

        List<ProfileTagDTO> topTags = ProfileTagUtil.topN(ProfileTagUtil.parse(profile.getPreferenceTags()), 10);
        if (!topTags.isEmpty()) {
            JSONArray tags = new JSONArray();
            for (ProfileTagDTO tag : topTags) {
                JSONObject item = new JSONObject(true);
                item.put("tag", tag.getTag());
                item.put("weight", tag.getWeight());
                tags.add(item);
            }
            obj.put("已确认偏好标签（权重由规则维护，不要改动）", tags);
        }
        List<String> freeTags = ProfileNoteBuilder.parseFreeTags(profile.getFreeTags());
        if (!freeTags.isEmpty()) {
            obj.put("已有自由标签", freeTags);
        }
        if (StringUtils.hasText(profile.getConstraintsText())) {
            obj.put("已确认硬性约束", profile.getConstraintsText());
        }
        if (StringUtils.hasText(profile.getBudgetLevel())) {
            obj.put("已确认预算档位", profile.getBudgetLevel());
        }
        if (StringUtils.hasText(profile.getPace())) {
            obj.put("已确认出行节奏", profile.getPace());
        }
        if (StringUtils.hasText(profile.getCompanions())) {
            obj.put("已确认同行人", profile.getCompanions());
        }
        if (profile.getPreferDays() != null && profile.getPreferDays() > 0) {
            obj.put("已确认偏好天数", profile.getPreferDays());
        }
        if (StringUtils.hasText(profile.getSummaryText())) {
            obj.put("上一版画像摘要", profile.getSummaryText());
        }
        return obj.toJSONString();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
