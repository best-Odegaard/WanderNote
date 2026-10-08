package com.gkv.dto;

import lombok.Data;

/**
 * 选定酒店（POST /hotel/select）
 *
 * 两种用法：
 *   1. 选了候选库里的酒店 → 只传 hotelCode（其余字段忽略，以库里的为准）。
 *      这是主链路，保证「同一家店在任何入口看到的信息一致」。
 *   2. 选了前端兜底候选（未收录的城市，如「XX市中心便捷酒店」）→ 库里查不到，
 *      这时必须把 name/address 等一起传上来，后端按传入值落库。
 *      这类兜底酒店坐标可能是 0（前端通用兜底没有坐标），
 *      路线页据此不闭环 —— 行为与「未选酒店」一致，不会画出一条通向 (0,0) 的假路线。
 *
 * tripId 可空：
 *   用户可能在「行程还没保存」时就选酒店（先在问卷后选店，再进对话生成行程）。
 *   那时没有 tripId，前端只本地暂存；行程保存拿到 id 后会再调一次本接口落库。
 *   传了 tripId 就必须是该用户的行程，否则报「行程不存在」，不越权改别人的行程。
 */
@Data
public class HotelSelectDTO {

    /** 行程id；为空表示行程尚未保存，只做校验不落库 */
    private Long tripId;

    /** 候选酒店编码（hotel_candidate.hotel_code） */
    private String hotelCode;

    // ── 以下是 hotelCode 查不到时的兜底字段 ──
    private String name;
    private String city;
    private String level;
    private String address;
    private String nearbyLandmark;
    private java.math.BigDecimal lng;
    private java.math.BigDecimal lat;
    private java.math.BigDecimal price;

    /** 入住日期（YYYY-MM-DD）；为空时服务端用行程的 startDate */
    private String checkin;

    /** 离店日期（YYYY-MM-DD）；为空时服务端用行程的 endDate */
    private String checkout;
}
