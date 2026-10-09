package com.gkv.service;

import com.gkv.dto.TicketSelectDTO;
import com.gkv.vo.TicketVO;

import java.util.List;

/**
 * 订票（火车票 / 飞机票）服务。
 *
 * 边界（与酒店那条链路一致，也是合规要求）：
 *   - 只做「候选查询 + 选定落库」，交易一律跳 12306 / 携程深链，**不做站内下单、不代购**。
 *   - 价格是参考价，接口不做「有票/可订」这类承诺（字段层面就没有余票与舱位库存）。
 *   - 绝不收集/存储用户 12306 账号、证件号等购票凭证。
 */
public interface TicketService {

    /**
     * 查候选班次，每条附购买深链。
     *
     * @param transportType train=火车票 flight=飞机票；为空则不限制票种
     * @param fromCity      出发城市
     * @param toCity        到达城市
     * @param date          出发日期（YYYY-MM-DD，可空），仅用于拼深链
     * @return 候选列表；该线路未收录时返回**空列表**（前端降级为「去官网查」按钮）
     */
    List<TicketVO> search(String transportType, String fromCity, String toCity, String date);

    /**
     * 选定班次：写 trip_ticket（按 (tripId, direction) upsert）。
     * dto.tripId 为空时只做校验与深链生成，不落库（行程还没保存）。
     */
    TicketVO selectForTrip(TicketSelectDTO dto);

    /**
     * 读行程已选的票（去程/返程各一条，可能只有一条或没有）。
     * 行程详情页靠它把「怎么去、怎么回」显示出来（杀进程后也能恢复）。
     */
    List<TicketVO> getTripTickets(Long tripId);

    /**
     * 清除行程某个方向的票（direction 为空则两个方向都清）。
     * 用户把去程换成单程、或者干脆不坐这趟时用得到。
     */
    void clearForTrip(Long tripId, String direction);
}
