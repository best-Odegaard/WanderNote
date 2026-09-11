create table geek012.chat_history
(
    id          bigint auto_increment comment '主键'
        primary key,
    session_id  varchar(64)                        not null comment '会话ID',
    role        varchar(16)                        not null comment '角色：user/assistant',
    content     text                               not null comment '消息内容',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '聊天历史记录表';

create index idx_session_id
    on geek012.chat_history (session_id);

