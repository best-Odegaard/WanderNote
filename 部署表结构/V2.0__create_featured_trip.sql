-- ============================================================
-- V2.0 精选行程表
-- 创建于 2026-09-11
--
-- 用途：首页轮播图展示「一整个城市的完整行程规划」（而不是单个景点）。
--   别人点进去可以在行程详情页查看，并「添加到我的行程」或「对话修改后再加入」。
--
-- 设计说明：
--   1. trip_json 内嵌完整行程（逐日明细：时间段/景点/门票/开放时间/地址），
--      不引用 trip_plan 表 —— 精选内容自包含，不会因为别的行程被删而碎掉。
--   2. trip_json 的结构与前端 TripPlan 对齐：
--      { title, fromCity, toCity, startDate, endDate, days, people, budget, tags[],
--        dayPlans: [ { day, title, schedules: [ { time, title, image, openTime, ticket,
--        location, featureTag, rating, type } ] } ] }
--   3. source_url 记录内容来源（外部游记链接）；后台支持「粘贴链接 → AI 生成行程」，
--      但注意现有链接导入只从链接里提取城市、再由 AI 生成行程，并不抓取页面正文。
-- ============================================================

create table if not exists geek012.featured_trip
(
    id          bigint auto_increment comment '主键' primary key,
    title       varchar(255)                       not null comment '行程标题',
    subtitle    varchar(255)                       null comment '副标题/一句话卖点',
    city        varchar(100)                       null comment '目的地城市',
    days        int                                null comment '行程天数',
    cover       varchar(500)                       null comment '封面图URL',
    trip_json   text                               not null comment '完整行程JSON（含逐日明细）',
    source_url  varchar(500)                       null comment '内容来源链接（外部游记）',
    sort_order  int      default 0                 null comment '排序（小的在前）',
    status      int      default 1                 null comment '状态：0下架 1上架',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
) comment '精选行程表（首页轮播展示的完整行程）';

create index idx_featured_status_sort on geek012.featured_trip (status, sort_order);
