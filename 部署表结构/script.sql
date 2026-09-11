create table if not exists sys_user
(
    id          int auto_increment comment '用户id（主键）'
        primary key,
    username    varchar(50)                        not null comment '用户名（登录账号）',
    password    varchar(20)                        not null comment '密码',
    phone       varchar(20)                        null comment '手机号',
    email       varchar(100)                       null comment '邮箱',
    nickname    varchar(50)                        null comment '昵称',
    avatar      varchar(500)                       null comment '头像URL',
    gender      tinyint  default 0                 null comment '性别（0：未知，1：男，2：女）',
    birthday    date                               null comment '出生日期',
    bio         varchar(255)                       null comment '个人简介',
    points      int      default 0                 null comment '积分',
    status      tinyint  default 1                 not null comment '状态：0禁用 1启用',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间（自动更新）',
    constraint phone
        unique (phone),
    constraint uk_phone
        unique (phone),
    constraint uk_username
        unique (username),
    constraint username
        unique (username)
)
    comment '用户表';

create table if not exists tag
(
    id          int auto_increment comment '主键id'
        primary key,
    tag_name    varchar(50)                        not null comment '标签名称',
    category    varchar(50)                        null comment '标签分类',
    description varchar(200)                       null comment '标签描述',
    use_count   int      default 0                 null comment '使用次数（被多少游记使用）',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint tag_name
        unique (tag_name)
)
    comment '标签主表';

create index idx_category
    on tag (category);

create index idx_tag_name
    on tag (tag_name);

create table if not exists travel_journal
(
    id            int auto_increment comment '游记id'
        primary key,
    user_id       int                                not null comment '发布用户id',
    title         varchar(100)                       not null comment '游记标题',
    content       text                               not null comment '游记内容',
    images        varchar(2000)                      null comment '图片URL列表，json数组格式',
    tags          varchar(500)                       null comment '标签,多个标签用逗号分割',
    location      varchar(255)                       null comment '地点（选填）',
    status        tinyint  default 1                 not null comment '状态：1-已发布，0-未发布',
    like_count    int      default 0                 not null comment '点赞数',
    collect_count int      default 0                 null comment '收藏数',
    create_time   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint fk_journal_user
        foreign key (user_id) references sys_user (id)
)
    comment '游记表';

create table if not exists travel_journal_browse
(
    id          bigint auto_increment comment '主键id'
        primary key,
    journal_id  int                                not null comment '游记id',
    user_id     int                                not null comment '浏览用户id',
    create_time datetime default CURRENT_TIMESTAMP null comment '浏览时间',
    constraint uk_browse_journal_user
        unique (journal_id, user_id),
    constraint fk_browse_journal
        foreign key (journal_id) references travel_journal (id)
            on delete cascade,
    constraint fk_browse_user
        foreign key (user_id) references sys_user (id)
            on delete cascade
)
    comment '游记浏览记录表';

create index idx_user_id
    on travel_journal_browse (user_id);

create table if not exists travel_journal_collect
(
    id          bigint auto_increment comment '主键id'
        primary key,
    journal_id  int                                not null comment '游记id',
    user_id     int                                not null comment '收藏用户id',
    create_time datetime default CURRENT_TIMESTAMP null comment '收藏时间',
    constraint uk_collect_journal_user
        unique (journal_id, user_id),
    constraint fk_collect_journal
        foreign key (journal_id) references travel_journal (id)
            on delete cascade,
    constraint fk_collect_user
        foreign key (user_id) references sys_user (id)
            on delete cascade
)
    comment '游记收藏记录表';

create index idx_user_id
    on travel_journal_collect (user_id);

create table if not exists travel_journal_comment
(
    id                bigint auto_increment comment '主键id'
        primary key,
    journal_id        int                                not null comment '游记id',
    user_id           int                                not null comment '评论用户id',
    parent_comment_id bigint                             null comment '父评论id（null表示一级评论）',
    content           text                               not null comment '评论内容',
    status            tinyint  default 0                 null comment '评论状态（0正常，1待审批，2删除）',
    create_time       datetime default CURRENT_TIMESTAMP null comment '评论时间',
    constraint fk_comment_journal
        foreign key (journal_id) references travel_journal (id)
            on delete cascade,
    constraint fk_comment_parent
        foreign key (parent_comment_id) references travel_journal_comment (id)
            on delete cascade,
    constraint fk_comment_user
        foreign key (user_id) references sys_user (id)
            on delete cascade
)
    comment '游记评论表';

create index idx_journal_id
    on travel_journal_comment (journal_id);

create index idx_parent_comment_id
    on travel_journal_comment (parent_comment_id);

create table if not exists travel_journal_like
(
    id          bigint auto_increment comment '主键id'
        primary key,
    journal_id  int                                not null comment '游记id',
    user_id     int                                not null comment '点赞用户id',
    create_time datetime default CURRENT_TIMESTAMP null comment '点赞时间',
    constraint uk_journal_user
        unique (journal_id, user_id),
    constraint fk_like_journal
        foreign key (journal_id) references travel_journal (id)
            on delete cascade,
    constraint fk_like_user
        foreign key (user_id) references sys_user (id)
            on delete cascade
)
    comment '游记点赞记录表';

create index idx_user_id
    on travel_journal_like (user_id);

create table if not exists travel_journal_tag
(
    id          bigint auto_increment comment '主键id'
        primary key,
    journal_id  int                                not null comment '游记id',
    tag_id      int                                not null comment '标签id',
    create_time datetime default CURRENT_TIMESTAMP null comment '关联时间',
    constraint uk_journal_tag
        unique (journal_id, tag_id),
    constraint fk_tag_journal
        foreign key (journal_id) references travel_journal (id)
            on delete cascade,
    constraint fk_tag_tag
        foreign key (tag_id) references tag (id)
            on delete cascade
)
    comment '游记标签关联表';

create index idx_tag_id
    on travel_journal_tag (tag_id);


