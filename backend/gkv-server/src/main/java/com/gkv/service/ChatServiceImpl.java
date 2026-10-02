package com.gkv.service;

import com.gkv.dto.*;
import com.gkv.context.BaseContext;
import com.gkv.entity.ChatHistory;
import com.gkv.mapper.ChatHistoryMapper;
import com.gkv.service.slot.SlotEngine;
import com.gkv.service.slot.SuggestionEngine;
import com.gkv.utils.AgentHttpUtil;
import com.gkv.vo.SlotStateVO;
import com.gkv.vo.TravelResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;


@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Resource
    private AgentHttpUtil agentHttpUtil;

    @Resource
    private ChatHistoryMapper chatHistoryMapper;

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private SlotEngine slotEngine;

    @Resource
    private SuggestionEngine suggestionEngine;

    /**
     * 是否把用户历史画像回灌给模型。
     *
     * 默认 false（停用）：产品侧要求先隐藏并停用「AI 记住的偏好」开关。
     * 恢复方式：配置 sky.profile.inject-enabled=true，不需要改代码。
     */
    @Value("${sky.profile.inject-enabled:false}")
    private boolean profileInjectEnabled;

    @Override
    public ChatResponseDTO chat(ChatRequestDTO req) {
        return chatStream(req, null);
    }

    /**
     * 流式对话。
     *
     * 与 chat() 共用同一套准备与收尾（会话 id、槽位抽取、画像/槽位回灌、落库、快照），
     * 唯一区别是模型回复「边收边给」：每读到一段就 onDelta 一次。
     * 这样前端能逐字渲染，而不是盯着"思考中"等一整段。
     */
    @Override
    public ChatResponseDTO chatStream(ChatRequestDTO req, Consumer<String> onDelta) {
        // 1) 从登录上下文取 userId（对话链路已收回登录态，这里必有值；取不到也照常对话，只是不沉淀）
        Long userId = BaseContext.getCurrentId();

        String sessionId = req.getSession_id();

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
            req.setSession_id(sessionId);
        }

        // 2) 调 AI 之前注入画像回灌文本与槽位回灌文本。
        //    顺序很关键：注入必须在调模型之前，否则模型看到的是没有画像/槽位的 prompt。
        //    槽位的廉价抽取也要放在注入之前，这样本轮刚说出的「3天」能立刻体现在 prompt 里。
        slotEngine.applyText(sessionId, req.getUser_input());
        injectProfileNote(req, userId);
        injectSlotNote(req, sessionId);

        // 3) 调模型。有回调就是流式，没有就是整包（两种走的是同一个 HTTP 请求，只是读取方式不同）
        String replyText = agentHttpUtil.streamChat(req, onDelta);

        // 模型的候选追问块要从正文里剥离（原因见 extractFollowUps 注释）：
        // 必须在下面组装 assistantMsg **之前**做，否则带标记的正文会入库，
        // 下一轮当历史回传给模型，模型会以为自己上一轮就该带这么一段。
        SuggestionEngine.FollowUpParse followUps = SuggestionEngine.extractFollowUps(replyText);

        ChatResponseDTO agentResp = new ChatResponseDTO();
        agentResp.setSession_id(sessionId);
        agentResp.setReply(followUps.getReply());

        List<ChatMessageDTO> updatedHistory = new ArrayList<>();
        if (req.getChat_history() != null) {
            updatedHistory.addAll(req.getChat_history());
        }

        ChatMessageDTO userMsg = new ChatMessageDTO();
        userMsg.setRole("user");
        userMsg.setContent(req.getUser_input());
        updatedHistory.add(userMsg);

        ChatMessageDTO assistantMsg = new ChatMessageDTO();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(agentResp.getReply());
        updatedHistory.add(assistantMsg);

        // 只入库本轮新增的 user + assistant 两条：
        // 完整历史由前端每轮回传（updatedHistory 仅用于响应），
        // 避免把历史消息重复 insert 造成 chat_history 表数据膨胀
        List<ChatHistory> records = new ArrayList<>();
        for (ChatMessageDTO msg : new ChatMessageDTO[]{userMsg, assistantMsg}) {
            ChatHistory record = new ChatHistory();
            // 3) 带上 user_id：画像要按用户聚合，必须知道每条消息属于谁
            record.setUserId(userId);
            record.setSessionId(sessionId);
            record.setRole(msg.getRole());
            record.setContent(msg.getContent());
            record.setCreateTime(LocalDateTime.now());
            records.add(record);
        }
        for (ChatHistory record : records) {
            chatHistoryMapper.insert(record);
        }

        // 4) 落库后做规则累加。整个画像链路包在 try/catch 里：
        //    画像任何失败都不能让用户的对话失败（画像表还没建、字段不兼容等都不能冒泡）。
        try {
            userProfileService.accumulate(userId, req.getBase_info(), req.getUser_input());
        } catch (Exception e) {
            log.warn("[Chat] 画像规则累加失败，不影响本轮对话: {}", e.getMessage(), e);
        }

        agentResp.setSession_id(sessionId);
        agentResp.setChat_history(updatedHistory);
        // 记录本轮回复里提到的城市，用于给「你想去哪里？」的选项排序。
        // 必须在下面 snapshot() **之前**做：AI 刚推荐了成都/厦门/西安，
        // 紧接着问用户"想去哪里"，这三个就该排在最前面。
        // 用剥离后的正文：候选块里的城市名不该影响排序。
        slotEngine.noteCityMentions(sessionId, slotEngine.extractCityMentions(followUps.getReply()));

        // 槽位快照必须每轮下发：前端的可点击选项、完整度与渐显入口全靠它
        SlotStateVO slotState = slotEngine.snapshot(sessionId);
        agentResp.setSlot_state(slotState);
        // 「猜你想问」跟着快照一起算：它需要看槽位（目的地/同行人/轮次），
        // 而且规则里要避开"当前待回答的那个槽位问题"，所以必须在快照之后。
        //
        // 轮次用**用户说过几句话**来算，而不是槽位的 askedCount：
        // askedCount 只统计"槽位问题被回答了几次"，用户打字聊天（不点选项）时它恒为 0，
        // 候选就永远停在第一条 —— 实测第 1、2 轮都冒出同一句「深圳有什么必吃？」
        //
        // 轮次从**数据库**数，不数 req.chat_history：客户端不回传历史时（例如脚本直连、
        // 或前端某次没带），按请求体数出来恒为 1，候选就不轮换了。库里每轮都落了 user 消息，
        // 是权威来源。
        int round;
        try {
            Long userMsgCount = chatHistoryMapper.selectCount(
                    new LambdaQueryWrapper<ChatHistory>()
                            .eq(ChatHistory::getSessionId, sessionId)
                            .eq(ChatHistory::getRole, "user"));
            round = userMsgCount == null ? 0 : userMsgCount.intValue();
        } catch (Exception e) {
            log.warn("[Chat] 统计对话轮次失败，退回按本次请求体估算: {}", e.getMessage());
            round = (int) updatedHistory.stream()
                    .filter(m -> m != null && "user".equalsIgnoreCase(m.getRole()))
                    .count();
        }
        // 候选追问：**规则优先**，模型给的 <followups> 只用来补足到上限。
        //
        // 为什么不再模型优先（原实现）：规则引擎是唯一知道当前槽位的地方 ——
        // 它按 destination 给当地话题，还会避开"当前正在问的那个问题"。
        // 而模型的 <followups> 只被 prompt 要求「紧扣刚说的那条回复」，
        // 没有任何机制校验它和槽位一致；加上模型看不到用户点选项的回合
        // （槽位链路不调模型、也不落库），它的上下文天然滞后一轮 ——
        // 实测现象就是「已经选了成都，却还在推第一轮提过的大理/厦门」。
        //
        // 合并策略抽到 SuggestionEngine.mergeSuggestions，便于单测覆盖。
        List<String> suggestions = SuggestionEngine.mergeSuggestions(
                suggestionEngine.suggest(slotState, round),
                followUps.getQuestions(),
                suggestionEngine.getMaxItems());
        agentResp.setSuggested_questions(suggestions);
        agentResp.setSuggested_questions(suggestions);
        return agentResp;
    }

    /**
     * 注入画像回灌文本到 base_info.profile_note
     *
     * 必须在本方法的调用线程（请求线程）里做：如果行程生成是异步任务，
     * 后台线程拿不到登录上下文（ThreadLocal 取不到用户），回灌就会静默失效。
     * 这里 chat 是同步链路，但 generatePlan 是异步的 —— 那边在 PlanTaskService.submit 里注入。
     *
     * ⚠️ 当前默认停用：产品侧要求先隐藏并停用「AI 记住的偏好」开关，
     * 因此这里不再把历史画像喂给模型。开关见 sky.profile.inject-enabled（默认 false）。
     *
     * 注意停用范围仅限于「回灌」：userProfileService.accumulate(...) 仍在正常沉淀数据，
     * 所以将来把开关打开就能立刻恢复，不需要补数据。
     */
    private void injectProfileNote(ChatRequestDTO req, Long userId) {
        BaseInfoDTO base = req.getBase_info();
        if (base == null) {
            return;
        }
        if (!profileInjectEnabled) {
            // 显式置空而不是留 null：AgentHttpUtil 会把 null 兜成 ""，这里直接给 "" 更明确
            base.setProfile_note("");
            return;
        }
        try {
            String note = userProfileService.buildInjectNote(userId);
            // 无条件覆盖：这个字段只由服务端注入，客户端传什么都不作数
            base.setProfile_note(note);
            if (note != null) {
                log.info("[Chat] 已为用户{}注入画像回灌文本，长度={}", userId, note.length());
            }
        } catch (Exception e) {
            log.warn("[Chat] 构建画像回灌文本失败，按无画像继续: {}", e.getMessage());
            base.setProfile_note(null);
        }
    }

    /**
     * 注入槽位回灌文本到 base_info.slot_note
     *
     * 作用：用户已经通过点选项明确回答过的信息（同行人、预算、住宿风格等），
     * 不要再在对话里追问一遍 —— 那会让用户觉得「我刚说过」。
     * 与画像一样，这个字段只由服务端写入，客户端传什么都不作数。
     */
    private void injectSlotNote(ChatRequestDTO req, String sessionId) {
        BaseInfoDTO base = req.getBase_info();
        if (base == null) {
            return;
        }
        try {
            base.setSlot_note(slotEngine.buildInjectNote(sessionId));
        } catch (Exception e) {
            log.warn("[Chat] 构建槽位回灌文本失败，按无槽位继续: {}", e.getMessage());
            base.setSlot_note(null);
        }
    }

    /**
     * 长对话后生成完整行程计划，返回含 plan_data.day_list 的完整 PlanResponseDTO，
     * 供前端展示每日景点预览。
     *
     * 注意：控制器的 /travel/generatePlan 现在走 PlanTaskService（异步任务版），
     * 这个方法是同步版的遗留入口，当前没有调用方。这里同样注入画像回灌文本 ——
     * 否则将来谁把它接回去，就会出现"只有异步链路有画像、同步链路没有"的静默差异。
     */
    @Override
    public PlanResponseDTO generatePlan(ChatRequestDTO req) {
        if (req.getSession_id() == null || req.getSession_id().isEmpty()) {
            req.setSession_id(UUID.randomUUID().toString().replace("-", ""));
        }
        injectProfileNote(req, BaseContext.getCurrentId());
        return agentHttpUtil.callPlan(req);
    }

    /**
     * 查询会话历史消息：按 create_time 正序，供前端刷新页面后恢复多轮上下文
     *
     * 归属校验：老数据 user_id 为 NULL（迁移时不做归属回溯），所以不能拿 user_id 直接过滤，
     * 否则所有历史会话都恢复不了。这里改成"只要这个会话里出现过别人的消息就拒绝返回" ——
     * 既保住了老会话的恢复能力，又堵住了登录用户拿到别人 sessionId 就能读别人对话的口子。
     */
    @Override
    public List<ChatMessageDTO> listHistory(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChatHistory> records = chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getSessionId, sessionId)
                        .orderByAsc(ChatHistory::getCreateTime)
                        .orderByAsc(ChatHistory::getId)
        );
        Long userId = BaseContext.getCurrentId();
        for (ChatHistory record : records) {
            if (record.getUserId() != null && !Objects.equals(record.getUserId(), userId)) {
                log.warn("[Chat] 会话{}不属于当前用户{}，拒绝返回历史", sessionId, userId);
                return new ArrayList<>();
            }
        }
        List<ChatMessageDTO> messages = new ArrayList<>();
        for (ChatHistory record : records) {
            ChatMessageDTO msg = new ChatMessageDTO();
            msg.setRole(record.getRole());
            msg.setContent(record.getContent());
            messages.add(msg);
        }
        return messages;
    }
}