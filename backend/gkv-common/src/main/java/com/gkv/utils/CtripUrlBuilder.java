package com.gkv.utils;

import com.gkv.entity.HotelCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 携程酒店深链构造器 —— **全站唯一出口**。
 *
 * 为什么必须收敛到一个类：
 *   携程没有公开 API，跳转只能靠 H5/App 深链，而深链的参数名随携程前端改版而变。
 *   散落在前端各处的字符串，改版时要靠 grep 全项目去找；这里是单点，
 *   改模板只动 application.yml 的 ctrip.hotel.url-template，编译都不用。
 *
 * 模板来源（不要臆造参数名）：
 *   参数模板以携程官方 URL 生成工具产出的结果为准
 *   （http://pages.ctrip.com/commerce/promote/common/tools/converturl/index.html）。
 *   本类内置的 DEFAULT_TEMPLATE 是按携程 H5 酒店列表页的公开参数拼的**可用近似**，
 *   上线前请用真机跑一次，把核对后的模板写进 application.yml（会覆盖默认值）。
 *   这样即使默认模板需要修正，也不影响代码逻辑。
 *
 * 占位符（不区分大小写地原样替换）：
 *   {city}      城市名（如「肇庆」）
 *   {checkin}   入住日期 YYYY-MM-DD，无则空
 *   {checkout}  离店日期 YYYY-MM-DD，无则空
 *   {keyword}   搜索关键词 = 酒店名 + 附近地标（用户要求「把地点和酒店名一起搜」）
 *   {name}      酒店名（单独用得到时）
 *   {landmark}  附近地标（单独用得到时）
 *
 * 降级行为：
 *   模板为空 → 用内置默认模板（不抛异常，保证按钮永远有链接）；
 *   城市与关键词都为空 → 返回 null，前端隐藏「携程预订」按钮（宁可不显示，也不给死链）。
 */
@Component
@Slf4j
public class CtripUrlBuilder {

    /**
     * 内置默认模板（可用近似）。
     *
     * ★ 参数名以携程官方 URL 生成工具产出的结果为准，上线前必须真机核对一次。
     *   核对后把结果写进 application.yml 的 ctrip.hotel.url-template，本常量保持不失联即可。
     */
    public static final String DEFAULT_TEMPLATE =
            "https://m.ctrip.com/webapp/hotel/hotellist?cityName={city}&checkin={checkin}&checkout={checkout}&keyword={keyword}";

    @Value("${ctrip.hotel.url-template:}")
    private String urlTemplate;

    /** 模板是否已显式配置（未配置时用内置默认，供上层打一次日志提醒） */
    public boolean isConfigured() {
        return notBlank(urlTemplate);
    }

    /**
     * 构造深链。
     *
     * @param city      城市名
     * @param checkin   入住日期，可空
     * @param checkout  离店日期，可空
     * @param hotelName 酒店名，可空
     * @param landmark  附近地标，可空
     * @return 深链；城市与关键词都为空时返回 null
     */
    public String build(String city, LocalDate checkin, LocalDate checkout, String hotelName, String landmark) {
        String keyword = joinKeyword(hotelName, landmark);
        if (isBlank(city) && isBlank(keyword)) {
            // 没有任何可检索信息，给出去也是一条搜不到东西的链接
            return null;
        }

        String template = notBlank(urlTemplate) ? urlTemplate : DEFAULT_TEMPLATE;
        if (!isConfigured()) {
            log.debug("[CtripUrl] 未配置 ctrip.hotel.url-template，使用内置默认模板");
        }

        String url = template
                .replace("{city}", encode(city))
                .replace("{checkin}", encode(formatDate(checkin)))
                .replace("{checkout}", encode(formatDate(checkout)))
                .replace("{keyword}", encode(keyword))
                .replace("{name}", encode(hotelName))
                .replace("{landmark}", encode(landmark));

        // 没被模板用到的可选参数会留下 "checkin=&checkout="，清洗掉，避免链接又长又脏
        url = stripEmptyParams(url);
        return url;
    }

    /** 候选库实体 → 深链（service 层只调这一个入口） */
    public String buildFor(HotelCandidate hotel, LocalDate checkin, LocalDate checkout) {
        if (hotel == null) {
            return null;
        }
        return build(hotel.getCity(), checkin, checkout, hotel.getName(), hotel.getNearbyLandmark());
    }

    /**
     * 关键词 = 酒店名 + 附近地标。
     *
     * 两段都有时拼起来（携程返回的范围更聚焦）；
     * 只有一段时用那一段；都没有时返回空串（由 build 决定是否直接放弃）。
     */
    private String joinKeyword(String hotelName, String landmark) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(hotelName)) {
            sb.append(hotelName.trim());
        }
        if (notBlank(landmark)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(landmark.trim());
        }
        return sb.toString();
    }

    /** 去掉值为空的查询参数，如 "?a=1&checkin=&b=2" → "?a=1&b=2" */
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
     * 携程的 H5 页面在两个位置解析参数（前端路由 + 后端查询），
     * '+' 在其中一处会被当成字面加号，城市名里带空格时会搜不到。
     */
    private String encode(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            return URLEncoder.encode(raw, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 必然存在，走到这里说明 JVM 环境异常；退化成原样返回，不让深链构造把主流程带崩
            log.warn("[CtripUrl] URL 编码失败，原样使用：{}", raw, e);
            return raw;
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private boolean isBlank(String s) {
        return !notBlank(s);
    }
}
