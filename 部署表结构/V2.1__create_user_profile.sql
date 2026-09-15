-- ============================================================
-- V2.1 AI 用户画像主表
-- 创建于 2026-09-14
--
-- 背景：
--   用户在 AI 对话页完成行程规划后，系统在不打扰用户的前提下，从对话 + 结构化表单里
--   沉淀这个人的偏好（受控标签+权重、固定字段、硬性约束、一段 AI 文字摘要），
--   下一次对话/规划时把画像回灌进 prompt，让 AI 不用重复追问已知偏好。
--
-- ★ 执行前必读（动手前已核对过开发库，结论如下）★
--   开发库 geek012 里 **这张表已经存在**（2026-09-14 15:58 建，已有 3 行测试数据），
--   而且列名是 pref_tags / constraints / chat_round_count / last_source / use_preference / mark。
--   本脚本的列名与之一致 —— 不以"文档里更顺眼的名字"为准，以库里真正生效的那张表为准。
--   原因：create table if not exists 碰到已存在的表会**静默跳过**，
--   如果代码按另一套列名写，运行时就报 Unknown column；
--   而画像链路整条包在 try/catch 里（旁路不允许影响对话），
--   表现是"功能看起来做好了但一直不生效"，这是最难查的一类故障。
--   Java 侧的字段↔列名映射集中在 com.gkv.entity.UserProfile 的 @TableField 上。
--
--   所以：
--     - 已经建过这张表的库（开发库、以及任何跑过早期版本的环境）：本脚本什么都不用做，
--       直接跳过即可，不需要任何 ALTER。
--     - 全新的库：执行下面这段建表语句，得到与开发库完全一致的列名。
--
-- 设计说明：
--   1. 一个用户一行（uk_user_profile_user_id），画像代表"这个人的最新状态"，不留历史快照。
--   2. pref_tags 存 JSON 数组，元素是固定字段顺序的 {tag, weight}：
--      [{"tag":"自然风光","weight":3},{"tag":"吃吃喝喝","weight":1}]
--      —— 权重 = 命中次数，由规则累加（每轮对话 +1），AI 重写摘要时不允许改动。
--      —— 用固定字段顺序的类序列化（不用裸 Map），否则字段顺序不定，
--         管理端按标签做 JSON 子串筛选（LIKE '%"自然风光"%'）会时灵时不灵。
--      —— 已按 权重倒序、同权重按词表顺序 稳定排序后落库。
--   3. free_tags 只做展示，不参与筛选聚合（自由标签可能含引号，子串匹配不可靠）。
--   4. constraints 是硬性约束，多个用中文分号「；」分隔；规则只追加不覆盖。
--   5. last_source 表示"画像内容最后一次是谁写的"：
--      rule=规则累加 / model=摘要模型重写 / manual=运营人工修正。
--      规则累加不改它，否则"命中次数语义"和管理端展示的"人来改的还是机器写的"会混。
--   6. use_preference 是用户端唯一的可见开关，默认开。关掉只停"回灌"，不停止"累积"。
--
-- 执行位置：服务器 MySQL 对应库（本地开发库是 geek012）。
-- 可重复执行：create table if not exists，重复跑不会报错、也不会改动已有表。
-- ============================================================

create table if not exists geek012.user_profile
(
    id                 bigint auto_increment comment '用户画像主键' primary key,
    user_id            bigint                             not null comment '用户ID',
    pref_tags          varchar(2000)                      null comment '受控偏好标签及权重(JSON：[{"tag":"自然风光","weight":3}])',
    free_tags          varchar(2000)                      null comment 'AI 归纳的自由标签(JSON 数组，不参与筛选聚合，仅展示)',
    constraints        varchar(1000)                      null comment '硬性约束(如 不吃辣；带小孩)，分号分隔',
    budget_level       varchar(20)                        null comment '预算档位：经济/适中/高档',
    pace               varchar(20)                        null comment '节奏偏好：悠闲/常规/暴走',
    companions         varchar(50)                        null comment '同行人：独自/情侣/朋友/家庭',
    prefer_days        int                                null comment '偏好出行天数',
    summary_text       varchar(2000)                      null comment 'AI 生成的文字画像摘要',
    summary_update_time datetime                          null comment '摘要最后一次生成时间',
    chat_round_count   int      not null default 0        comment '累计对话轮数(摘要重写判断用)',
    last_source        varchar(10)                        null comment '画像最后更新来源：rule=规则累加 model=模型重写 manual=人工编辑',
    use_preference     tinyint  not null default 1        comment '回灌开关：1开启 0关闭',
    remark             varchar(500)                       null comment '运营备注',
    mark               varchar(50)                        null comment '运营标记(如 重点客户)',
    create_time        datetime not null default CURRENT_TIMESTAMP comment '创建时间',
    update_time        datetime not null default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_profile_user_id
        unique (user_id)
) comment '用户画像';

-- ------------------------------------------------------------
-- 执行后自查（把库名换成实际库名）：
--
--   -- 1) 表结构是否符合预期（重点看列名是不是 pref_tags / constraints / use_preference）
--   select column_name, column_type, is_nullable, column_default
--     from information_schema.columns
--    where table_schema = 'geek012' and table_name = 'user_profile'
--    order by ordinal_position;
--
--   -- 2) 唯一键在不在（并发首轮插入靠它兜底）
--   select index_name, column_name, non_unique
--     from information_schema.statistics
--    where table_schema = 'geek012' and table_name = 'user_profile';
--
-- 预期：唯一键 uk_user_profile_user_id(user_id) 存在，non_unique = 0。
-- ------------------------------------------------------------
