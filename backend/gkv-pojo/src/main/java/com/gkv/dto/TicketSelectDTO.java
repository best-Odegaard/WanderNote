package com.gkv.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 选定班次 / 交通方式（POST /ticket/select）。
 *
 * 三种调用方式（前两种与酒店那条链路一致）：
 *   1. 选了候选库里的班次 → 只传 ticketCode（其余字段忽略，以库里的为准）。
 *   2. 选了前端兜底班次（未收录的线路）→ 库里查不到，
 *      这时以本 DTO 上的字段为准（ticketNo / 时刻 / 价格由前端本地表提供）。
 *   3. 选了「无班次方式」（自驾 drive）→ ticketCode 与 ticketNo 都为空，
 *      只需 transportType + durationMin + distanceKm + price（都是估算值）。
 *      这条路径的存在意义：不是所有线路都该坐火车/飞机，但行程里同样要记上「怎么去」。
 *
 * 用户可能在「行程还没保存」时就选票（先在问卷后选票，再进对话生成行程）：
 * 这时 tripId 为空，后端只做校验与深链生成，不落库；前端在保存行程后补一次调用。
 *
 * ★ 合规：本 DTO **不许**增加证件号、手机号、12306 账号密码等字段 ——
 *   本站不代购、不收集购票凭证。
 */
@Data
@ApiModel(description = "选定班次 / 交通方式")
public class TicketSelectDTO {

    /** 行程id；行程还没保存时留空（后端只校验，不落库） */
    @ApiModelProperty("行程id，可空")
    private Long tripId;

    /** 方向：outbound=去程 return=返程 */
    @ApiModelProperty("方向：outbound/return")
    private String direction;

    /** 候选班次编码（ticket_candidate.ticket_code） */
    @ApiModelProperty("候选班次编码")
    private String ticketCode;

    // ── 以下是 ticketCode 查不到时的兜底字段 ──

    /** 票种/方式：train=火车票 flight=飞机票 drive=自驾（无班次，ticketNo 可空） */
    private String transportType;

    private String carrier;
    private String ticketNo;

    private String fromCity;
    private String toCity;
    private String fromStation;
    private String toStation;

    /** 出发日期（YYYY-MM-DD）；为空时后端按方向取行程的起/止日期 */
    private String departDate;

    private String departTime;
    private String arriveTime;
    private Integer durationMin;

    /** 里程（公里）：自驾这类无班次方式必填，前端按地图规划或直线距离估算 */
    private BigDecimal distanceKm;

    private String seatClass;
    private BigDecimal price;
}
