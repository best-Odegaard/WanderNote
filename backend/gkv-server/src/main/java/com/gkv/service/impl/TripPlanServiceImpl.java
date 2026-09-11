package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.context.BaseContext;
import com.gkv.dto.AgentRequestDTO;
import com.gkv.dto.AttractionDTO;
import com.gkv.dto.DayScheduleDTO;
import com.gkv.dto.PlanResponseDTO;
import com.gkv.dto.TripImportLinkDTO;
import com.gkv.dto.TripPlanDTO;
import com.gkv.entity.TripPlan;
import com.gkv.exception.BaseException;
import com.gkv.mapper.TripPlanMapper;
import com.gkv.service.TripPlanService;
import com.gkv.utils.AgentHttpUtil;
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

    /** 从链接中识别目的地城市：优先 "xx市"，其次 "xx旅游/游记/攻略/打卡/一日游/玩" 等模式 */
    private static final Pattern CITY_WITH_SUFFIX = Pattern.compile("([\\u4e00-\\u9fa5]{2,5}?)市");
    private static final Pattern CITY_BEFORE_KEYWORD = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,5}?)(?:旅游|游记|攻略|打卡|美食|[0-9一二三四五六日]+日游|玩)");
    private static final String CITY_TRIM_PREFIX = "^[去游]";

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

        if (entity.getId() == null) {
            tripPlanMapper.insert(entity);
            log.info("用户新建行程成功，userId={}，tripId={}", userId, entity.getId());
        } else {
            // 有 id：校验归属后更新
            TripPlan exist = tripPlanMapper.selectById(entity.getId());
            if (exist == null || !exist.getUserId().equals(userId)) {
                throw new BaseException("行程不存在");
            }
            entity.setUserId(userId);
            tripPlanMapper.updateById(entity);
            log.info("用户更新行程成功，userId={}，tripId={}", userId, entity.getId());
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
     * 从导入链接构建行程：
     * 1. 尝试从链接中识别目的地城市，识别到则调用 AI Agent 生成该城市的行程；
     * 2. 识别不到或生成失败时返回空草稿（不写死任何城市，绝不回落肇庆模板）。
     */
    private TripPlan buildImportedTripPlan(Long userId, String sourceUrl) {
        String city = extractCityFromSourceUrl(sourceUrl);
        if (city != null) {
            try {
                AgentRequestDTO req = new AgentRequestDTO();
                req.setDeparture_city("");
                req.setDestination_city(city);
                req.setStart_day(null);
                req.setEnd_date(null);
                req.setDays(3);
                req.setHobby(Collections.emptyList());
                req.setPeople_num("");
                req.setBudget("");
                PlanResponseDTO resp = agentHttpUtil.callAgent(req);
                if (resp != null && resp.getPlan_data() != null) {
                    TripPlanDTO plan = resp.getPlan_data();
                    TripPlan tripPlan = new TripPlan();
                    tripPlan.setUserId(userId);
                    tripPlan.setTitle(plan.getTitle() != null && !plan.getTitle().isEmpty()
                            ? plan.getTitle() : city + "3日游");
                    tripPlan.setFromCity("");
                    tripPlan.setToCity(city);
                    tripPlan.setDays(3);
                    tripPlan.setPeople(1);
                    tripPlan.setBudget(BigDecimal.ZERO);
                    tripPlan.setEstimatedCost(BigDecimal.ZERO);
                    tripPlan.setHotel("");
                    tripPlan.setTags(JSON.toJSONString(new JSONArray()));
                    tripPlan.setDayPlans(JSON.toJSONString(buildDayPlansFromAgent(plan)));
                    tripPlan.setCover("");
                    tripPlan.setSourceUrl(sourceUrl);
                    tripPlan.setSourceType(3);
                    tripPlan.setVisibility(0);
                    tripPlan.setStatus(2);
                    tripPlan.setShareCount(0);
                    tripPlan.setLikeCount(0);
                    tripPlan.setCreateTime(LocalDateTime.now());
                    tripPlan.setUpdateTime(LocalDateTime.now());
                    log.info("链接导入：从链接识别到目的地「{}」，已由 AI 生成行程", city);
                    return tripPlan;
                }
            } catch (Exception e) {
                log.warn("链接导入：AI 生成失败（目的地 {}），返回空草稿: {}", city, e.getMessage());
            }
        } else {
            log.info("链接导入：链接中未识别到目的地，返回空草稿，sourceUrl={}", sourceUrl);
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
        entity.setCreateTime(vo.getCreateTime());
        entity.setUpdateTime(vo.getUpdateTime());
        return entity;
    }
}
