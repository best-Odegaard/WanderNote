-- ============================================================
-- V2.8 订票（火车票 / 飞机票）两张表
-- 创建于 2026-10-09
--
-- 用途：给「行程 + 大交通」补齐最后一环 —— 用户选定去程/返程后，
--   班次信息要能落库，杀进程/换设备再打开行程还能看到「怎么去、怎么回」。
--
-- 两张表的分工（与酒店那套 hotel_candidate / trip_hotel 完全对齐）：
--   1) ticket_candidate —— 自建精选班次库，运营维护。
--      为什么自建：12306 与航司都没有面向第三方的公开开放 API（见 产品项目文档 5.7.1）。
--      所以这里只存「车次/航班号 + 时刻 + 参考价」这类静态信息，
--      绝不存余票、舱位、库存 —— 有库存字段就会有人想在这里下单，那是合规红线。
--   2) trip_ticket —— 某条行程选中的班次（去程/返程各一条）。
--
-- 设计说明：
--   1. 一个行程每个方向只留一条票 → (trip_id, direction) 建唯一索引
--      uk_trip_ticket_trip_dir。应用层用「先查后写 / 命中则更新」实现 upsert；
--      唯一索引是并发兜底（用户连点两次「确认」不会插出两条）。
--      ★ 并发命中唯一索引会抛 DuplicateKeyException，
--        TicketServiceImpl.selectForTrip 已 catch 并转成「更新已有记录」，不会 500。
--   2. direction 取值只有 outbound（去程）/ return（返程）。
--      「单程」不是第三种方向，而是「只有 outbound、没有 return」—— 少一条记录而已，
--      所以不需要为单程加字段。
--   3. purchase_url 落库而不是每次现算：
--      12306/携程的参数模板会随它们前端改版而变，历史行程里已存的链接不会跟着变（可接受），
--      换来的是「历史行程的链接行为可追溯」。模板集中在 TicketUrlBuilder。
--   4. ticket_code 只做关联线索，不建外键：候选库下架某个班次后，
--      历史行程仍要能显示车次与时刻，所以这里是冗余存储。
--   5. depart_date / depart_time 允许为空：用户在日期还没定时也能先选班次。
--
-- ★ 执行前必读 ★
--   create table if not exists 是幂等的，重复执行等于空操作，不会报错。
--   种子数据用 insert ... on duplicate key update，重复执行只刷新字段、不报错。
-- ============================================================

-- ------------------------------------------------------------
-- 执行前自查：
--   select table_name from information_schema.tables
--    where table_schema = 'geek012' and table_name in ('ticket_candidate','trip_ticket');
--     → 返回 2 行 = 已建过，本脚本仍可安全重跑（幂等）
-- ------------------------------------------------------------

create table if not exists geek012.ticket_candidate
(
    id             bigint auto_increment comment '主键' primary key,
    ticket_code    varchar(60)                        not null comment '业务编码（如 train-gz-zq-c7001），前端本地兜底表同款',
    transport_type varchar(10)                        not null comment '票种：train=火车票 flight=飞机票',
    carrier        varchar(40)                        null comment '承运方（铁路局 / 航空公司）',
    ticket_no      varchar(20)                        not null comment '车次号（C7001）或航班号（CZ3101）',
    from_city      varchar(40)                        not null comment '出发城市',
    to_city        varchar(40)                        not null comment '到达城市',
    from_station   varchar(60)                        null comment '出发站/机场（广州南 / 白云机场）',
    to_station     varchar(60)                        null comment '到达站/机场',
    depart_time    varchar(5)                         null comment '出发时刻 HH:mm',
    arrive_time    varchar(5)                         null comment '到达时刻 HH:mm（跨天请在 duration 上体现）',
    duration_min   int                                null comment '历时（分钟）',
    seat_class     varchar(20)                        null comment '席别/舱位（二等座 / 经济舱）',
    ref_price      decimal(10, 2)                     null comment '参考价（元/人，以 12306 或携程为准，非实时价）',
    stops          int      default 0                 null comment '经停次数，0=直达',
    tags           varchar(120)                       null comment '卖点标签，逗号分隔',
    sort_order     int      default 0                 null comment '同线路内的推荐排序（小的在前）',
    status         tinyint  default 1                 null comment '状态：0下架 1上架',
    create_time    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_ticket_candidate_code (ticket_code),
    key idx_ticket_route (transport_type, from_city, to_city, status)
) comment '订票候选库（自建精选班次，运营维护；不含余票/库存）';

create table if not exists geek012.trip_ticket
(
    id             bigint auto_increment comment '主键' primary key,
    trip_id        bigint                                not null comment '行程id（trip_plan.id）',
    direction      varchar(10)                           not null comment '方向：outbound=去程 return=返程',
    transport_type varchar(10)                           null comment '票种：train/flight',
    ticket_code    varchar(60)                           null comment '候选班次编码（兜底班次为空）',
    ticket_no      varchar(20)                           null comment '车次号/航班号',
    carrier        varchar(40)                           null comment '承运方',
    from_city      varchar(40)                           null comment '出发城市',
    to_city        varchar(40)                           null comment '到达城市',
    from_station   varchar(60)                           null comment '出发站/机场',
    to_station     varchar(60)                           null comment '到达站/机场',
    depart_date    date                                  null comment '出发日期',
    depart_time    varchar(5)                            null comment '出发时刻 HH:mm',
    arrive_time    varchar(5)                            null comment '到达时刻 HH:mm',
    duration_min   int                                   null comment '历时（分钟）',
    seat_class     varchar(20)                           null comment '席别/舱位',
    ref_price      decimal(10, 2)                        null comment '参考价（元/人，以 12306 或携程为准）',
    purchase_url   varchar(500)                          null comment '购买深链（TicketUrlBuilder 生成）',
    create_time    datetime default CURRENT_TIMESTAMP    null comment '创建时间',
    update_time    datetime default CURRENT_TIMESTAMP    null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_trip_ticket_trip_dir (trip_id, direction),
    key idx_trip_ticket_code (ticket_code)
) comment '行程票务（选中的去程/返程班次，含购买深链）';

-- ------------------------------------------------------------
-- 种子数据：覆盖热门线路的火车 + 飞机各若干班次。
--   为什么给这么少：候选库是「让用户有个可比较的起点」，不是全量时刻表。
--   未收录的线路由前端兜底 + 纯跳转 12306/携程（见 TicketServiceImpl 的降级说明）。
--   insert ... on duplicate key update：重复执行只刷新字段，不报错。
-- ------------------------------------------------------------
insert into geek012.ticket_candidate
(ticket_code, transport_type, carrier, ticket_no, from_city, to_city, from_station, to_station,
 depart_time, arrive_time, duration_min, seat_class, ref_price, stops, tags, sort_order, status)
values
    -- ── 广州 ↔ 肇庆（城际，最常见的一条） ──
    ('train-gz-zq-c7001', 'train', '广铁集团', 'C7001', '广州', '肇庆', '广州南', '肇庆东', '07:12', '07:47', 35, '二等座', 26.00, 0, '早班,直达', 1, 1),
    ('train-gz-zq-c7015', 'train', '广铁集团', 'C7015', '广州', '肇庆', '广州南', '肇庆东', '09:30', '10:06', 36, '二等座', 26.00, 0, '直达', 2, 1),
    ('train-gz-zq-d1832', 'train', '广铁集团', 'D1832', '广州', '肇庆', '广州南', '肇庆东', '14:05', '14:49', 44, '二等座', 30.00, 0, '下午班', 3, 1),
    ('train-zq-gz-c7002', 'train', '广铁集团', 'C7002', '肇庆', '广州', '肇庆东', '广州南', '08:05', '08:41', 36, '二等座', 26.00, 0, '早班,直达', 1, 1),
    ('train-zq-gz-c7028', 'train', '广铁集团', 'C7028', '肇庆', '广州', '肇庆东', '广州南', '18:20', '18:56', 36, '二等座', 26.00, 0, '晚班返程', 2, 1),

    -- ── 广州 ↔ 成都 ──
    ('train-gz-cd-g3702', 'train', '广铁集团', 'G3702', '广州', '成都', '广州南', '成都东', '08:16', '17:02', 526, '二等座', 764.00, 0, '高铁,直达', 1, 1),
    ('train-gz-cd-z122', 'train', '广铁集团', 'Z122', '广州', '成都', '广州', '成都东', '19:32', '06:12', 1600, '硬卧', 428.00, 2, '夕发朝至,省一晚住宿', 2, 1),
    ('flight-gz-cd-cz3401', 'flight', '南方航空', 'CZ3401', '广州', '成都', '白云机场T2', '天府机场T1', '07:35', '10:05', 150, '经济舱', 780.00, 0, '早班,含餐', 1, 1),
    ('flight-gz-cd-3u8732', 'flight', '四川航空', '3U8732', '广州', '成都', '白云机场T1', '双流机场T2', '15:20', '17:55', 155, '经济舱', 640.00, 0, '下午班', 2, 1),
    ('train-cd-gz-g3701', 'train', '成都局集团', 'G3701', '成都', '广州', '成都东', '广州南', '07:58', '16:44', 526, '二等座', 764.00, 0, '高铁,直达', 1, 1),
    ('flight-cd-gz-cz3402', 'flight', '南方航空', 'CZ3402', '成都', '广州', '天府机场T1', '白云机场T2', '19:40', '22:05', 145, '经济舱', 720.00, 0, '晚班返程', 1, 1),

    -- ── 广州 ↔ 杭州 ──
    ('train-gz-hz-g86', 'train', '广铁集团', 'G86', '广州', '杭州', '广州南', '杭州东', '08:00', '14:02', 362, '二等座', 682.00, 0, '高铁,直达', 1, 1),
    ('flight-gz-hz-ca1726', 'flight', '中国国航', 'CA1726', '广州', '杭州', '白云机场T2', '萧山机场T3', '09:15', '11:20', 125, '经济舱', 560.00, 0, '上午班', 1, 1),
    ('flight-gz-hz-mu5392', 'flight', '东方航空', 'MU5392', '广州', '杭州', '白云机场T1', '萧山机场T4', '18:30', '20:35', 125, '经济舱', 480.00, 0, '晚班', 2, 1),
    ('train-hz-gz-g85', 'train', '上海局集团', 'G85', '杭州', '广州', '杭州东', '广州南', '09:10', '15:12', 362, '二等座', 682.00, 0, '高铁,直达', 1, 1),
    ('flight-hz-gz-ca1725', 'flight', '中国国航', 'CA1725', '杭州', '广州', '萧山机场T3', '白云机场T2', '20:05', '22:15', 130, '经济舱', 520.00, 0, '晚班返程', 1, 1),

    -- ── 广州 ↔ 重庆 ──
    ('train-gz-cq-d1852', 'train', '广铁集团', 'D1852', '广州', '重庆', '广州南', '重庆西', '09:12', '17:36', 504, '二等座', 553.00, 0, '动车,直达', 1, 1),
    ('flight-gz-cq-cz3461', 'flight', '南方航空', 'CZ3461', '广州', '重庆', '白云机场T2', '江北机场T3', '11:20', '13:30', 130, '经济舱', 590.00, 0, '午班', 1, 1),
    ('flight-gz-cq-pn6207', 'flight', '西部航空', 'PN6207', '广州', '重庆', '白云机场T1', '江北机场T2', '21:05', '23:15', 130, '经济舱', 420.00, 0, '红眼,便宜', 2, 1),
    ('train-cq-gz-d1851', 'train', '成都局集团', 'D1851', '重庆', '广州', '重庆西', '广州南', '08:26', '16:50', 504, '二等座', 553.00, 0, '动车,直达', 1, 1),
    ('flight-cq-gz-cz3462', 'flight', '南方航空', 'CZ3462', '重庆', '广州', '江北机场T3', '白云机场T2', '14:40', '16:50', 130, '经济舱', 610.00, 0, '下午返程', 1, 1),

    -- ── 广州 ↔ 桂林 ──
    ('train-gz-gl-d2812', 'train', '广铁集团', 'D2812', '广州', '桂林', '广州南', '桂林北', '08:32', '11:14', 162, '二等座', 152.50, 0, '动车,直达', 1, 1),
    ('train-gz-gl-g2918', 'train', '广铁集团', 'G2918', '广州', '桂林', '广州南', '桂林西', '15:10', '17:26', 136, '二等座', 168.00, 0, '高铁,下午班', 2, 1),
    ('train-gl-gz-d2811', 'train', '南宁局集团', 'D2811', '桂林', '广州', '桂林北', '广州南', '17:05', '19:47', 162, '二等座', 152.50, 0, '晚班返程', 1, 1),

    -- ── 广州 ↔ 北京 ──
    ('train-gz-bj-g66', 'train', '广铁集团', 'G66', '广州', '北京', '广州南', '北京西', '08:00', '15:59', 479, '二等座', 862.00, 0, '高铁,直达', 1, 1),
    ('flight-gz-bj-ca1316', 'flight', '中国国航', 'CA1316', '广州', '北京', '白云机场T2', '首都机场T3', '08:30', '11:45', 195, '经济舱', 1180.00, 0, '早班', 1, 1),
    ('flight-gz-bj-mu6300', 'flight', '东方航空', 'MU6300', '广州', '北京', '白云机场T1', '大兴机场', '19:00', '22:15', 195, '经济舱', 890.00, 0, '晚班', 2, 1),
    ('train-bj-gz-g65', 'train', '北京局集团', 'G65', '北京', '广州', '北京西', '广州南', '09:00', '16:59', 479, '二等座', 862.00, 0, '高铁,直达', 1, 1),
    ('flight-bj-gz-ca1315', 'flight', '中国国航', 'CA1315', '北京', '广州', '首都机场T3', '白云机场T2', '13:20', '16:40', 200, '经济舱', 1050.00, 0, '下午返程', 1, 1),

    -- ── 广州 ↔ 上海 ──
    ('train-gz-sh-g100', 'train', '广铁集团', 'G100', '广州', '上海', '广州南', '上海虹桥', '08:05', '15:04', 419, '二等座', 793.00, 0, '高铁,直达', 1, 1),
    ('flight-gz-sh-mu5301', 'flight', '东方航空', 'MU5301', '广州', '上海', '白云机场T1', '虹桥机场T2', '09:40', '12:00', 140, '经济舱', 690.00, 0, '上午班', 1, 1),
    ('flight-gz-sh-ho1252', 'flight', '吉祥航空', 'HO1252', '广州', '上海', '白云机场T2', '浦东机场T1', '20:10', '22:30', 140, '经济舱', 520.00, 0, '晚班', 2, 1),
    ('train-sh-gz-g99', 'train', '上海局集团', 'G99', '上海', '广州', '上海虹桥', '广州南', '10:00', '16:59', 419, '二等座', 793.00, 0, '高铁,直达', 1, 1),
    ('flight-sh-gz-mu5302', 'flight', '东方航空', 'MU5302', '上海', '广州', '虹桥机场T2', '白云机场T1', '18:30', '20:55', 145, '经济舱', 650.00, 0, '晚班返程', 1, 1),

    -- ── 广州 ↔ 西安 ──
    ('train-gz-xa-g96', 'train', '广铁集团', 'G96', '广州', '西安', '广州南', '西安北', '08:55', '16:20', 445, '二等座', 812.50, 0, '高铁,直达', 1, 1),
    ('flight-gz-xa-mu2108', 'flight', '东方航空', 'MU2108', '广州', '西安', '白云机场T1', '咸阳机场T3', '10:25', '12:55', 150, '经济舱', 620.00, 0, '上午班', 1, 1),
    ('train-xa-gz-g95', 'train', '西安局集团', 'G95', '西安', '广州', '西安北', '广州南', '09:30', '16:55', 445, '二等座', 812.50, 0, '高铁,直达', 1, 1),
    ('flight-xa-gz-mu2107', 'flight', '东方航空', 'MU2107', '西安', '广州', '咸阳机场T3', '白云机场T1', '17:40', '20:15', 155, '经济舱', 580.00, 0, '晚班返程', 1, 1),

    -- ── 广州 ↔ 三亚 ──
    ('flight-gz-sy-cz6741', 'flight', '南方航空', 'CZ6741', '广州', '三亚', '白云机场T2', '凤凰机场', '08:20', '09:50', 90, '经济舱', 460.00, 0, '早班,快', 1, 1),
    ('flight-gz-sy-hu7188', 'flight', '海南航空', 'HU7188', '广州', '三亚', '白云机场T1', '凤凰机场', '16:15', '17:45', 90, '经济舱', 380.00, 0, '下午班', 2, 1),
    ('flight-sy-gz-cz6742', 'flight', '南方航空', 'CZ6742', '三亚', '广州', '凤凰机场', '白云机场T2', '20:30', '22:00', 90, '经济舱', 420.00, 0, '晚班返程', 1, 1),

    -- ── 广州 ↔ 厦门 ──
    ('train-gz-xm-d2388', 'train', '广铁集团', 'D2388', '广州', '厦门', '广州东', '厦门北', '08:47', '13:05', 258, '二等座', 246.50, 0, '动车,直达', 1, 1),
    ('flight-gz-xm-mf8302', 'flight', '厦门航空', 'MF8302', '广州', '厦门', '白云机场T2', '高崎机场T3', '12:10', '13:30', 80, '经济舱', 400.00, 0, '午班', 1, 1),
    ('train-xm-gz-d2387', 'train', '南昌局集团', 'D2387', '厦门', '广州', '厦门北', '广州东', '14:20', '18:38', 258, '二等座', 246.50, 0, '下午返程', 1, 1),
    ('flight-xm-gz-mf8301', 'flight', '厦门航空', 'MF8301', '厦门', '广州', '高崎机场T3', '白云机场T2', '19:40', '21:05', 85, '经济舱', 430.00, 0, '晚班返程', 1, 1)
on duplicate key update
    transport_type = values(transport_type),
    carrier        = values(carrier),
    ticket_no      = values(ticket_no),
    from_city      = values(from_city),
    to_city        = values(to_city),
    from_station   = values(from_station),
    to_station     = values(to_station),
    depart_time    = values(depart_time),
    arrive_time    = values(arrive_time),
    duration_min   = values(duration_min),
    seat_class     = values(seat_class),
    ref_price      = values(ref_price),
    stops          = values(stops),
    tags           = values(tags),
    sort_order     = values(sort_order),
    status         = values(status);

-- ------------------------------------------------------------
-- 执行后核对：
--   select transport_type, count(*) from geek012.ticket_candidate group by transport_type;
--     → train / flight 两类都有行
--
--   select t.id, t.title, k.direction, k.ticket_no, k.depart_date
--     from geek012.trip_plan t
--     join geek012.trip_ticket k on k.trip_id = t.id
--    order by k.update_time desc limit 20;
--     → 用户选过票之后这里应能看到对应行；
--       同一 trip_id 最多两行，且 direction 一个是 outbound、一个是 return。
-- ------------------------------------------------------------
