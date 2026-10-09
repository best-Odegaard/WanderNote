package com.gkv.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行程票务（这条行程怎么去、怎么回）。
 *
 * 为什么单独一张表，而不是往 trip_plan 上加两个字段：
 *   一个行程有「去程 + 返程」两条记录，字段化就要写 depNo/retNo/depTime/retTime… 十来个列，
 *   而且「单程」还得靠一堆 null 表达。两张行记录 + direction 更直白，
 *   也和 trip_hotel 的做法保持一致。
 *
 * 一个行程每个方向只留一条记录（(trip_id, direction) 唯一索引），service 层做 upsert。
 * ticket_code 只做关联线索，不建外键：候选库下架某个班次后，
 * 历史行程仍要能显示车次与时刻，所以这里是冗余存储。
 *
 * ★ 合规：本表**不存任何用户身份凭证**（没有 12306 账号密码字段，将来也不许加）。
 */
@Data
@TableName("trip_ticket")
public class TripTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 行程id（trip_plan.id） */
    private Long tripId;

    /** 方向：outbound=去程 return=返程。单程就是「只有 outbound」。 */
    private String direction;

    /**
     * 交通方式：train=火车票 flight=飞机票 drive=自驾。
     *
     * 自驾是「无班次方式」：ticket_no / seat_class / purchase_url 都为空，
     * 但 duration_min 与 distance_km 必须有值 —— 否则卡片上只剩「自驾」两个字，
     * 用户看不出要开多久、多远（见 TicketUrlBuilder.isModeOnly）。
     */
    private String transportType;

    /** 候选班次编码；用户用的是兜底班次（库里没有）时为空 */
    private String ticketCode;

    /** 车次号/航班号 */
    private String ticketNo;

    private String carrier;

    private String fromCity;
    private String toCity;
    private String fromStation;
    private String toStation;

    private LocalDate departDate;

    private String departTime;
    private String arriveTime;

    private Integer durationMin;

    /** 里程（公里）：地图规划值；拿不到时由前端按直线距离估算 */
    private BigDecimal distanceKm;

    private String seatClass;

    /** 参考价（元/人，以 12306 或携程为准） */
    private BigDecimal refPrice;

    /** 购买深链（TicketUrlBuilder 生成，落库便于追溯历史链接） */
    private String purchaseUrl;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
