-- ============================================================
-- V2.2 聊天历史表补 user_id
-- 创建于 2026-09-14
--
-- 背景：
--   画像要"按用户聚合"，就必须知道每条消息属于谁。chat_history 原来只有 session_id，
--   session_id 是前端每轮带上来、后端按需生成的，不能反推用户，所以画像链路无法工作。
--
-- ★ 执行前必读（动手前已核对过开发库）★
--   开发库 geek012.chat_history **已经有 user_id 列和索引了**：
--     id / session_id / user_id / role / content / create_time
--     idx_chat_history_session_id(session_id)
--     idx_chat_history_session_time(session_id, create_time)
--     idx_chat_history_user_id(user_id)
--   所以：**在开发库上不需要再执行下面的 DDL**，直接跳过。
--   MySQL 不支持 ADD COLUMN IF NOT EXISTS，在有 user_id 的库上重复执行会报
--   Duplicate column name 'user_id'，这是预期报错，不是脚本有问题。
--   下面这段只给"还没加过这一列"的环境（比如生产库如果没跑过这一版）用。
--
-- 说明：
--   1. user_id 可空：存量历史消息没有可靠的归属依据，保持 NULL，
--      不做归属回溯（强行按 session 猜归属会串数据）。
--      画像读取侧对 user_id 为 NULL 的行直接忽略；
--      历史接口对 NULL 行不拦截，改成"只要会话里出现过别人的消息就拒绝返回"，
--      这样老会话仍能恢复，同时堵住登录用户拿到别人 sessionId 就读别人对话的口子。
--   2. 写入侧（ChatServiceImpl）从登录上下文取 userId 落库，本轮 user + assistant 两条都带。
--   3. 库名前缀按服务器实际库名调整（本地开发库是 geek012，
--      旧快照 部署表结构/chat_history.sql 里写的是 aitravel，两者不一致，
--      以服务器上真正在跑的那个库为准）。
-- ============================================================

-- 仅当 chat_history 还没有 user_id 列时执行（开发库已有，会报 Duplicate column name，属预期，跳过即可）：
alter table geek012.chat_history
    add column user_id bigint null comment '用户ID(存量历史为NULL，不做回溯)' after id;

alter table geek012.chat_history
    add index idx_chat_history_user_id (user_id);

-- ------------------------------------------------------------
-- 执行前先自查（把库名换成实际库名）：
--
--   select column_name, column_type, is_nullable
--     from information_schema.columns
--    where table_schema = 'geek012' and table_name = 'chat_history'
--    order by ordinal_position;
--
--   select index_name, group_concat(column_name order by seq_in_index) cols
--     from information_schema.statistics
--    where table_schema = 'geek012' and table_name = 'chat_history'
--    group by index_name;
--
-- 预期：能看到 user_id 列 + 一个以 user_id 起头的索引。看到了就说明本脚本无需执行。
--
-- 顺带确认（本次排查发现的其它环境漂移，与本功能无关但会挡主链路）：
--   - chat_history 实际是 role varchar(20) / content longtext，
--     而快照 部署表结构/chat_history.sql 写的是 varchar(16) / text —— 快照已过期。
--   - trip_plan 实际有 chat_session_id 列（实体 TripPlan.chatSessionId 在用），
--     但快照 部署表结构/trip_plan_geek012.sql 没声明这一列。
--   - 库里的应用用户表是 sys_user（User 实体 @TableName("sys_user")），
--     不是 部署表结构/user.sql 里的 user。
--   - geek012 里还有 ai_chat_session / ai_chat_message 两张**没有代码在用的遗留表**，
--     本次没有使用它们：真正生效的是 chat_history（ChatHistoryMapper）。
-- ------------------------------------------------------------
