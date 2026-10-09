package com.gkv.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 班次候选（/ticket/search、/ticket/select、/ticket/trip/{tripId} 的返回结构）。
 *
 * 字段刻意与前端 utils/tickets.ts 的 TicketOption 一一对应：
 * 接口可用时前端直接用这份数据，接口不可用/该线路未收录时前端用手写兜底表 ——
 * 两边结构一致，页面与行程卡片不用写两套逻辑。
 *
 * 价格语义（重要）：
 *   price 是**参考价**，不是实时票价、不代表有票。
 *   前端展示必须带「以 12306 / 携程为准」，禁止出现「有票」「可订」字样。
 *
 * 合规语义（同样重要）：
 *   本 VO 里没有任何证件号/账号字段，也不做站内下单 ——
 *   purchaseUrl 只负责把人送到 12306 或携程，交易在那里完成。
 */
@Data
public class TicketVO {

    /** 业务编码（ticket_code）；兜底班次没有编码，前端用本地 id */
    private String id;

    /**
     * 方向：outbound=去程 return=返程。
     * /ticket/search 不填（搜索本身不分方向），/select 与 /trip/{tripId} 会填。
     */
    private String direction;

    /** 票种/方式：train=火车票 flight=飞机票 drive=自驾 */
    private String transportType;

    /** 承运方（铁路局 / 航空公司） */
    private String carrier;

    /** 车次号（C7001）或航班号（CZ3101） */
    private String ticketNo;

    private String fromCity;
    private String toCity;

    /** 出发站/机场 */
    private String fromStation;

    /** 到达站/机场 */
    private String toStation;

    /** 出发日期（YYYY-MM-DD），可空（用户日期还没定时） */
    private String departDate;

    /** 出发时刻 HH:mm */
    private String departTime;

    /** 到达时刻 HH:mm */
    private String arriveTime;

    /** 历时（分钟） */
    private Integer durationMin;

    /** 里程（公里）：地图规划值或直线估算；自驾这类无班次方式主要靠它表达「多远」 */
    private BigDecimal distanceKm;

    /** 席别/舱位（二等座 / 经济舱）；自驾等无班次方式为空 */
    private String seatClass;

    /** 参考价（元/人），以 12306 或携程为准 */
    private BigDecimal price;

    /** 经停次数，0=直达 */
    private Integer stops;

    private List<String> tags;

    /**
     * 购买深链（TicketUrlBuilder 单一出口生成）。
     * 为空表示班次信息不足以拼出一条能用的链接，前端应给出「去官网查」的兜底而不是死链。
     */
    private String purchaseUrl;

    // ── 以下字段只有 /ticket/select 与 /ticket/trip/{tripId} 会填 ──

    /** 该票所属的行程id */
    private Long tripId;
}
