create table geek012.user
(
    id          bigint auto_increment comment '主键'
        primary key,
    username    varchar(50)                        not null comment '用户名',
    password    varchar(100)                       not null comment '密码',
    phone       varchar(20)                        null comment '手机号',
    email       varchar(100)                       null comment '邮箱',
    avatar      varchar(255)                       null comment '头像',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    is_deleted  int      default 0                 null comment '逻辑删除：0-未删除，1-已删除',
    constraint uk_username
        unique (username)
)
    comment '用户表';

