package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订票候选（自建精选班次库，运营维护）—— 火车票与飞机票共用一张表。
 *
 * 为什么自建而不是拉实时班次：
 *   12306 与各航司都没有面向第三方的公开开放 API（见 产品项目文档 5.7.1）。
 *   本库只存「车次/航班号 + 时刻 + 参考价」这类静态信息，
 *   **刻意没有余票、舱位、库存字段** —— 有库存字段就会有人想在这里下单，
 *   那是合规红线（不代购、不存 12306 账号）。
 *
 * ticketCode 是业务主键（如 train-gz-zq-c7001），不是自增 id：
 *   前端 utils/tickets.ts 的本地兜底表用同一套 code，
 *   接口不可用时走本地兜底，用户上次选的那班还能按 code 对上号。
 *
 * transportType 只有两个取值，见 {@link com.gkv.utils.TicketUrlBuilder} 的常量：
 *   train（火车票）/ flight（飞机票）。
 */
@Data
@TableName("ticket_candidate")
public class TicketCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码（前端本地兜底表同款） */
    private String ticketCode;

    /** 票种：train=火车票 flight=飞机票 */
    private String transportType;

    /** 承运方（铁路局 / 航空公司），如「广铁集团」「南方航空」 */
    private String carrier;

    /** 车次号（C7001）或航班号（CZ3101） */
    private String ticketNo;

    private String fromCity;
    private String toCity;

    /** 出发站/机场（广州南 / 白云机场T2） */
    private String fromStation;

    /** 到达站/机场 */
    private String toStation;

    /** 出发时刻 HH:mm */
    private String departTime;

    /** 到达时刻 HH:mm */
    private String arriveTime;

    /** 历时（分钟） */
    private Integer durationMin;

    /** 席别/舱位（二等座 / 经济舱） */
    private String seatClass;

    /** 参考价（元/人）；不是实时价，展示时必须带「以 12306 / 携程为准」 */
    private BigDecimal refPrice;

    /** 经停次数，0=直达 */
    private Integer stops;

    /** 卖点标签，逗号分隔 */
    private String tags;

    private Integer sortOrder;

    /** 状态：0下架 1上架 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
