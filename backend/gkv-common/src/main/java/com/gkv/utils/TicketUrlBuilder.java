package com.gkv.utils;

import com.gkv.entity.TicketCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 订票深链构造器（火车票 → 12306，飞机票 → 携程）—— **全站唯一出口**。
 *
 * 为什么必须收敛到一个类：
 *   12306 与携程都没有面向第三方的公开开放 API，跳转只能靠 H5/App 深链，
 *   而深链参数名会随它们的前端改版而变。散落在前端各处，改版时要 grep 整个项目；
 *   这里是单点，改模板只动 application.yml 的 ticket.train/flight.url-template，编译都不用。
 *
 * ★ 参数模板都不是官方承诺，随时可能变 ★
 *   内置的 DEFAULT_* 是按 12306 车票查询页与携程 H5 机票搜索页的公开参数拼的**可用近似**，
 *   上线前请用真机跑一次，把核对后的模板写进 application.yml（会覆盖默认值）。
 *   即使默认模板需要修正，也不影响业务逻辑 —— 用户仍能通过
 *   「车站/机场 + 日期 + 车次」在落地页里自己查。
 *
 * 占位符（不区分大小写地原样替换）：
 *   {from}      出发站/机场（没有站名时退化为城市名）
 *   {to}        到达站/机场（没有站名时退化为城市名）
 *   {fromCity}  出发城市
 *   {toCity}    到达城市
 *   {date}      出发日期 YYYY-MM-DD，无则空（12306 会默认查当天）
 *   {no}        车次号/航班号（模板想按车次预筛时用得到）
 *
 * 降级行为：
 *   模板为空 → 用内置默认模板（不抛异常，保证按钮永远有链接）；
 *   出发与到达都为空 → 返回对应官网首页（宁可选，也不给一条搜不出结果的链接）。
 *
 * ★ 合规红线（不可妥协）★
 *   本类只生成「查询页」链接。绝不构造下单/支付链接，绝不拼接任何用户凭证，
 *   也不提供任何代购参数 —— 交易一律在 12306 / 携程自己的页面完成。
 */
@Component
@Slf4j
public class TicketUrlBuilder {

    /** 票种：火车票 */
    public static final String TYPE_TRAIN = "train";
    /** 票种：飞机票 */
    public static final String TYPE_FLIGHT = "flight";
    /**
     * 交通方式：自驾。
     *
     * 没有班次、没有查询页 —— 它是「方式」而不是「票」：
     * 里程与耗时由前端按地图规划（拿不到就按直线距离估算）算出来，
     * 本类对它一律返回 null（不给死链），前端据此隐藏跳转按钮。
     */
    public static final String TYPE_DRIVE = "drive";

    /** 方向：去程 */
    public static final String DIRECTION_OUTBOUND = "outbound";
    /** 方向：返程 */
    public static final String DIRECTION_RETURN = "return";

    /**
     * 是否是「无班次方式」（自驾 / 大巴 / 拼车 / 步行）。
     *
     * 用途有二，缺一不可：
     *   1. 不生成深链（这些方式没有可跳转的查询页）；
     *   2. service 层不再要求 ticket_no 非空 —— 自驾本来就没有车次号。
     *
     * 将来扩方式时只改这里，不用翻业务代码。
     */
    public static boolean isModeOnly(String transportType) {
        String t = transportType == null ? "" : transportType.trim().toLowerCase();
        return TYPE_DRIVE.equals(t) || "bus".equals(t) || "carpool".equals(t) || "walk".equals(t);
    }

    /**
     * 内置默认模板（可用近似）。
     *
     * ★ 上线前必须真机核对一次，把结果写进 application.yml 的 ticket.train.url-template。
     *   linktypeid=dc 表示「单程」查询页；flag=N,N,Y 是 12306 查询页的默认勾选位。
     */
    public static final String DEFAULT_TRAIN_TEMPLATE =
            "https://kyfw.12306.cn/otn/leftTicket/init?linktypeid=dc&fs={from}&ts={to}&date={date}&flag=N,N,Y";

    /**
     * 内置默认模板（可用近似）。
     *
     * ★ 携程 H5 机票搜索页参数以官方 URL 生成工具产出的结果为准，上线前真机核对。
     */
    public static final String DEFAULT_FLIGHT_TEMPLATE =
            "https://m.ctrip.com/webapp/flight/search?dcity={fromCity}&acity={toCity}&ddate={date}";

    /** 出发与到达都为空时的兜底：官网首页（永远可用，不会给人一条搜不出东西的链接） */
    public static final String TRAIN_HOME = "https://www.12306.cn/index/";
    public static final String FLIGHT_HOME = "https://m.ctrip.com/webapp/flight";

    @Value("${ticket.train.url-template:}")
    private String trainUrlTemplate;

    @Value("${ticket.flight.url-template:}")
    private String flightUrlTemplate;

    /** 火车票模板是否已显式配置（未配置时用内置默认，供上层打一次日志提醒） */
    public boolean isTrainConfigured() {
        return notBlank(trainUrlTemplate);
    }

    /** 飞机票模板是否已显式配置 */
    public boolean isFlightConfigured() {
        return notBlank(flightUrlTemplate);
    }

    /**
     * 候选班次 → 深链（service 层只调这一个入口）。
     *
     * @param ticket 候选班次；为空返回 null
     * @param date   出发日期，可空（12306 会默认查当天）
     */
    public String buildFor(TicketCandidate ticket, LocalDate date) {
        if (ticket == null) {
            return null;
        }
        return build(ticket.getTransportType(),
                ticket.getFromCity(), ticket.getToCity(),
                ticket.getFromStation(), ticket.getToStation(),
                ticket.getTicketNo(), date);
    }

    /**
     * 构造深链。
     *
     * @param transportType train / flight（其它值见下）；空值按火车票处理（兼容老数据）
     * @param fromCity      出发城市，可空
     * @param toCity        到达城市，可空
     * @param fromStation   出发站/机场，可空（空则用城市名）
     * @param toStation     到达站/机场，可空（空则用城市名）
     * @param ticketNo      车次号/航班号，可空
     * @param date          出发日期，可空
     * @return 深链；无班次方式（自驾等）返回 null；出发与到达都为空时返回对应官网首页
     */
    public String build(String transportType, String fromCity, String toCity,
                        String fromStation, String toStation, String ticketNo, LocalDate date) {
        // 自驾这类「方式」没有可查的班次页 —— 返回 null 让前端隐藏按钮，而不是给一条死链
        if (isModeOnly(transportType)) {
            log.debug("[TicketUrl] {} 是无班次方式，不生成购买深链", transportType);
            return null;
        }
        boolean flight = TYPE_FLIGHT.equalsIgnoreCase(trim(transportType));
        String from = notBlank(fromStation) ? fromStation.trim() : trim(fromCity);
        String to = notBlank(toStation) ? toStation.trim() : trim(toCity);

        if (isBlank(from) && isBlank(to)) {
            // 连去哪儿都不知道，参数化链接必然是空查询：直接给官网首页
            return flight ? FLIGHT_HOME : TRAIN_HOME;
        }

        String template = flight
                ? (notBlank(flightUrlTemplate) ? flightUrlTemplate : DEFAULT_FLIGHT_TEMPLATE)
                : (notBlank(trainUrlTemplate) ? trainUrlTemplate : DEFAULT_TRAIN_TEMPLATE);

        if (flight && !isFlightConfigured()) {
            log.debug("[TicketUrl] 未配置 ticket.flight.url-template，使用内置默认模板");
        }
        if (!flight && !isTrainConfigured()) {
            log.debug("[TicketUrl] 未配置 ticket.train.url-template，使用内置默认模板");
        }

        String url = template
                .replace("{from}", encode(from))
                .replace("{to}", encode(to))
                .replace("{fromCity}", encode(fromCity))
                .replace("{toCity}", encode(toCity))
                .replace("{date}", encode(formatDate(date)))
                .replace("{no}", encode(ticketNo));

        // 没被模板用到的可选参数会留下 "date=&no="，清洗掉，避免链接又长又脏
        return stripEmptyParams(url);
    }

    /**
     * 拼一条「只查这一程」的深链（供 select 时的兜底班次使用，字段可能不全）。
     */
    public String build(String transportType, String fromCity, String toCity, LocalDate date) {
        return build(transportType, fromCity, toCity, null, null, null, date);
    }

    /** 去掉值为空的查询参数，如 "?a=1&date=&b=2" → "?a=1&b=2" */
    private String stripEmptyParams(String url) {
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        String head = url.substring(0, q);
        String[] pairs = url.substring(q + 1).split("&");
        StringBuilder kept = new StringBuilder();
        for (String pair : pairs) {
            if (pair.endsWith("=") || isBlank(pair)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(pair);
        }
        return kept.length() > 0 ? head + "?" + kept : head;
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * URL 编码。用 %20 而不是 '+'：
     * 12306 与携程的 H5 页面在两个位置解析参数（前端路由 + 后端查询），
     * '+' 在其中一处会被当成字面加号，站名里带空格时会搜不到。
     */
    private String encode(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            return URLEncoder.encode(raw, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 必然存在，走到这里说明 JVM 环境异常；退化成原样返回，不让深链构造把主流程带崩
            log.warn("[TicketUrl] URL 编码失败，原样使用：{}", raw, e);
            return raw;
        }
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private boolean isBlank(String s) {
        return !notBlank(s);
    }
}
