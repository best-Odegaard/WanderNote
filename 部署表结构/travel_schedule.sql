create table aitravel.travel_schedule
(
    id                    bigint auto_increment comment '主键'
        primary key,
    destination           varchar(100)                       null comment '目的地',
    travel_days           int                                null comment '出行天数',
    travel_preference     varchar(255)                       null comment '旅行偏好',
    people_num            int                                null comment '出行人数',
    budget                decimal(10, 2)                     null comment '预算',
    start_city            varchar(100)                       null comment '出发城市',
    plan_title            varchar(255)                       null comment '行程标题',
    day_tag               varchar(20)                        null comment '行程天数标识 Day1/Day2',
    real_travel_date      date                               null comment '实际出行日期',
    visit_start_time      time                               null comment '游玩开始时间',
    visit_end_time        time                               null comment '游玩结束时间',
    visit_time_range      varchar(50)                        null comment '游玩时段原文',
    scenic_name           varchar(100)                       null comment '景点名称',
    open_time_desc        varchar(100)                       null comment '景点营业时间',
    location              varchar(255)                       null comment '景点地址',
    feature_tag           varchar(100)                       null comment '景点标签',
    ticket_price          decimal(10, 2)                     null comment '门票价格',
    ticket_desc           varchar(100)                       null comment '门票原始文案',
    budget_range          varchar(100)                       null comment '预算范围',
    travel_pace           varchar(100)                       null comment '旅行节奏',
    experience_demand     varchar(255)                       null comment '游玩偏好体验',
    travel_people         varchar(100)                       null comment '同行人员',
    custom_demand         text                               null comment '用户自定义额外需求',
    origin_ai_json        text                               null comment 'AI返回完整原始JSON',
    agent_travel_strategy text                               null comment 'AI生成完整行程攻略',
    create_time           datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time           datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_deleted            int      default 0                 null comment '逻辑删除：0-未删除，1-已删除'
)
    comment '行程计划表';

create index idx_create_time
    on aitravel.travel_schedule (create_time);

create index idx_destination
    on aitravel.travel_schedule (destination);

