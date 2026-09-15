-- 行程计划表（TripPlan 实体对应表）
-- 服务器数据库：geek012
-- 用于 AI 生成行程入库 + "我的行程"列表/详情
-- 注意：本文件是「最新结构快照」。已有库升级请执行 V1.9__alter_trip_plan_add_dates.sql，
--       不要重复建表（线上库还有 chat_session_id，是建表后单独补的）。
create table if not exists geek012.trip_plan
(
    id             bigint auto_increment comment '主键' primary key,
    user_id        bigint                             null comment '所属用户ID',
    title          varchar(255)                       null comment '行程标题',
    from_city      varchar(100)                       null comment '出发城市',
    to_city        varchar(100)                       null comment '目的地城市',
    start_date     varchar(20)                        null comment '出发日期(YYYY-MM-DD)',
    end_date       varchar(20)                        null comment '结束日期(YYYY-MM-DD)',
    days           int                                null comment '行程天数',
    people         int                                null comment '出行人数',
    budget         decimal(10, 2)                     null comment '预算',
    estimated_cost decimal(10, 2)                     null comment '预估花费',
    hotel          varchar(255)                       null comment '推荐住宿',
    tags           varchar(500)                       null comment '标签(JSON数组字符串)',
    day_plans      text                               null comment '每日行程(JSON: day/title/schedules[])',
    cover          varchar(500)                       null comment '封面图URL',
    source_url     varchar(500)                       null comment '来源链接(导入场景)',
    source_type    int      default 0                 null comment '来源类型：1手动 2AI生成 3链接导入',
    visibility     int      default 0                 null comment '可见性：0私有 1公开',
    status         int      default 1                 null comment '状态：1草稿 2已发布',
    share_count    int      default 0                 null comment '分享数',
    like_count     int      default 0                 null comment '点赞数',
    create_time    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
) comment '行程计划表';

create index idx_user_id on geek012.trip_plan (user_id);
create index idx_create_time on geek012.trip_plan (create_time);
