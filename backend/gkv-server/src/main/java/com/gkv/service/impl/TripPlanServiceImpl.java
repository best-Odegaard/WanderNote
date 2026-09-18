package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.context.BaseContext;
import com.gkv.dto.AttractionDTO;
import com.gkv.dto.BaseInfoDTO;
import com.gkv.dto.ChatMessageDTO;
import com.gkv.dto.ChatRequestDTO;
import com.gkv.dto.DayScheduleDTO;
import com.gkv.dto.PlanResponseDTO;
import com.gkv.dto.TripImportLinkDTO;
import com.gkv.dto.TripPlanDTO;
import com.gkv.dto.TripPlanFrameDTO;
import com.gkv.entity.TripPlan;
import com.gkv.exception.BaseException;
import com.gkv.mapper.TripPlanMapper;
import com.gkv.service.TripPlanService;
import com.gkv.utils.AgentHttpUtil;
import com.gkv.utils.TripDateUtil;
import com.gkv.utils.WebPageFetcher;
import com.gkv.vo.TripPlanVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TripPlanServiceImpl implements TripPlanService {

    @Autowired
    private TripPlanMapper tripPlanMapper;

    @Resource
    private AgentHttpUtil agentHttpUtil;

    @Resource
    private com.gkv.service.ProfileRewriteService profileRewriteService;

    /** 从链接中识别目的地城市：优先 "xx市"，其次 "xx旅游/游记/攻略/打卡/一日游/玩" 等模式 */
    private static final Pattern CITY_WITH_SUFFIX = Pattern.compile("([\\u4e00-\\u9fa5]{2,5}?)市");
    private static final Pattern CITY_BEFORE_KEYWORD = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,5}?)(?:旅游|游记|攻略|打卡|美食|[0-9一二三四五六日]+日游|玩)");
    private static final String CITY_TRIM_PREFIX = "^[去游]";

    /** 从行程原文里猜天数用：「两天」「2天」「三日游」 */
    private static final Pattern DAY_PATTERN = Pattern.compile("([0-9一二三四五六七八九十两]+)\\s*[天日]");
    private static final Pattern DAY_ORDINAL_PATTERN = Pattern.compile("第([0-9一二三四五六七八九十两]+)[天日]");
    /** 「3天2晚」「三天两晚」这种带晚数的写法，是最可靠的行程时长信号 */
    private static final Pattern DAY_NIGHT_PATTERN =
            Pattern.compile("([0-9一二三四五六七八九十两]+)\\s*[天日]\\s*[0-9一二三四五六七八九十两]+\\s*[晚夜]");

    /**
     * 交给智能体前先声明补全要求。
     * 游记原文经常只有一串地点名，没有时间点、玩多久、先后顺序，模型也就跟着留空；
     * 先说清楚要它自己安排，再用后端兜底（fillMissingVisitTimes）保证一定补上。
     */
    private static final String FILL_INSTRUCTION =
            "请把下面的内容整理成一份结构化行程，要求：\n"
                    + "1) 原文如果没写时间点、每个地点的游玩时长或先后顺序，请结合地点之间的距离、"
                    + "景点开放时间和常规游览时长自行合理安排并补齐，这几项都不要留空；\n"
                    + "2) 游玩顺序以原文出现的先后为准；原文没写顺序就按地理位置顺路安排；\n"
                    + "3) 每个地点的 visit_time_range 一律用 HH:mm-HH:mm 格式，同一天内按时间先后递增。\n\n";
    private static final String CN_NUM = "一二三四五六七八九十";

    @Override
    public TripPlanVO importFromLink(TripImportLinkDTO dto) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("请先登录后创建行程");
        }

        String sourceUrl = dto.getSourceUrl().trim();
        TripPlan tripPlan = buildImportedTripPlan(userId, sourceUrl);
        tripPlanMapper.insert(tripPlan);
        log.info("用户{}通过链接导入行程成功，requestUserId={}，tripId={}", userId, dto.getUserId(), tripPlan.getId());
        return toVO(tripPlan);
    }

    @Override
    public TripPlanVO getById(Long id) {
        Long userId = BaseContext.getCurrentId();
        TripPlan tripPlan = tripPlanMapper.selectById(id);
        if (tripPlan == null || !tripPlan.getUserId().equals(userId)) {
            throw new BaseException("行程不存在");
        }
        return toVO(tripPlan);
    }

    @Override
    public List<TripPlanVO> listMine() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("请先登录");
        }
        LambdaQueryWrapper<TripPlan> wrapper = new LambdaQueryWrapper<TripPlan>()
                .eq(TripPlan::getUserId, userId)
                .orderByDesc(TripPlan::getCreateTime);
        List<TripPlan> list = tripPlanMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<TripPlanVO> result = new ArrayList<>(list.size());
        for (TripPlan tp : list) {
            result.add(toVO(tp));
        }
        return result;
    }

    @Override
    public TripPlanVO save(TripPlanVO vo) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BaseException("请先登录后创建行程");
        }
        TripPlan entity = toEntity(vo);
        entity.setUserId(userId);
        if (entity.getShareCount() == null) entity.setShareCount(0);
        if (entity.getLikeCount() == null) entity.setLikeCount(0);
        if (entity.getVisibility() == null) entity.setVisibility(0);
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getSourceType() == null) entity.setSourceType(2); // AI 生成
        if (entity.getCreateTime() == null) entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());

        // 本条业务数据是否携带（新的）AI 会话 id：决定要不要触发画像摘要重写。
        // 没有会话说明不是 AI 对话产出的，不该沉淀；
        // 会话没变说明只是重新保存同一份行程，重复调摘模型没意义。
        boolean needProfileRewrite;
        if (entity.getId() == null) {
            tripPlanMapper.insert(entity);
            needProfileRewrite = true;
            log.info("用户新建行程成功，userId={}，tripId={}", userId, entity.getId());
        } else {
            // 有 id：校验归属后更新
            TripPlan exist = tripPlanMapper.selectById(entity.getId());
            if (exist == null || !exist.getUserId().equals(userId)) {
                throw new BaseException("行程不存在");
            }
            needProfileRewrite = !java.util.Objects.equals(exist.getChatSessionId(), entity.getChatSessionId());
            entity.setUserId(userId);
            tripPlanMapper.updateById(entity);
            log.info("用户更新行程成功，userId={}，tripId={}", userId, entity.getId());
        }

        // 生成行程成功落库之后触发画像摘要重写（异步旁路，失败不影响已保存的行程）
        if (needProfileRewrite) {
            profileRewriteService.triggerAsync(userId, entity.getChatSessionId());
        }
        return toVO(entity);
    }

    @Override
    public TripPlanVO update(Long id, TripPlanVO vo) {
        Long userId = BaseContext.getCurrentId();
        TripPlan exist = tripPlanMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new BaseException("行程不存在");
        }
        TripPlan entity = toEntity(vo);
        entity.setId(id);
        entity.setUserId(userId);
        entity.setUpdateTime(LocalDateTime.now());
        tripPlanMapper.updateById(entity);
        return toVO(tripPlanMapper.selectById(id));
    }

    @Override
    public void delete(Long id) {
        Long userId = BaseContext.getCurrentId();
        TripPlan exist = tripPlanMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new BaseException("行程不存在");
        }
        tripPlanMapper.deleteById(id);
        log.info("用户删除行程成功，userId={}，tripId={}", userId, id);
    }

    /**
     * 只解析外部内容生成行程数据，不落库（管理端「精选行程」用）。
     * 优先级：用户粘的「行程原文」 > 自动抓取的链接正文 > 只按城市生成。
     */
    @Override
    public TripPlanVO parseLink(String sourceUrl, String city, String content) {
        String url = sourceUrl == null ? "" : sourceUrl.trim();
        String destCity = city == null ? "" : city.trim();
        String text = content == null ? "" : content.trim();
        boolean textFromUser = !text.isEmpty();

        // 城市没手填时，退回从链接里识别（多数分享链接里没有中文城市名，识别不到）
        if (destCity.isEmpty()) {
            String extracted = extractCityFromSourceUrl(url);
            if (extracted != null) {
                destCity = extracted;
            }
        }

        // 只给了链接时，自动抓一次网页正文，让「粘个链接就能生成」成立
        String fetchedText = null;
        if (text.isEmpty()) {
            WebPageFetcher.Page page = WebPageFetcher.fetch(url);
            if (page != null) {
                text = page.getText();
                fetchedText = text;
                log.info("已自动抓取链接正文：url={}，title={}，chars={}", url, page.getTitle(), text.length());
            }
        }

        if (destCity.isEmpty() && text.isEmpty()) {
            throw new BaseException("没能从该链接取到正文，请把游记正文粘进「行程原文」，或填写目的地城市后再解析");
        }
        TripPlan parsed = buildParsedTripPlan(url, destCity, text);
        log.info("解析外部内容生成行程（不落库）：city={}，原文来源={}，字数={}，title={}",
                destCity, textFromUser ? "用户粘贴" : (fetchedText != null ? "自动抓取" : "无"),
                text.length(), parsed.getTitle());
        // 走到兜底草稿说明 AI 没生成出来（超时/失败）。这时不能装作成功——
        // 空草稿会让管理端以为"生成了但没内容"，要明确报错让人重试。
        if (parsed.getDays() == null || parsed.getDays() <= 0) {
            throw new BaseException("AI 生成行程超时或失败，请稍后重试；也可以先把游记正文粘进「行程原文」再解析");
        }
        TripPlanVO vo = toVO(parsed);
        // 把抓到的正文回传，管理端会填进「行程原文」，让人看到这次到底拿什么生成的
        vo.setSourceText(fetchedText);
        return vo;
    }

    /**
     * 解析外部内容生成行程（不落库）：
     * 有「行程原文」就按原文生成，只有城市就按城市生成；都失败则返回空草稿。
     */
    private TripPlan buildParsedTripPlan(String url, String city, String content) {
        if (!city.isEmpty() || !content.isEmpty()) {
            TripPlanDTO plan = generatePlanViaAgent(city, content);
            if (plan != null) {
                return toTripFromAgentPlan(plan, url, city);
            }
        }
        TripPlan draft = buildDraftTripPlan(null, url);
        if (!city.isEmpty()) {
            draft.setToCity(city);
            draft.setTitle(city + "行程草稿");
        }
        return draft;
    }

    /**
     * 调智能体生成行程（一次调用成型，与 App 内 AI 生成链路一致）。
     *
     * 注意：不要再走「mode=frame + mode=detail」两段式。2026-09 之后服务端那份智能体
     * 的 /api/plan 已经没有 mode 参数了（FastAPI 会忽略未知查询参数），两段式会让
     * 同一条规划管线白跑两遍，耗时翻倍并撞上读取超时。
     * 游记正文放 chat_history（新管线以它为唯一参考来提取需求/骨架/原文），
     * 同时放 context_note 备用。
     *
     * @param userInput 用户上下文（如抓取/粘进来的游记原文）；为空则仅按城市生成
     */
    private TripPlanDTO generatePlanViaAgent(String city, String userInput) {
        try {
            ChatRequestDTO req = new ChatRequestDTO();
            // 智能体侧这几个字段是必填：fastjson 默认丢弃 null 字段，漏了会直接 422
            req.setSession_id("featured_" + System.currentTimeMillis());

            int days = guessDays(userInput);
            String startDay = TripDateUtil.todayStartDay();
            BaseInfoDTO base = new BaseInfoDTO();
            base.setDeparture_city("");
            base.setDestination_city(city);
            // start_day / end_date 也是必填（智能体校验日期连续递增），按「今天出发 + days 天」推算
            base.setStart_day(startDay);
            base.setEnd_date(TripDateUtil.endDateOf(startDay, days));
            base.setDays(days);
            base.setHobby(Collections.emptyList());
            base.setPeople_num("");
            base.setBudget("");
            // 游记正文走智能体专门的外部上下文入口；同时先声明补全要求
            String agentInput = userInput == null || userInput.trim().isEmpty()
                    ? "" : FILL_INSTRUCTION + userInput.trim();
            base.setContext_note(agentInput);
            req.setBase_info(base);
            req.setUser_input(agentInput);
            // 关键：只放 user_input 时智能体基本按城市通用生成、不采纳原文；
            // 放进 chat_history 才是 App 多轮对话的真实形态，生成阶段会把它当作上下文
            if (!agentInput.isEmpty()) {
                ChatMessageDTO msg = new ChatMessageDTO();
                msg.setRole("user");
                msg.setContent(agentInput);
                req.setChat_history(Collections.singletonList(msg));
            }

            // 一次调用成型：新管线直接跑 researcher -> planner -> inspector，不再分帧/明细两步
            PlanResponseDTO resp = agentHttpUtil.callPlan(req);
            if (resp != null && resp.getPlan_data() != null) {
                return resp.getPlan_data();
            }
            log.warn("生成行程：智能体未返回行程（city={}）", city);
        } catch (Exception e) {
            log.warn("生成行程失败（city={}）：{}", city, e.getMessage());
        }
        return null;
    }

    /**
     * 从行程原文里粗略识别天数，识别不到按 3 天。
     * 依次尝：「3天2晚」这类带晚数的写法 → 明确的「N天/N日」→「第一天…第N天」的最大序号。
     * 两种要绕开的情况：「第一天」里的「一天」是序号不是时长；「5天有效」说的是票券有效期。
     */
    private int guessDays(String text) {
        if (text == null || text.isEmpty()) {
            return 3;
        }
        String t = text.replaceAll("\\s+", "");

        Matcher dayNight = DAY_NIGHT_PATTERN.matcher(t);
        if (dayNight.find()) {
            int n = toDayCount(dayNight.group(1));
            if (n > 0) {
                return n;
            }
        }

        Matcher m = DAY_PATTERN.matcher(t);
        while (m.find()) {
            if (m.start() > 0 && t.charAt(m.start() - 1) == '第') {
                continue;
            }
            if (t.startsWith("有效", m.end())) {
                continue;
            }
            int n = toDayCount(m.group(1));
            if (n > 0) {
                return n;
            }
        }

        int maxOrdinal = 0;
        Matcher om = DAY_ORDINAL_PATTERN.matcher(t);
        while (om.find()) {
            int n = toDayCount(om.group(1));
            if (n > maxOrdinal) {
                maxOrdinal = n;
            }
        }
        return maxOrdinal > 0 ? maxOrdinal : 3;
    }

    /** 天数文本 → 数字，超出 1~30 或认不出返回 0 */
    private int toDayCount(String token) {
        if (token == null || token.isEmpty()) {
            return 0;
        }
        String s = token.trim();
        int n = s.matches("[0-9]+") ? Integer.parseInt(s) : cnToInt(s);
        return (n >= 1 && n <= 30) ? n : 0;
    }

    /** 中文数字 → 整数，支持「三」「两」「十」「十二」「二十三」；认不出返回 0 */
    private int cnToInt(String s) {
        if ("两".equals(s)) {
            return 2;
        }
        if (!s.matches("[一二三四五六七八九十]+")) {
            return 0;
        }
        int section = 0;
        for (char c : s.toCharArray()) {
            if (c == '十') {
                section = (section == 0 ? 1 : section) * 10;
            } else {
                section += CN_NUM.indexOf(c) + 1;
            }
        }
        return section;
    }

    /** 智能体返回的计划 → 行程实体（天数按实际返回的天数算，不再写死 3） */
    private TripPlan toTripFromAgentPlan(TripPlanDTO plan, String sourceUrl, String city) {
        JSONArray days = buildDayPlansFromAgent(plan);
        TripPlan tripPlan = new TripPlan();
        tripPlan.setTitle(plan.getTitle() != null && !plan.getTitle().isEmpty()
                ? plan.getTitle()
                : (city.isEmpty() ? "行程草稿" : city + "行程"));
        tripPlan.setFromCity("");
        tripPlan.setToCity(city);
        tripPlan.setDays(days.isEmpty() ? 3 : days.size());
        tripPlan.setPeople(1);
        tripPlan.setBudget(BigDecimal.ZERO);
        tripPlan.setEstimatedCost(BigDecimal.ZERO);
        tripPlan.setHotel("");
        tripPlan.setTags(JSON.toJSONString(new JSONArray()));
        tripPlan.setDayPlans(JSON.toJSONString(days));
        tripPlan.setCover("");
        tripPlan.setSourceUrl(sourceUrl);
        tripPlan.setSourceType(3);
        tripPlan.setVisibility(0);
        tripPlan.setStatus(2);
        tripPlan.setShareCount(0);
        tripPlan.setLikeCount(0);
        tripPlan.setCreateTime(LocalDateTime.now());
        tripPlan.setUpdateTime(LocalDateTime.now());
        return tripPlan;
    }

    /**
     * 从导入链接构建行程：
     * 1. 尝试从链接中识别目的地城市，识别到则调用 AI Agent 生成该城市的行程；
     * 2. 识别不到或生成失败时返回空草稿（不写死任何城市，绝不回落肇庆模板）。
     */
    private TripPlan buildImportedTripPlan(Long userId, String sourceUrl) {
        String city = extractCityFromSourceUrl(sourceUrl);
        // 链接里通常没有城市名，先尝试抓一次网页正文，抓到了就按正文生成真实行程
        WebPageFetcher.Page page = WebPageFetcher.fetch(sourceUrl);
        String articleText = page == null ? "" : page.getText();
        if (page != null) {
            log.info("链接导入：已抓到网页正文，title={}，chars={}", page.getTitle(), articleText.length());
        }
        if (city != null || !articleText.isEmpty()) {
            TripPlanDTO plan = generatePlanViaAgent(city == null ? "" : city, articleText);
            if (plan != null) {
                TripPlan tripPlan = toTripFromAgentPlan(plan, sourceUrl, city == null ? "" : city);
                tripPlan.setUserId(userId);
                log.info("链接导入：已由 AI 生成行程（识别城市={}，正文={}字）",
                        city, articleText.length());
                return tripPlan;
            }
            log.warn("链接导入：AI 生成失败（目的地 {}），返回空草稿", city);
        } else {
            log.info("链接导入：既没识别到目的地也没抓到正文，返回空草稿，sourceUrl={}", sourceUrl);
        }
        return buildDraftTripPlan(userId, sourceUrl);
    }

    /** 无法解析目的地/生成失败时的兜底草稿：不包含任何写死的城市内容 */
    private TripPlan buildDraftTripPlan(Long userId, String sourceUrl) {
        TripPlan tripPlan = new TripPlan();
        tripPlan.setUserId(userId);
        tripPlan.setTitle("导入的行程（待解析目的地）");
        tripPlan.setFromCity("");
        tripPlan.setToCity("");
        tripPlan.setDays(0);
        tripPlan.setPeople(1);
        tripPlan.setBudget(BigDecimal.ZERO);
        tripPlan.setEstimatedCost(BigDecimal.ZERO);
        tripPlan.setHotel("");
        tripPlan.setTags(JSON.toJSONString(new JSONArray()));
        tripPlan.setDayPlans(JSON.toJSONString(new JSONArray()));
        tripPlan.setCover("");
        tripPlan.setSourceUrl(sourceUrl);
        tripPlan.setSourceType(3);
        tripPlan.setVisibility(0);
        tripPlan.setStatus(2);
        tripPlan.setShareCount(0);
        tripPlan.setLikeCount(0);
        tripPlan.setCreateTime(LocalDateTime.now());
        tripPlan.setUpdateTime(LocalDateTime.now());
        return tripPlan;
    }

    /** 将 Agent 返回的 TripPlanDTO（day_list）转换为前端 TripDayPlan 结构的 JSON 数组 */
    private JSONArray buildDayPlansFromAgent(TripPlanDTO plan) {
        JSONArray days = new JSONArray();
        if (plan.getDay_list() == null) {
            return days;
        }
        // 原文常常没写时间点/时长/顺序，先补全再看板，避免前端只能显示默认的 09:00-11:00
        fillMissingVisitTimes(plan.getDay_list());
        int dayIndex = 1;
        for (DayScheduleDTO dayDTO : plan.getDay_list()) {
            JSONObject dayObj = new JSONObject();
            dayObj.put("day", dayIndex);
            dayObj.put("title", dayDTO.getDate() != null && !dayDTO.getDate().isEmpty()
                    ? dayDTO.getDate() : "第" + dayIndex + "天");
            JSONArray schedules = new JSONArray();
            if (dayDTO.getSchedule() != null) {
                for (AttractionDTO attraction : dayDTO.getSchedule()) {
                    JSONObject s = new JSONObject();
                    s.put("time", attraction.getVisit_time_range());
                    s.put("title", attraction.getSpot_name());
                    s.put("duration", durationText(attraction.getVisit_time_range()));
                    s.put("description", String.format("📍%s | 🕒%s | 💰%s",
                            attraction.getLocation() != null ? attraction.getLocation() : "待定",
                            attraction.getOpen_time() != null ? attraction.getOpen_time() : "全天",
                            attraction.getTicket() != null ? attraction.getTicket() : "详询现场"));
                    s.put("type", "scenic");
                    s.put("openTime", attraction.getOpen_time());
                    s.put("ticket", attraction.getTicket());
                    s.put("location", attraction.getLocation());
                    s.put("featureTag", attraction.getFeature_tag());
                    schedules.add(s);
                }
            }
            dayObj.put("schedules", schedules);
            days.add(dayObj);
            dayIndex++;
        }
        return days;
    }

    /** 每段默认 2 小时、一天从 09:00 开始：与 App 行程详情页拖拽排序后的重排规则一致 */
    private static final int DAY_START_MIN = 9 * 60;
    private static final int SLOT_MINUTES = 120;
    /** 相邻两段之间留的通勤/缓冲时间 */
    private static final int TRANSIT_MINUTES = 30;
    /** 一天的收尾时间，避免排到半夜 */
    private static final int DAY_END_MIN = 22 * 60 + 30;

    private static final Pattern TIME_RANGE_PATTERN =
            Pattern.compile("([01]?\\d|2[0-3])[:：]([0-5]\\d)\\s*[-~至到]\\s*([01]?\\d|2[0-3])[:：]([0-5]\\d)");
    private static final Pattern SINGLE_TIME_PATTERN = Pattern.compile("([01]?\\d|2[0-3])[:：]([0-5]\\d)");

    /**
     * 补全每个地点的游玩时间段。
     *
     * 原文（尤其是游记）常常只有一串地点名，没有时间点、时长和先后关系，
     * 模型有时也会漏填，结果前端只能按默认值渲染成一堆同样的 09:00-11:00。
     * 这里按固定规则补：以列表顺序为游玩顺序，第 i 段从 09:00 + i×2h 起，
     * 每段 2 小时、段间留 30 分钟通勤；原文/模型给了模糊时段（上午/下午/晚上）就按时段起点对齐，
     * 给了单个时间点就按 2 小时补齐。已经写清楚的时间段原样保留。
     */
    private void fillMissingVisitTimes(List<DayScheduleDTO> dayList) {
        for (DayScheduleDTO day : dayList) {
            List<AttractionDTO> spots = day.getSchedule();
            if (spots == null || spots.isEmpty()) {
                continue;
            }
            int cursor = -1; // 上一段的结束时间
            for (int i = 0; i < spots.size(); i++) {
                AttractionDTO spot = spots.get(i);
                int[] range = parseTimeRange(spot.getVisit_time_range());
                int start = range != null ? range[0] : indexBasedStart(spot.getVisit_time_range(), i);
                int end = range != null ? range[1] : start + SLOT_MINUTES;
                int duration = Math.max(SLOT_MINUTES, end - start);

                // 数组顺序即游玩顺序：时间必须递增、段间留通勤时间，
                // 否则界面上的时间线会倒着走或几段挤在同一时刻
                if (cursor >= 0 && start < cursor + TRANSIT_MINUTES) {
                    start = cursor + TRANSIT_MINUTES;
                    end = start + duration;
                }
                // 别排到半夜：超出收尾时间就压缩当天最后一段（一天塞太多地点时的兜底）
                if (end > DAY_END_MIN) {
                    end = DAY_END_MIN;
                    if (end - start < 30) {
                        start = Math.max(DAY_START_MIN, end - 30);
                    }
                    if (cursor >= 0 && start < cursor) {
                        start = Math.min(cursor, Math.max(DAY_START_MIN, DAY_END_MIN - 30));
                    }
                }
                spot.setVisit_time_range(fmtMinutes(start) + "-" + fmtMinutes(end));
                cursor = end;
            }
        }
    }

    /** 第 i 个地点的时间起点：按提示词（上午/中午/下午/傍晚/晚上）对齐，没提示就 09:00 + i×2h */
    private int indexBasedStart(String hint, int index) {
        if (hint != null) {
            String h = hint.trim();
            if (h.contains("中午") || h.contains("午间")) {
                return 12 * 60;
            }
            if (h.contains("下午")) {
                return 14 * 60;
            }
            if (h.contains("傍晚") || h.contains("日落") || h.contains("黄昏")) {
                return 17 * 60;
            }
            if (h.contains("晚上") || h.contains("夜")) {
                return 19 * 60;
            }
            if (h.contains("上午") || h.contains("早")) {
                return DAY_START_MIN;
            }
        }
        return DAY_START_MIN + index * SLOT_MINUTES;
    }

    /** 解析 "HH:mm-HH:mm"；解析不出返回 null */
    private int[] parseTimeRange(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String t = text.trim().replace('：', ':');
        Matcher m = TIME_RANGE_PATTERN.matcher(t);
        if (m.find()) {
            int start = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
            int end = Integer.parseInt(m.group(3)) * 60 + Integer.parseInt(m.group(4));
            if (end > start) {
                return new int[]{start, end};
            }
            return null;
        }
        // 只给了一个时间点（如「09:30」）→ 按默认时长补出结束时间
        Matcher single = SINGLE_TIME_PATTERN.matcher(t);
        if (single.find()) {
            int start = Integer.parseInt(single.group(1)) * 60 + Integer.parseInt(single.group(2));
            int end = Math.min(start + SLOT_MINUTES, DAY_END_MIN);
            if (end > start) {
                return new int[]{start, end};
            }
        }
        return null;
    }

    /** "HH:mm-HH:mm" → "约2小时"/"约2.5小时"（给前端和 AI 上下文一个显式时长） */
    private String durationText(String timeRange) {
        int[] range = parseTimeRange(timeRange);
        if (range == null) {
            return "约" + (SLOT_MINUTES / 60) + "小时";
        }
        int minutes = range[1] - range[0];
        double hours = minutes / 60.0;
        String value = hours == Math.floor(hours)
                ? String.valueOf((int) hours)
                : String.valueOf(Math.round(hours * 10) / 10.0);
        return "约" + value + "小时";
    }

    private String fmtMinutes(int totalMinutes) {
        int h = (totalMinutes / 60) % 24;
        int m = totalMinutes % 60;
        return String.format("%02d:%02d", h, m);
    }

    /** 从链接中提取目的地城市名；识别不到返回 null */
    private String extractCityFromSourceUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        Matcher m1 = CITY_WITH_SUFFIX.matcher(url);
        if (m1.find()) {
            return m1.group(1);
        }
        Matcher m2 = CITY_BEFORE_KEYWORD.matcher(url);
        if (m2.find()) {
            return m2.group(1).replaceAll(CITY_TRIM_PREFIX, "");
        }
        return null;
    }

    private TripPlanVO toVO(TripPlan tripPlan) {
        TripPlanVO vo = new TripPlanVO();
        vo.setId(tripPlan.getId());
        vo.setChatSessionId(tripPlan.getChatSessionId());
        vo.setUserId(tripPlan.getUserId());
        vo.setTitle(tripPlan.getTitle());
        vo.setFromCity(tripPlan.getFromCity());
        vo.setToCity(tripPlan.getToCity());
        vo.setStartDate(tripPlan.getStartDate());
        vo.setEndDate(tripPlan.getEndDate());
        vo.setDays(tripPlan.getDays());
        vo.setPeople(tripPlan.getPeople());
        vo.setBudget(tripPlan.getBudget());
        vo.setEstimatedCost(tripPlan.getEstimatedCost());
        vo.setHotel(tripPlan.getHotel());
        vo.setTags(JSON.parseArray(tripPlan.getTags(), String.class));
        vo.setDayPlans(JSON.parse(tripPlan.getDayPlans()));
        vo.setCover(tripPlan.getCover());
        vo.setSourceUrl(tripPlan.getSourceUrl());
        vo.setSourceType(tripPlan.getSourceType());
        vo.setVisibility(tripPlan.getVisibility());
        vo.setStatus(tripPlan.getStatus());
        vo.setShareCount(tripPlan.getShareCount());
        vo.setLikeCount(tripPlan.getLikeCount());
        vo.setCreateTime(tripPlan.getCreateTime());
        vo.setUpdateTime(tripPlan.getUpdateTime());
        return vo;
    }

    /** VO -> 实体：tags(List) -> JSON 字符串；dayPlans(Object) -> JSON 字符串 */
    private TripPlan toEntity(TripPlanVO vo) {
        if (vo == null) {
            return new TripPlan();
        }
        TripPlan entity = new TripPlan();
        entity.setId(vo.getId());
        entity.setChatSessionId(vo.getChatSessionId());
        entity.setUserId(vo.getUserId());
        entity.setTitle(vo.getTitle());
        entity.setFromCity(vo.getFromCity());
        entity.setToCity(vo.getToCity());
        entity.setStartDate(vo.getStartDate());
        entity.setEndDate(vo.getEndDate());
        entity.setDays(vo.getDays());
        entity.setPeople(vo.getPeople());
        entity.setBudget(vo.getBudget());
        entity.setEstimatedCost(vo.getEstimatedCost());
        entity.setHotel(vo.getHotel());
        // tags: List<String> -> JSON 字符串
        entity.setTags(vo.getTags() != null ? JSON.toJSONString(vo.getTags()) : null);
        // dayPlans: Object -> JSON 字符串（前端传的是数组对象）
        if (vo.getDayPlans() != null) {
            entity.setDayPlans(vo.getDayPlans() instanceof String
                    ? (String) vo.getDayPlans()
                    : JSON.toJSONString(vo.getDayPlans()));
        }
        entity.setCover(vo.getCover());
        entity.setSourceUrl(vo.getSourceUrl());
        entity.setSourceType(vo.getSourceType());
        entity.setVisibility(vo.getVisibility());
        entity.setStatus(vo.getStatus());
        entity.setShareCount(vo.getShareCount());
        entity.setLikeCount(vo.getLikeCount());
        entity.setSourceFeaturedId(vo.getSourceFeaturedId());
        entity.setCreateTime(vo.getCreateTime());
        entity.setUpdateTime(vo.getUpdateTime());
        return entity;
    }
}
