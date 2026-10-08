package com.gkv.service;

import com.gkv.dto.HotelSelectDTO;
import com.gkv.vo.HotelVO;

import java.util.List;

/**
 * 酒店（住宿）服务。
 *
 * 边界：
 *   - 只做「候选查询 + 选定落库」，交易一律跳携程深链（无公开 API，不做站内下单）。
 *   - 价格是参考价，接口不做「有房/可订」这类承诺（字段层面就没有库存）。
 */
public interface HotelService {

    /**
     * 按城市 / 档次 / 位置查候选酒店，每条附携程深链。
     *
     * @param city     目的地城市；为空时返回少量通用候选（用于「目的地还没定」的场景）
     * @param checkin  入住日期（YYYY-MM-DD，可空），仅用于拼深链
     * @param checkout 离店日期（YYYY-MM-DD，可空）
     * @param style    档次（经济型/舒适型/高档型/特色民宿）；「不限」「无要求」或空 = 不过滤
     * @param area     位置关键词（地标/地址模糊匹配）；命中为空时自动放宽，不返回空列表
     */
    List<HotelVO> search(String city, String checkin, String checkout, String style, String area);

    /**
     * 选定酒店：写 trip_hotel（upsert）+ 回写 trip_plan.hotel。
     * dto.tripId 为空时只做校验与深链生成，不落库（行程还没保存）。
     */
    HotelVO selectForTrip(HotelSelectDTO dto);

    /**
     * 读行程已保存的住宿；没有则返回 null。
     * 详情页/路线页靠它恢复「酒店 → 各站 → 酒店」的闭环坐标（杀进程后也能恢复）。
     */
    HotelVO getTripHotel(Long tripId);
}
