package com.gkv.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gkv.context.BaseContext;
import com.gkv.dto.TicketSelectDTO;
import com.gkv.entity.TicketCandidate;
import com.gkv.entity.TripPlan;
import com.gkv.entity.TripTicket;
import com.gkv.exception.BaseException;
import com.gkv.mapper.TicketCandidateMapper;
import com.gkv.mapper.TripPlanMapper;
import com.gkv.mapper.TripTicketMapper;
import com.gkv.service.TicketService;
import com.gkv.utils.TicketUrlBuilder;
import com.gkv.vo.TicketVO;
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
 * 订票 / 行程交通候选查询 + 选定落库。
 *
 * 一条贯穿全类的语义：「有班次的票」与「无班次的方式」走同一条落库链路，
 * 区别只在 TicketUrlBuilder.isModeOnly()：
 *   · 有班次（train/flight）：有车次号、有 12306/携程查询页、可展开具体班次；
 *   · 无班次（drive 自驾）：没有车次号与查询页，靠 duration_min + distance_km 表达「多久、多远」。
 * 这样行程里始终记着「怎么去」，不会因为某个线路没有班次数据就空着。
 *
 * 三处刻意的取舍（都来自真实使用场景，与酒店那条链路保持同一套判断）：
 *   1. 候选库查不到这条线路 → 返回**空列表**，而不是硬凑几条无关的车次/航班。
 *      酒店能返回「XX市中心便捷酒店」这种通用候选，是因为「住一晚」这个需求到哪都成立；
 *      「广州 → 肇庆」的车次放到别的线路上就是错的，凑数比空着更糟。
 *      空列表会由前端降级成「查 X→Y 的票 →」按钮，直接跳 12306/携程 ——
 *      数据源未就绪时功能依然可用（见 产品项目文档 5.7.1 的降级策略）。
 *   2. 城市名做一次归一化（去「市/省/区」后缀）+ 一次包含匹配，避免
 *      「广州市」查不到「广州」的数据这种一眼假的问题。
 *   3. 出发地与目的地任一为空 → 直接返回空列表：没有方向的班次列表对用户毫无意义。
 *
 * ★ 合规：本类不生成下单链接、不存任何购票凭证（字段层面就没有这些列）。
 */
@Service
@Slf4j
public class TicketServiceImpl implements TicketService {

    /** 城市名归一化时允许出现且不参与匹配的后缀 */
    private static final String[] CITY_SUFFIXES = {"特别行政区", "自治区", "市", "省", "区", "县"};

    /** 模糊匹配时最多扫多少行（候选库是精选库，量级很小；给个上限防止将来膨胀） */
    private static final int FUZZY_SCAN_LIMIT = 500;

    @Autowired
    private TicketCandidateMapper ticketCandidateMapper;

    @Autowired
    private TripTicketMapper tripTicketMapper;

    @Autowired
    private TripPlanMapper tripPlanMapper;

    @Autowired
    private TicketUrlBuilder ticketUrlBuilder;

    @Override
    public List<TicketVO> search(String transportType, String fromCity, String toCity, String date) {
        // 没有方向就没有可查的班次：返回空列表，由前端给「去官网查」的出路
        if (isBlank(fromCity) || isBlank(toCity)) {
            return new ArrayList<>();
        }
        LocalDate departDate = parseDate(date);

        List<TicketCandidate> rows = queryCandidates(transportType, fromCity, toCity);
        if (rows.isEmpty()) {
            log.info("[Ticket] 候选库无此线路：type={}, {} → {}，前端将降级为跳转 12306/携程",
                    transportType, fromCity, toCity);
            return new ArrayList<>();
        }

        // 排序：先按出发时刻（早的在前，用户最关心「几点走」），同刻按运营配的 sort_order
        List<TicketCandidate> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator
                .comparing((TicketCandidate t) -> t.getDepartTime() == null ? "99:99" : t.getDepartTime())
                .thenComparing(t -> t.getSortOrder() == null ? 0 : t.getSortOrder()));

        return sorted.stream()
                .map(t -> toVO(t, departDate))
                .collect(Collectors.toList());
    }

    @Override
    public TicketVO selectForTrip(TicketSelectDTO dto) {
        if (dto == null) {
            throw new BaseException("请选择班次");
        }
        String direction = normalizeDirection(dto.getDirection());

        // 1) 先按编码取候选；取不到就用传入字段兜底（未收录线路的兜底班次）
        TicketCandidate candidate = null;
        if (notBlank(dto.getTicketCode())) {
            candidate = ticketCandidateMapper.selectOne(new LambdaQueryWrapper<TicketCandidate>()
                    .eq(TicketCandidate::getTicketCode, dto.getTicketCode())
                    .last("limit 1"));
        }
        TripPlan trip = dto.getTripId() == null ? null : requireOwnedTrip(dto.getTripId());

        // 方式先定下来：候选库有就以库为准，否则以 DTO 为准
        // （不改写 dto，后面还要用 dto.getTransportType() 判断「无班次方式」）
        String dtoType = notBlank(dto.getTransportType()) ? dto.getTransportType() : TicketUrlBuilder.TYPE_TRAIN;
        boolean modeOnly = TicketUrlBuilder.isModeOnly(candidate != null ? candidate.getTransportType() : dtoType);

        String transportType;
        String carrier;
        String ticketNo;
        String fromCity;
        String toCity;
        String fromStation;
        String toStation;
        String departTime;
        String arriveTime;
        Integer durationMin;
        BigDecimal distanceKm;
        String seatClass;
        BigDecimal price;

        if (candidate != null) {
            transportType = candidate.getTransportType();
            carrier = candidate.getCarrier();
            ticketNo = candidate.getTicketNo();
            fromCity = candidate.getFromCity();
            toCity = candidate.getToCity();
            fromStation = candidate.getFromStation();
            toStation = candidate.getToStation();
            departTime = candidate.getDepartTime();
            arriveTime = candidate.getArriveTime();
            durationMin = candidate.getDurationMin();
            // 候选库没有里程（票务不需要），要展示里程时由前端按地图算
            distanceKm = null;
            seatClass = candidate.getSeatClass();
            price = candidate.getRefPrice();
        } else {
            // 候选库没有这班（前端兜底表 / 运营还没录）：以传入字段为准。
            // 无班次方式（自驾）本来就没有车次号，不能按「班次不存在」拒掉。
            if (isBlank(dto.getTicketNo()) && !modeOnly) {
                throw new BaseException("班次不存在");
            }
            transportType = dtoType;
            carrier = dto.getCarrier();
            ticketNo = dto.getTicketNo();
            // 方向决定出发/到达：DTO 没给时按方向从行程推，避免选返程却把出发地写成目的地
            fromCity = notBlank(dto.getFromCity()) ? dto.getFromCity() : (trip == null ? null : cityOf(trip, direction, true));
            toCity = notBlank(dto.getToCity()) ? dto.getToCity() : (trip == null ? null : cityOf(trip, direction, false));
            fromStation = dto.getFromStation();
            toStation = dto.getToStation();
            departTime = dto.getDepartTime();
            arriveTime = dto.getArriveTime();
            durationMin = dto.getDurationMin();
            distanceKm = dto.getDistanceKm();
            seatClass = dto.getSeatClass();
            price = dto.getPrice();
        }

        // 2) 日期：DTO 没给就按方向取行程的起/止日期（去程=出发日，返程=返程日）
        LocalDate departDate = parseDate(dto.getDepartDate());
        if (departDate == null && trip != null) {
            departDate = parseDate(TicketUrlBuilder.DIRECTION_RETURN.equals(direction)
                    ? trip.getEndDate()
                    : trip.getStartDate());
        }

        String purchaseUrl = ticketUrlBuilder.build(transportType, fromCity, toCity,
                fromStation, toStation, ticketNo, departDate);

        // 3) 落库：行程还没保存时（tripId 为空）只返回结果，由前端在保存行程后再调一次
        if (trip != null) {
            upsertTripTicket(trip.getId(), direction, candidate, transportType, ticketNo, carrier,
                    fromCity, toCity, fromStation, toStation,
                    departDate, departTime, arriveTime, durationMin, distanceKm, seatClass, price, purchaseUrl);
        }

        TicketVO vo = new TicketVO();
        vo.setId(candidate != null ? candidate.getTicketCode() : null);
        vo.setDirection(direction);
        vo.setTransportType(transportType);
        vo.setCarrier(carrier);
        vo.setTicketNo(ticketNo);
        vo.setFromCity(fromCity);
        vo.setToCity(toCity);
        vo.setFromStation(fromStation);
        vo.setToStation(toStation);
        vo.setDepartDate(departDate == null ? null : departDate.toString());
        vo.setDepartTime(departTime);
        vo.setArriveTime(arriveTime);
        vo.setDurationMin(durationMin);
        vo.setDistanceKm(distanceKm);
        vo.setSeatClass(seatClass);
        vo.setPrice(price);
        vo.setStops(candidate != null ? candidate.getStops() : null);
        vo.setTags(candidate != null ? splitTags(candidate.getTags()) : new ArrayList<>());
        vo.setPurchaseUrl(purchaseUrl);
        vo.setTripId(trip == null ? null : trip.getId());
        return vo;
    }

    @Override
    public List<TicketVO> getTripTickets(Long tripId) {
        if (tripId == null) {
            return new ArrayList<>();
        }
        requireOwnedTrip(tripId);
        List<TripTicket> rows = tripTicketMapper.selectList(new LambdaQueryWrapper<TripTicket>()
                .eq(TripTicket::getTripId, tripId));
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        // 去程在前、返程在后：页面上的顺序就是用户的行程顺序
        List<TripTicket> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(t -> TicketUrlBuilder.DIRECTION_RETURN.equals(t.getDirection()) ? 1 : 0));

        List<TicketVO> list = new ArrayList<>();
        for (TripTicket row : sorted) {
            list.add(toVO(row));
        }
        return list;
    }

    @Override
    public void clearForTrip(Long tripId, String direction) {
        if (tripId == null) {
            return;
        }
        requireOwnedTrip(tripId);
        LambdaQueryWrapper<TripTicket> wrapper = new LambdaQueryWrapper<TripTicket>()
                .eq(TripTicket::getTripId, tripId);
        if (notBlank(direction)) {
            wrapper.eq(TripTicket::getDirection, normalizeDirection(direction));
        }
        int removed = tripTicketMapper.delete(wrapper);
        log.info("[Ticket] 清除行程票务：tripId={}, direction={}, 删除 {} 条", tripId, direction, removed);
    }

    // ────────────────────────── 内部实现 ──────────────────────────

    /**
     * 取候选：先按票种 + 精确城市匹配，空则退一次归一化/包含匹配。
     *
     * 为什么要退：用户嘴里的城市名与库里的写法经常差一个字（「广州市」vs「广州」），
     * 精确匹配失败就整块功能不可用，代价太大。
     */
    private List<TicketCandidate> queryCandidates(String transportType, String fromCity, String toCity) {
        String from = normalizeCity(fromCity);
        String to = normalizeCity(toCity);

        List<TicketCandidate> exact = ticketCandidateMapper.selectList(baseWrapper(transportType)
                .eq(TicketCandidate::getFromCity, from)
                .eq(TicketCandidate::getToCity, to));
        if (!exact.isEmpty()) {
            return exact;
        }

        // 退一次模糊：把该票种的候选拉一小批回来，在内存里按「归一化后包含」匹配。
        // 走 like 查不了这种「库里的值比用户输入短」的情况（'广州' like '%广州市%' 为假）。
        List<TicketCandidate> pool = ticketCandidateMapper.selectList(baseWrapper(transportType)
                .orderByAsc(TicketCandidate::getSortOrder)
                .last("limit " + FUZZY_SCAN_LIMIT));
        return pool.stream()
                .filter(t -> cityMatches(t.getFromCity(), from) && cityMatches(t.getToCity(), to))
                .collect(Collectors.toList());
    }

    private LambdaQueryWrapper<TicketCandidate> baseWrapper(String transportType) {
        LambdaQueryWrapper<TicketCandidate> wrapper = new LambdaQueryWrapper<TicketCandidate>()
                .eq(TicketCandidate::getStatus, 1);
        if (notBlank(transportType)) {
            wrapper.eq(TicketCandidate::getTransportType, transportType.trim());
        }
        return wrapper;
    }

    /** 归一化后互相包含即算命中（处理「广州」/「广州市」/「广州南」这类差异） */
    private boolean cityMatches(String dbCity, String want) {
        if (isBlank(dbCity) || isBlank(want)) {
            return false;
        }
        String a = normalizeCity(dbCity);
        String b = normalizeCity(want);
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    /** 去掉常见的行政区后缀，便于跨写法匹配 */
    private String normalizeCity(String raw) {
        String city = raw == null ? "" : raw.trim();
        for (String suffix : CITY_SUFFIXES) {
            if (city.length() > suffix.length() && city.endsWith(suffix)) {
                city = city.substring(0, city.length() - suffix.length());
                break;
            }
        }
        return city;
    }

    /**
     * 写 trip_ticket（一个行程每个方向一条）。
     *
     * 先查后写是主路径；插入撞唯一索引（并发连点）时转成更新，
     * 保证「连点两次确认」不会变成 500。
     */
    private void upsertTripTicket(Long tripId, String direction, TicketCandidate candidate,
                                  String transportType, String ticketNo, String carrier,
                                  String fromCity, String toCity, String fromStation, String toStation,
                                  LocalDate departDate, String departTime, String arriveTime,
                                  Integer durationMin, BigDecimal distanceKm, String seatClass,
                                  BigDecimal price, String purchaseUrl) {
        TripTicket existing = tripTicketMapper.selectOne(new LambdaQueryWrapper<TripTicket>()
                .eq(TripTicket::getTripId, tripId)
                .eq(TripTicket::getDirection, direction)
                .last("limit 1"));

        TripTicket entity = existing != null ? existing : new TripTicket();
        entity.setTripId(tripId);
        entity.setDirection(direction);
        entity.setTransportType(transportType);
        entity.setTicketCode(candidate != null ? candidate.getTicketCode() : null);
        entity.setTicketNo(ticketNo);
        entity.setCarrier(carrier);
        entity.setFromCity(fromCity);
        entity.setToCity(toCity);
        entity.setFromStation(fromStation);
        entity.setToStation(toStation);
        entity.setDepartDate(departDate);
        entity.setDepartTime(departTime);
        entity.setArriveTime(arriveTime);
        entity.setDurationMin(durationMin);
        entity.setDistanceKm(distanceKm);
        entity.setSeatClass(seatClass);
        entity.setRefPrice(price);
        entity.setPurchaseUrl(purchaseUrl);

        try {
            if (existing != null) {
                tripTicketMapper.updateById(entity);
            } else {
                tripTicketMapper.insert(entity);
            }
        } catch (DuplicateKeyException e) {
            // 并发插入撞 uk_trip_ticket_trip_dir：改成更新已存在的那条（后写覆盖，等价于用户最后一次选择）
            log.info("[Ticket] 并发写入票务记录，转为更新：tripId={}, direction={}", tripId, direction);
            TripTicket current = tripTicketMapper.selectOne(new LambdaQueryWrapper<TripTicket>()
                    .eq(TripTicket::getTripId, tripId)
                    .eq(TripTicket::getDirection, direction)
                    .last("limit 1"));
            if (current == null) {
                throw e;
            }
            entity.setId(current.getId());
            tripTicketMapper.updateById(entity);
        }
    }

    /** 候选库 → VO（搜索链路） */
    private TicketVO toVO(TicketCandidate t, LocalDate date) {
        TicketVO vo = new TicketVO();
        vo.setId(t.getTicketCode());
        vo.setTransportType(t.getTransportType());
        vo.setCarrier(t.getCarrier());
        vo.setTicketNo(t.getTicketNo());
        vo.setFromCity(t.getFromCity());
        vo.setToCity(t.getToCity());
        vo.setFromStation(t.getFromStation());
        vo.setToStation(t.getToStation());
        vo.setDepartDate(date == null ? null : date.toString());
        vo.setDepartTime(t.getDepartTime());
        vo.setArriveTime(t.getArriveTime());
        vo.setDurationMin(t.getDurationMin());
        vo.setSeatClass(t.getSeatClass());
        vo.setPrice(t.getRefPrice());
        vo.setStops(t.getStops());
        vo.setTags(splitTags(t.getTags()));
        vo.setPurchaseUrl(ticketUrlBuilder.buildFor(t, date));
        return vo;
    }

    /** 已落库记录 → VO（行程详情链路）；候选库还有这班就补齐展示字段，下架了也不影响车次与时刻 */
    private TicketVO toVO(TripTicket row) {
        TicketVO vo = new TicketVO();
        vo.setId(row.getTicketCode());
        vo.setDirection(row.getDirection());
        vo.setTransportType(row.getTransportType());
        vo.setCarrier(row.getCarrier());
        vo.setTicketNo(row.getTicketNo());
        vo.setFromCity(row.getFromCity());
        vo.setToCity(row.getToCity());
        vo.setFromStation(row.getFromStation());
        vo.setToStation(row.getToStation());
        vo.setDepartDate(row.getDepartDate() == null ? null : row.getDepartDate().toString());
        vo.setDepartTime(row.getDepartTime());
        vo.setArriveTime(row.getArriveTime());
        vo.setDurationMin(row.getDurationMin());
        vo.setDistanceKm(row.getDistanceKm());
        vo.setSeatClass(row.getSeatClass());
        vo.setPrice(row.getRefPrice());
        vo.setTripId(row.getTripId());
        // 深链落过库就直接用（历史链接可追溯）；没落过（老数据）现算一条
        vo.setPurchaseUrl(notBlank(row.getPurchaseUrl())
                ? row.getPurchaseUrl()
                : ticketUrlBuilder.build(row.getTransportType(), row.getFromCity(), row.getToCity(),
                row.getFromStation(), row.getToStation(), row.getTicketNo(), row.getDepartDate()));

        if (notBlank(row.getTicketCode())) {
            TicketCandidate candidate = ticketCandidateMapper.selectOne(new LambdaQueryWrapper<TicketCandidate>()
                    .eq(TicketCandidate::getTicketCode, row.getTicketCode())
                    .last("limit 1"));
            if (candidate != null) {
                vo.setStops(candidate.getStops());
                vo.setTags(splitTags(candidate.getTags()));
                if (vo.getPrice() == null) {
                    vo.setPrice(candidate.getRefPrice());
                }
            }
        }
        if (vo.getTags() == null) {
            vo.setTags(new ArrayList<>());
        }
        return vo;
    }

    /** 按方向取行程城市：depart=true 取出发侧，false 取到达侧 */
    private String cityOf(TripPlan trip, String direction, boolean depart) {
        boolean back = TicketUrlBuilder.DIRECTION_RETURN.equals(direction);
        String from = back ? trip.getToCity() : trip.getFromCity();
        String to = back ? trip.getFromCity() : trip.getToCity();
        return depart ? from : to;
    }

    /** 方向归一化：只认 outbound / return，其它写法一律当去程（不因为一个脏参数就报错） */
    private String normalizeDirection(String direction) {
        String d = direction == null ? "" : direction.trim().toLowerCase();
        if ("return".equals(d) || "inbound".equals(d) || "back".equals(d) || "ret".equals(d)) {
            return TicketUrlBuilder.DIRECTION_RETURN;
        }
        return TicketUrlBuilder.DIRECTION_OUTBOUND;
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
            log.warn("[Ticket] 日期解析失败，按无日期处理：{}", raw);
            return null;
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private boolean isBlank(String s) {
        return !notBlank(s);
    }
}
