-- ============================================================
-- V2.3 trip_plan 补 source_featured_id（精选行程复制来源）
-- 创建于 2026-09-17
--
-- 背景：
--   用户端「把精选行程添加到我的行程」是"读一次内嵌 JSON → 走 save() 插一条新行程"。
--   原来没有任何去重依据，连续快速点击会插出多条一模一样的行程。
--   修法：复制时把来源精选行程 id 落到 trip_plan.source_featured_id，
--         再入库前按 (user_id, source_featured_id) 查一次，命中就拒绝。
--   对应代码：FeaturedTripServiceImpl.copyToMine(...)。
--
-- ★ 执行前必读 ★
--   MySQL 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报
--   Duplicate column name 'source_featured_id' / Duplicate key name，
--   这是预期报错，不是脚本有问题 —— 看到就说明这一版已经跑过了，跳过即可。
--   先跑下面的自查 SQL 确认现状，再决定要不要执行 DDL。
--
-- 说明：
--   1. source_featured_id 可空：存量行程（用户自己生成/AI 生成）没有来源精选行程，
--      全部保持 NULL。不做归属回溯 —— 旧数据无从得知是从哪个精选行程复制来的，
--      硬猜会串数据，也会让本该允许的重复行程被误判。
--      去重只对"改动上线后新复制的精选行程"生效，历史重复数据保留原样。
--   2. 索引用**普通复合索引**而非唯一索引，是刻意的：
--      唯一索引确实能根治并发（check-then-insert 之间的窗口），但并发命中时
--      会抛 DuplicateKeyException，而 copyToMine 只 catch 了 JSON 解析异常，
--      结果会变成 500 而不是「您已添加过该精选行程」这句友好提示。
--      当前普通索引 + 应用层查重已能覆盖"连续快速点击"这个真实场景。
--      若后续要上唯一索引，需同步在 Service 层捕获 DuplicateKeyException 转成友好提示。
--      另注：唯一索引对 NULL 不去重（MySQL 语义），存量 NULL 行不会互相冲突。
--   3. 库名前缀按服务器实际库名调整（开发/线上库名都是 geek012）。
--
-- 关联提交：geek012 后端 8bb3eb8「修复连续快速点击"添加到我的行程"会添加多条相同行程的bug」
-- ============================================================

-- ------------------------------------------------------------
-- 执行前自查（把库名换成实际库名）：
--
--   select column_name, column_type, is_nullable
--     from information_schema.columns
--    where table_schema = 'geek012' and table_name = 'trip_plan'
--    order by ordinal_position;
--
--   select index_name, group_concat(column_name order by seq_in_index) cols
--     from information_schema.statistics
--    where table_schema = 'geek012' and table_name = 'trip_plan'
--    group by index_name;
--
-- 预期：能看到 source_featured_id 列 + 一个 idx_trip_plan_user_featured 索引。
--       两个都看到 → 本脚本无需执行。
-- ------------------------------------------------------------

-- 1. 加列（位置放在 like_count 之后，与实体 TripPlan 的字段顺序一致）
alter table geek012.trip_plan
    add column source_featured_id bigint null comment '来源精选行程id（复制来源，NULL=非复制而来）' after like_count;

-- 2. 加复合索引，给 copyToMine 的 (user_id, source_featured_id) 查重走索引
alter table geek012.trip_plan
    add index idx_trip_plan_user_featured (user_id, source_featured_id);

-- ------------------------------------------------------------
-- 执行后核对：
--   select count(*) from geek012.trip_plan where source_featured_id is not null;
--     → 刚上线应该是 0；跑一段时间后 = 从上架精选行程复制过来的行程条数
--
-- 顺带确认去重是否真的生效（同一用户 + 同一精选行程应该只有 1 行）：
--   select user_id, source_featured_id, count(*) c
--     from geek012.trip_plan
--    where source_featured_id is not null
--    group by user_id, source_featured_id
--   having c > 1;
--     → 正常情况下应返回空集
--
-- 存量环境漂移（与本功能无关，供参考）：
--   - trip_plan 实际已有 chat_session_id / start_date / end_date，
--     但快照 部署表结构/trip_plan_geek012.sql 没声明这几列，快照已过期。
--   - 应用用户表是 sys_user，不是 部署表结构/user.sql 里的 user。
-- ------------------------------------------------------------
