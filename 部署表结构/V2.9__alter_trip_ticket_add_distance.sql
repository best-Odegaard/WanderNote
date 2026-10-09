-- ============================================================
-- V2.9 行程票务支持「无班次的交通方式」（自驾）
-- 创建于 2026-10-09
--
-- 背景：
--   交通卡上线时，行程里能记的只有「有班次的票」（火车票 / 飞机票），
--   因为那时靠候选库提供车次与参考价。但推荐逻辑一上来就会发现：
--   不少线路（尤其 100~600km 的城市对）自驾更合适，用户也真的会选自驾。
--   自驾没有车次号、没有席别、没有 12306/携程查询页，却也必须有里程与耗时，
--   否则交通卡上只剩一行「自驾」，用户看不出到底要开多久、多远。
--
-- 本脚本只做一件事：给 trip_ticket 加 distance_km。
--
-- 顺带说明 transport_type 的取值（列本身不用改，varchar(10) 放得下）：
--   train  火车票（有班次，有查询页）
--   flight 飞机票（有班次，有查询页）
--   drive  自驾（无班次、无查询页，purchase_url 为空）
--   将来若扩到 bus / carpool，同样按「无班次方式」处理：
--   TicketUrlBuilder.isModeOnly() 返回 true → 不生成深链；
--   TicketServiceImpl 也不再要求 ticket_no 非空。
--
-- ★ 执行前必读 ★
--   MySQL 不支持 ADD COLUMN IF NOT EXISTS。
--   若目标库已有 distance_km，重复执行会报 Duplicate column name 'distance_km'，
--   属预期，跳过即可（开发库本次已执行过一次）。
-- ============================================================

-- ------------------------------------------------------------
-- 执行前自查：
--   select column_name, column_type, is_nullable
--     from information_schema.columns
--    where table_schema = 'geek012' and table_name = 'trip_ticket'
--    order by ordinal_position;
--     → 已经能看到 distance_km 就跳过本脚本
-- ------------------------------------------------------------

alter table geek012.trip_ticket
    add column distance_km decimal(10, 1) null comment '里程（公里，地图规划值或按直线距离估算）' after duration_min;

-- ------------------------------------------------------------
-- 执行后核对：
--   select trip_id, direction, transport_type, ticket_no, duration_min, distance_km, ref_price
--     from geek012.trip_ticket order by update_time desc limit 20;
--     → 自驾那几条：transport_type='drive'、ticket_no 为空、purchase_url 为空，
--       但 duration_min / distance_km / ref_price 都有值。
-- ------------------------------------------------------------
