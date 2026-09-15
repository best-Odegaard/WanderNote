-- ============================================================
-- V1.9 行程表补日期字段
-- 创建于 2026-09-11
--
-- 背景：
--   前端 pages/plan/wizard.vue 一直在收集并提交出发/结束日期
--   （formatDateValue 生成 YYYY-MM-DD），但 trip_plan 表没有对应列，
--   TripPlanVO / TripPlan 实体也没有这两个字段，所以日期在保存链路上被丢弃，
--   行程列表接口也返回不了日期。
--   前端「我的行程」卡片的三态徽章（待出行 / 进行中 / 已结束）依赖这两个字段
--   与今天的先后关系来判定，缺字段时只能恒显示「待出行」。
--
-- 说明：
--   - 两列均可空，对既有存量数据无影响（保持 NULL）。
--   - 用 varchar(20) 而非 date：AI 生成链路可能产生非常规日期字符串，
--     前端只按字符串取「月.日」展示，不做数据库侧日期运算，用 varchar 更稳。
--   - MySQL 不支持 ADD COLUMN IF NOT EXISTS，本脚本为一次性变更，
--     重复执行会报 Duplicate column name，属预期。
-- ============================================================

alter table geek012.trip_plan
    add column start_date varchar(20) null comment '出发日期(YYYY-MM-DD)' after to_city,
    add column end_date   varchar(20) null comment '结束日期(YYYY-MM-DD)' after start_date;
