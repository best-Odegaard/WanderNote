package com.gkv.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.dto.FeaturedParseDTO;
import com.gkv.dto.FeaturedTripSaveDTO;
import com.gkv.entity.FeaturedTrip;
import com.gkv.exception.BaseException;
import com.gkv.mapper.FeaturedTripMapper;
import com.gkv.service.AdminFeaturedTripService;
import com.gkv.service.TripPlanService;
import com.gkv.vo.TripPlanVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 精选行程（管理端）实现
 */
@Service
@Slf4j
public class AdminFeaturedTripServiceImpl implements AdminFeaturedTripService {

    @Autowired
    private FeaturedTripMapper featuredTripMapper;

    @Autowired
    private TripPlanService tripPlanService;

    /** 智能体在核实不到景点坐标时会写入这个占位标记。它只对内部排查有意义，不该出现在地址/描述里 */
    private static final String COORD_PLACEHOLDER = "（坐标数据待核实）";

    /** description 是「📍地址 | 🕒开放时间 | 💰门票」拼好的整串，这段前缀就是地址段 */
    private static final String DESC_ADDR_PREFIX = "📍";

    @Override
    public List<FeaturedTrip> list() {
        return featuredTripMapper.selectList(new LambdaQueryWrapper<FeaturedTrip>()
                .orderByAsc(FeaturedTrip::getSortOrder)
                .orderByDesc(FeaturedTrip::getId));
    }

    @Override
    public void save(FeaturedTripSaveDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BaseException("标题不能为空");
        }
        if (dto.getTripJson() == null || dto.getTripJson().trim().isEmpty()) {
            throw new BaseException("行程内容不能为空，可先用「解析链接」生成");
        }
        // 校验 JSON 合法性，避免把坏数据存进库
        try {
            JSON.parse(dto.getTripJson());
        } catch (Exception e) {
            throw new BaseException("行程内容不是合法 JSON，请检查格式");
        }

        FeaturedTrip entity = new FeaturedTrip();
        entity.setId(dto.getId());
        entity.setTitle(dto.getTitle().trim());
        entity.setSubtitle(dto.getSubtitle());
        entity.setCity(dto.getCity());
        entity.setDays(dto.getDays());
        entity.setCover(dto.getCover());
        entity.setTripJson(dto.getTripJson());
        entity.setSourceUrl(dto.getSourceUrl());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setUpdateTime(LocalDateTime.now());

        if (entity.getId() == null) {
            entity.setCreateTime(LocalDateTime.now());
            featuredTripMapper.insert(entity);
            log.info("新增精选行程：{}", entity.getTitle());
        } else {
            if (featuredTripMapper.selectById(entity.getId()) == null) {
                throw new BaseException("精选行程不存在");
            }
            featuredTripMapper.updateById(entity);
            log.info("编辑精选行程：id={}，title={}", entity.getId(), entity.getTitle());
        }
    }

    @Override
    public void delete(Long id) {
        featuredTripMapper.deleteById(id);
        log.info("删除精选行程：id={}", id);
    }

    @Override
    public void status(Long id, Integer status) {
        if (featuredTripMapper.selectById(id) == null) {
            throw new BaseException("精选行程不存在");
        }
        FeaturedTrip upd = new FeaturedTrip();
        upd.setId(id);
        upd.setStatus(status == null ? 0 : status);
        upd.setUpdateTime(LocalDateTime.now());
        featuredTripMapper.updateById(upd);
        log.info("修改精选行程状态：id={}，status={}", id, status);
    }

    @Override
    public FeaturedTripSaveDTO parseLink(FeaturedParseDTO dto) {
        if (dto == null) {
            throw new BaseException("参数不能为空");
        }
        // 复用行程侧的解析逻辑：用户粘的原文优先，没粘就自动抓链接正文，都没有才只按城市生成
        TripPlanVO trip = tripPlanService.parseLink(dto.getSourceUrl(), dto.getCity(), dto.getContent());

        // 抓到的正文单独回传，不塞进行程 JSON（那是行程数据，不该混进几 KB 网页正文）
        String sourceText = trip.getSourceText();
        trip.setSourceText(null);

        FeaturedTripSaveDTO draft = new FeaturedTripSaveDTO();
        draft.setTitle(trip.getTitle());
        draft.setCity(trip.getToCity() != null ? trip.getToCity() : dto.getCity());
        draft.setDays(trip.getDays());
        draft.setSourceUrl(dto.getSourceUrl());
        draft.setSourceText(sourceText);
        String cleanTripJson = cleanTripJson(JSON.toJSONString(trip));
        draft.setTripJson(cleanTripJson);
        draft.setSortOrder(0);
        draft.setStatus(1);
        log.info("解析生成精选行程草稿：city={}，title={}，抓取正文字数={}",
                draft.getCity(), draft.getTitle(), sourceText == null ? 0 : sourceText.length());
        return draft;
    }

    /**
     * 清理行程 JSON 里智能体留下的占位标记。
     * 只做字符串替换是不够的，两个字段的语义不同：
     *   - location 是单个地址，清完为空就说明"这个景点没有可用地址"，应该把字段去掉，
     *     而不是留一个空字符串 —— 前端 geocode 用的是 `s.location || s.title`，
     *     空值能顺着回退到景点名去定位，留空串虽然也能兜底，但语义含糊、早晚踩坑。
     *   - description 是分段的整串，只替换会把「📍 | 🕒…」这种悬空的地址段留在里面，
     *     所以按 | 拆段，地址段清空后整段丢掉。
     */
    private String cleanTripJson(String tripJson) {
        try {
            JSONObject trip = JSON.parseObject(tripJson);
            JSONArray dayPlans = trip.getJSONArray("dayPlans");
            if (dayPlans == null) {
                return trip.toJSONString();
            }
            for (int i = 0; i < dayPlans.size(); i++) {
                JSONObject day = dayPlans.getJSONObject(i);
                if (day == null) {
                    continue;
                }
                JSONArray schedules = day.getJSONArray("schedules");
                if (schedules == null) {
                    continue;
                }
                for (int j = 0; j < schedules.size(); j++) {
                    Object item = schedules.get(j);
                    // 逐个判断类型：智能体万一返回了非对象的元素，也只跳过这一条，
                    // 不让整份行程放弃清洗（外面那层 catch 是最后的兜底）
                    if (item instanceof JSONObject) {
                        cleanSpot((JSONObject) item);
                    }
                }
            }
            return trip.toJSONString();
        } catch (Exception e) {
            log.warn("行程内容清洗失败，按原文返回：{}", e.getMessage());
            return tripJson;
        }
    }

    /** 清洗单个景点的 location 与 description */
    private void cleanSpot(JSONObject spot) {
        String location = spot.getString("location");
        if (location != null) {
            String cleaned = location.replace(COORD_PLACEHOLDER, "").trim();
            if (cleaned.isEmpty()) {
                spot.remove("location");
            } else {
                spot.put("location", cleaned);
            }
        }

        String description = spot.getString("description");
        if (description != null) {
            List<String> kept = new ArrayList<>();
            for (String segment : description.split("\\|")) {
                String text = segment.replace(COORD_PLACEHOLDER, "").trim();
                // 地址段清空后只剩一个 📍，这一段整体丢掉，避免留下悬空的图标与分隔符
                if (text.isEmpty() || DESC_ADDR_PREFIX.equals(text)) {
                    continue;
                }
                kept.add(text);
            }
            if (kept.isEmpty()) {
                spot.remove("description");
            } else {
                spot.put("description", String.join(" | ", kept));
            }
        }
    }
}
