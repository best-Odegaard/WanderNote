package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.context.BaseContext;
import com.gkv.dto.HotelSelectDTO;
import com.gkv.entity.HotelCandidate;
import com.gkv.entity.TripHotel;
import com.gkv.entity.TripPlan;
import com.gkv.exception.BaseException;
import com.gkv.mapper.HotelCandidateMapper;
import com.gkv.mapper.TripHotelMapper;
import com.gkv.mapper.TripPlanMapper;
import com.gkv.service.HotelService;
import com.gkv.utils.CtripUrlBuilder;
import com.gkv.vo.HotelVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 酒店候选查询 + 选定落库。
 *
 * 三个刻意的「宁可放宽，也不返回空」决定（都来自真实使用场景）：
 *   1. 档次筛选命中 0 家 → 放宽到全部档次。
 *      用户在「高档型」没有候选的城市会看到空列表，只会以为功能坏了；
 *      给一批其他档次的候选 + 价格摆在那里，他还能自己挑。
 *   2. 位置关键词（area）命中 0 家 → 放宽到全城。
 *      输入的地标名与候选库里的写法不一致是常态（「七星岩」vs「七星岩景区」），
 *      模糊匹配失败就退全城，比空列表有用。
 *   3. 城市精确匹配 0 家 → 退一次模糊匹配（城市名带「市」后缀等）。
 *
 * 反过来有一处「宁可放弃」：城市与关键词都为空时不构造携程深链（返回 null），
 * 前端会隐藏「携程预订」按钮 —— 给死链比不给更伤。
 */
@Service
@Slf4j
public class HotelServiceImpl implements HotelService {

    /** 档次「不限」的几种写法：问卷里是「无要求」，筛选栏里是「不限」 */
    private static final String LEVEL_ANY = "不限";
    private static final String LEVEL_NO_PREFERENCE = "无要求";

    /** city 为空时的通用候选上限（避免把整张候选表倒给前端） */
    private static final int GENERIC_LIMIT = 12;

    @Autowired
    private HotelCandidateMapper hotelCandidateMapper;

    @Autowired
    private TripHotelMapper tripHotelMapper;

    @Autowired
    private TripPlanMapper tripPlanMapper;

    @Autowired
    private CtripUrlBuilder ctripUrlBuilder;

    @Override
    public List<HotelVO> search(String city, String checkin, String checkout, String style, String area) {
        LocalDate in = parseDate(checkin);
        LocalDate out = parseDate(checkout);
        if (in != null && out != null && !out.isAfter(in)) {
            // 离店不晚于入住：多半是行程日期还没定，宁可链接里不带日期，也不要一条搜不出结果的链接
            log.warn("[Hotel] 忽略不合法的入住/离店日期：checkin={}, checkout={}", checkin, checkout);
            in = null;
            out = null;
        }
        // lambda 捕获要求 effectively final，这里定稿一次
        final LocalDate inDate = in;
        final LocalDate outDate = out;

        List<HotelCandidate> rows = queryCandidates(city);
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }

        List<HotelCandidate> filtered = filterByLevel(rows, style);
        filtered = filterByArea(filtered, area);

        // 「风格匹配度 + 距目标地标距离」排序：档次已被上面过滤，
        // 这里按距离升序（近的优先），距离缺失的排最后，距离相同看运营配的 sort_order。
        List<HotelCandidate> sorted = new ArrayList<>(filtered);
        sorted.sort(Comparator
                .comparing((HotelCandidate h) -> h.getDistanceKm() == null ? 1 : 0)
                .thenComparing(h -> h.getDistanceKm() == null ? BigDecimal.ZERO : h.getDistanceKm())
                .thenComparing(h -> h.getSortOrder() == null ? 0 : h.getSortOrder()));

        return sorted.stream().map(h -> toVO(h, inDate, outDate)).collect(Collectors.toList());
    }

    @Override
    public HotelVO selectForTrip(HotelSelectDTO dto) {
        if (dto == null) {
            throw new BaseException("请选择酒店");
        }

        // 1) 先按编码取候选；取不到就用传入字段兜底（未收录城市的通用候选）
        HotelCandidate candidate = null;
        if (notBlank(dto.getHotelCode())) {
            candidate = hotelCandidateMapper.selectOne(new LambdaQueryWrapper<HotelCandidate>()
                    .eq(HotelCandidate::getHotelCode, dto.getHotelCode())
                    .last("limit 1"));
        }
        TripPlan trip = dto.getTripId() == null ? null : requireOwnedTrip(dto.getTripId());

        String hotelName;
        String city;
        String address;
        String styleTags;
        BigDecimal lng;
        BigDecimal lat;
        BigDecimal price;
        String landmark;

        if (candidate != null) {
            hotelName = candidate.getName();
            city = candidate.getCity();
            address = candidate.getAddress();
            styleTags = candidate.getStyleTags();
            lng = candidate.getLng();
            lat = candidate.getLat();
            price = candidate.getRefPrice();
            landmark = candidate.getNearbyLandmark();
        } else {
            // 候选库没有这家（前端兜底候选 / 运营还没录）：以传入字段为准
            if (isBlank(dto.getName())) {
                throw new BaseException("酒店不存在");
            }
            hotelName = dto.getName();
            city = notBlank(dto.getCity()) ? dto.getCity() : (trip != null ? trip.getToCity() : null);
            address = dto.getAddress();
            styleTags = dto.getLevel();
            lng = dto.getLng();
            lat = dto.getLat();
            price = dto.getPrice();
            landmark = dto.getNearbyLandmark();
        }

        // 2) 日期：DTO 没给就用行程的起止日期（深链里带上真实日期才有意义）
        LocalDate in = parseDate(dto.getCheckin());
        LocalDate out = parseDate(dto.getCheckout());
        if (in == null && trip != null) {
            in = parseDate(trip.getStartDate());
        }
        if (out == null && trip != null) {
            out = parseDate(trip.getEndDate());
        }
        if (in != null && out != null && !out.isAfter(in)) {
            log.warn("[Hotel] 行程日期不合法（checkin={}, checkout={}），深链不带日期", in, out);
            in = null;
            out = null;
        }

        String ctripUrl = ctripUrlBuilder.build(city, in, out, hotelName, landmark);

        // 3) 落库：行程还没保存时（tripId 为空）只返回结果，由前端在保存行程后再调一次
        if (trip != null) {
            upsertTripHotel(trip.getId(), candidate, hotelName, address, styleTags, lng, lat, price, ctripUrl, in, out);
            writeBackTripHotelName(trip.getId(), hotelName);
        }

        HotelVO vo = new HotelVO();
        vo.setId(candidate != null ? candidate.getHotelCode() : null);
        vo.setName(hotelName);
        vo.setCity(city);
        vo.setLevel(candidate != null ? candidate.getLevel() : dto.getLevel());
        vo.setRating(candidate != null ? candidate.getRating() : null);
        vo.setPrice(price);
        vo.setAddress(address);
        vo.setNearbyLandmark(landmark);
        vo.setDistanceKm(candidate != null ? candidate.getDistanceKm() : null);
        vo.setTags(splitTags(styleTags));
        vo.setCover(candidate != null ? candidate.getCover() : null);
        vo.setLng(lng);
        vo.setLat(lat);
        vo.setDesc(candidate != null ? candidate.getIntro() : null);
        vo.setCtripUrl(ctripUrl);
        vo.setTripId(trip == null ? null : trip.getId());
        vo.setCheckin(in == null ? null : in.toString());
        vo.setCheckout(out == null ? null : out.toString());
        return vo;
    }

    @Override
    public HotelVO getTripHotel(Long tripId) {
        if (tripId == null) {
            return null;
        }
        requireOwnedTrip(tripId);
        TripHotel saved = tripHotelMapper.selectOne(new LambdaQueryWrapper<TripHotel>()
                .eq(TripHotel::getTripId, tripId)
                .last("limit 1"));
        if (saved == null) {
            return null;
        }

        HotelVO vo = new HotelVO();
        vo.setId(saved.getHotelCode());
        vo.setName(saved.getHotelName());
        vo.setTripId(saved.getTripId());
        vo.setAddress(saved.getAddress());
        vo.setLng(saved.getLng());
        vo.setLat(saved.getLat());
        vo.setPrice(saved.getRefPrice());
        vo.setTags(splitTags(saved.getStyleTags()));
        vo.setCheckin(saved.getCheckin() == null ? null : saved.getCheckin().toString());
        vo.setCheckout(saved.getCheckout() == null ? null : saved.getCheckout().toString());
        // 深链落过库就直接用（历史链接可追溯）；没落过（老数据）现算一条
        vo.setCtripUrl(notBlank(saved.getCtripUrl())
                ? saved.getCtripUrl()
                : ctripUrlBuilder.build(null, saved.getCheckin(), saved.getCheckout(), saved.getHotelName(), null));

        // 补齐展示字段：候选库还有这家就带上档次/评分/封面，下架了也不影响名称与坐标
        if (notBlank(saved.getHotelCode())) {
            HotelCandidate candidate = hotelCandidateMapper.selectOne(new LambdaQueryWrapper<HotelCandidate>()
                    .eq(HotelCandidate::getHotelCode, saved.getHotelCode())
                    .last("limit 1"));
            if (candidate != null) {
                vo.setCity(candidate.getCity());
                vo.setLevel(candidate.getLevel());
                vo.setRating(candidate.getRating());
                vo.setCover(candidate.getCover());
                vo.setNearbyLandmark(candidate.getNearbyLandmark());
                vo.setDistanceKm(candidate.getDistanceKm());
                vo.setDesc(candidate.getIntro());
            }
        }
        return vo;
    }

    // ────────────────────────── 内部实现 ──────────────────────────

    /**
     * 取候选：先精确匹配城市，空则退一次模糊匹配。
     * city 为空时返回少量通用候选（按运营排序），用于「目的地还没定」的场景。
     */
    private List<HotelCandidate> queryCandidates(String city) {
        if (isBlank(city)) {
            return hotelCandidateMapper.selectList(new LambdaQueryWrapper<HotelCandidate>()
                    .eq(HotelCandidate::getStatus, 1)
                    .orderByAsc(HotelCandidate::getSortOrder)
                    .last("limit " + GENERIC_LIMIT));
        }
        String cityName = city.trim();
        List<HotelCandidate> rows = hotelCandidateMapper.selectList(new LambdaQueryWrapper<HotelCandidate>()
                .eq(HotelCandidate::getStatus, 1)
                .eq(HotelCandidate::getCity, cityName)
                .orderByAsc(HotelCandidate::getSortOrder));
        if (!rows.isEmpty()) {
            return rows;
        }
        // 「肇庆市」/「肇庆 」这类写法差异，退一次模糊匹配
        return hotelCandidateMapper.selectList(new LambdaQueryWrapper<HotelCandidate>()
                .eq(HotelCandidate::getStatus, 1)
                .like(HotelCandidate::getCity, cityName)
                .orderByAsc(HotelCandidate::getSortOrder));
    }

    /** 档次过滤；命中为空时放宽到全部（不返回空列表） */
    private List<HotelCandidate> filterByLevel(List<HotelCandidate> rows, String style) {
        if (isBlank(style) || LEVEL_ANY.equals(style.trim()) || LEVEL_NO_PREFERENCE.equals(style.trim())) {
            return rows;
        }
        String want = style.trim();
        List<HotelCandidate> hit = rows.stream()
                .filter(h -> want.equals(h.getLevel()))
                .collect(Collectors.toList());
        if (hit.isEmpty()) {
            log.info("[Hotel] 档次 {} 无候选，已放宽到全部档次", want);
            return rows;
        }
        return hit;
    }

    /** 位置过滤（地标/地址/名称模糊）；命中为空时放宽到全城 */
    private List<HotelCandidate> filterByArea(List<HotelCandidate> rows, String area) {
        if (isBlank(area)) {
            return rows;
        }
        String keyword = area.trim();
        List<HotelCandidate> hit = rows.stream()
                .filter(h -> contains(h.getNearbyLandmark(), keyword)
                        || contains(h.getAddress(), keyword)
                        || contains(h.getName(), keyword))
                .collect(Collectors.toList());
        if (hit.isEmpty()) {
            log.info("[Hotel] 位置关键词「{}」无候选，已放宽到全城", keyword);
            return rows;
        }
        return hit;
    }

    private HotelVO toVO(HotelCandidate h, LocalDate in, LocalDate out) {
        HotelVO vo = new HotelVO();
        vo.setId(h.getHotelCode());
        vo.setName(h.getName());
        vo.setCity(h.getCity());
        vo.setLevel(h.getLevel());
        vo.setRating(h.getRating());
        vo.setPrice(h.getRefPrice());
        vo.setAddress(h.getAddress());
        vo.setNearbyLandmark(h.getNearbyLandmark());
        vo.setDistanceKm(h.getDistanceKm());
        vo.setTags(splitTags(h.getStyleTags()));
        vo.setCover(h.getCover());
        vo.setLng(h.getLng());
        vo.setLat(h.getLat());
        vo.setDesc(h.getIntro());
        vo.setCtripUrl(ctripUrlBuilder.build(h.getCity(), in, out, h.getName(), h.getNearbyLandmark()));
        return vo;
    }

    /**
     * 写 trip_hotel（一个行程一条）。
     *
     * 先查后写是主路径；插入撞唯一索引（并发连点）时转成更新，
     * 保证「连点两次确认」不会变成 500。
     */
    private void upsertTripHotel(Long tripId, HotelCandidate candidate,
                                 String hotelName, String address, String styleTags,
                                 BigDecimal lng, BigDecimal lat, BigDecimal price, String ctripUrl,
                                 LocalDate in, LocalDate out) {
        TripHotel existing = tripHotelMapper.selectOne(new LambdaQueryWrapper<TripHotel>()
                .eq(TripHotel::getTripId, tripId)
                .last("limit 1"));

        TripHotel entity = existing != null ? existing : new TripHotel();
        entity.setTripId(tripId);
        entity.setHotelCode(candidate != null ? candidate.getHotelCode() : null);
        entity.setHotelName(hotelName);
        entity.setAddress(address);
        entity.setStyleTags(styleTags);
        entity.setLng(lng);
        entity.setLat(lat);
        entity.setRefPrice(price);
        entity.setCtripUrl(ctripUrl);
        entity.setCheckin(in);
        entity.setCheckout(out);

        try {
            if (existing != null) {
                tripHotelMapper.updateById(entity);
            } else {
                tripHotelMapper.insert(entity);
            }
        } catch (DuplicateKeyException e) {
            // 并发插入撞 uk_trip_hotel_trip：改成更新已存在的那条（后写覆盖，等价于用户最后一次选择）
            log.info("[Hotel] 并发写入住宿记录，转为更新：tripId={}", tripId);
            TripHotel current = tripHotelMapper.selectOne(new LambdaQueryWrapper<TripHotel>()
                    .eq(TripHotel::getTripId, tripId)
                    .last("limit 1"));
            if (current == null) {
                throw e;
            }
            entity.setId(current.getId());
            tripHotelMapper.updateById(entity);
        }
    }

    /** 回写 trip_plan.hotel（名称）：行程卡片/分享等旧链路都读它 */
    private void writeBackTripHotelName(Long tripId, String hotelName) {
        TripPlan patch = new TripPlan();
        patch.setId(tripId);
        patch.setHotel(hotelName);
        tripPlanMapper.updateById(patch);
    }

    /** 行程必须存在且属于当前用户，否则不越权改别人的行程 */
    private TripPlan requireOwnedTrip(Long tripId) {
        TripPlan trip = tripPlanMapper.selectById(tripId);
        if (trip == null) {
            throw new BaseException("行程不存在");
        }
        Long currentUserId = BaseContext.getCurrentId();
        if (trip.getUserId() != null && currentUserId != null && !trip.getUserId().equals(currentUserId)) {
            throw new BaseException("无权操作该行程");
        }
        return trip;
    }

    private List<String> splitTags(String tags) {
        if (isBlank(tags)) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (String t : tags.split("[,，]")) {
            if (notBlank(t)) {
                list.add(t.trim());
            }
        }
        return list;
    }

    private LocalDate parseDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            // 兼容 "2026-10-01" 与带时间的 "2026-10-01 08:00:00"
            return LocalDate.parse(raw.trim().length() > 10 ? raw.trim().substring(0, 10) : raw.trim());
        } catch (DateTimeParseException e) {
            log.warn("[Hotel] 日期解析失败，按无日期处理：{}", raw);
            return null;
        }
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.contains(keyword);
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private boolean isBlank(String s) {
        return !notBlank(s);
    }
}
