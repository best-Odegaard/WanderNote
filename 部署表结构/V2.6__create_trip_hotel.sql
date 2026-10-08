-- ============================================================
-- V2.6 行程住宿表 trip_hotel
-- 创建于 2026-10-08
--
-- 用途：记录「这条行程晚上住哪」。
--   在它之前，住宿只有一个名字 —— trip_plan.hotel（varchar）。
--   名字不够用：路线页要把当天行程补成「酒店出发 → 各站 → 返回酒店」的闭环，
--   必须有坐标；用户在酒店页选好酒店、杀进程再打开行程时，
--   名字还在但坐标丢了，闭环就退化回「第一站 → 最后一站」。
--   所以这里把名称 + 坐标 + 地址 + 参考价 + 携程深链一起落库。
--
-- 设计说明：
--   1. 一个行程一条住宿记录 → trip_id 上建唯一索引 uk_trip_hotel_trip。
--      应用层用「先查后写 / 命中则更新」实现 upsert，
--      唯一索引是并发兜底（同一用户连点两次「确认这家」不会插出两条）。
--      ★ 注意：并发命中唯一索引会抛 DuplicateKeyException，
--        HotelServiceImpl.selectForTrip 已 catch 并转成「更新已有记录」，不会 500。
--   2. trip_plan.hotel（名称）仍然保留并同步写入 —— 行程卡片/分享等旧链路都读它，
--      不动老字段，避免为了新表把旧展示全改一遍。
--   3. 深链 ctrip_url 落库而不是每次现算：
--      携程改参数模板时，历史行程里已存的链接不会跟着变（可接受），
--      换来的是「历史行程的链接行为可追溯」。模板集中在 CtripUrlBuilder。
--   4. hotel_code 关联 hotel_candidate.hotel_code（V2.7）。
--      候选库下架某家酒店后，历史行程仍能显示名称与坐标 —— 所以不做外键，只存冗余值。
--   5. checkin/checkout 允许为空：用户在问卷里还没定日期时也能先选酒店。
--
-- ★ 执行前必读 ★
--   create table if not exists 是幂等的，重复执行等于空操作，不会报错。
-- ============================================================

-- ------------------------------------------------------------
-- 执行前自查：
--   select table_name from information_schema.tables
--    where table_schema = 'geek012' and table_name = 'trip_hotel';
--     → 返回 1 行 = 已建过，跳过本脚本
-- ------------------------------------------------------------

create table if not exists geek012.trip_hotel
(
    id          bigint auto_increment comment '主键' primary key,
    trip_id     bigint                                not null comment '行程id（trip_plan.id）',
    hotel_code  varchar(40)                           null comment '候选酒店编码（hotel_candidate.hotel_code，兜底酒店为空）',
    hotel_name  varchar(120)                          null comment '酒店名称（冗余存，候选库下架后历史行程仍可展示）',
    lng         decimal(10, 6)                        null comment '经度（路线闭环必需）',
    lat         decimal(10, 6)                        null comment '纬度（路线闭环必需）',
    address     varchar(255)                          null comment '详细地址',
    style_tags  varchar(120)                          null comment '风格标签，逗号分隔',
    checkin     date                                  null comment '入住日期',
    checkout    date                                  null comment '离店日期',
    ref_price   decimal(10, 2)                        null comment '参考价（元/晚，以携程为准，非实时价）',
    ctrip_url   varchar(500)                          null comment '携程深链（CtripUrlBuilder 生成）',
    create_time datetime default CURRENT_TIMESTAMP    null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP    null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_trip_hotel_trip (trip_id),
    key idx_trip_hotel_code (hotel_code)
) comment '行程住宿表（选中的酒店，含坐标与携程深链）';

-- ------------------------------------------------------------
-- 执行后核对：
--   select t.id, t.title, t.hotel, h.hotel_name, h.lat, h.lng
--     from geek012.trip_plan t
--     left join geek012.trip_hotel h on h.trip_id = t.id
--    where h.id is not null
--    order by h.update_time desc limit 20;
--     → 刚上线的行程没有住宿记录，属正常；用户选过酒店后这里应能看到对应行，
--       且 trip_plan.hotel 与 trip_hotel.hotel_name 一致。
-- ------------------------------------------------------------
