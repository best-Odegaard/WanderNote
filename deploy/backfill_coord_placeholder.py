#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""一次性回填：清理 trip 数据里的地址类脏数据。两类都治：

  A. 智能体留下的占位串「（坐标数据待核实）」
  B. 与占位串无关的既有问题：`"location":""`（空地址）、`"description":"📍 | 🕒…"`（悬空地址段）

清洗规则与后端 AdminFeaturedTripServiceImpl.cleanSpot 保持完全一致：
  - location：删掉占位串后为空 → 移除该字段
  - description：按 | 分段，删掉占位串后为空、或只剩 📍 → 丢掉这一段；全空 → 移除该字段

这样做让存量数据与新代码产出的形态一致（前端 `s.location || s.title` 对空值与字段缺失等价，
所以行为上没有区别，只是数据更干净）。

为什么不用全表 REPLACE：
  1. 直接 REPLACE 会留下 `"location":""` 和悬空的 `"description":"📍 | 🕒…"`，
     等于把要修的两个问题固化进库；
  2. 全表 REPLACE 分不清"原本就空"和"被清空"。这里改成按字段逐个判断：
     只有**确实存在缺陷**（含占位串 / 空地址 / 悬空地址段）的字段才动，其余字节原样保留。

注意：必须**全表扫描**——既有问题里有 4 处出现在不含占位串的行上
（trip_plan 3 行 + featured_trip 1 行），按占位串过滤会漏掉。

用法：
    python3 backfill_coord_placeholder.py            # dry-run，只统计不写库（默认）
    python3 backfill_coord_placeholder.py --apply    # 正式回填（务必先 mysqldump 备份）

备份：
    mysqldump -uroot -p'***' geek012 featured_trip trip_plan \\
      > /tmp/backup_ph_clean_$(date +%Y%m%d_%H%M%S).sql
"""
import argparse
import base64
import json
import re
import subprocess
import sys

DB = "geek012"
MYSQL = ["mysql", "-uroot", "-p123456", DB, "-N", "-B", "--raw"]

PH = "（坐标数据待核实）"
ADDR_PREFIX = "📍"

# 只在"取值里含占位串"时才会命中；其余字段一律不动
FIELD_RE = {
    "location": re.compile(r'([,{])"location":"((?:[^"\\]|\\.)*)"'),
    "description": re.compile(r'([,{])"description":"((?:[^"\\]|\\.)*)"'),
}

# 表名 -> 字段名
TARGETS = [
    ("featured_trip", "trip_json"),
    ("trip_plan", "day_plans"),
]


def run_sql(sql):
    r = subprocess.run(MYSQL + ["-e", sql], capture_output=True, text=True)
    if r.returncode != 0:
        print("SQL 执行失败：", r.stderr[:500], file=sys.stderr)
        sys.exit(1)
    return r.stdout


def fetch_rows(table, column):
    """用 base64 取回原始文本，避免 JSON 里的制表符/换行破坏 -B 的分列。
    全表扫描：既有问题有一部分不在含占位串的行上，按占位串过滤会漏。"""
    sql = "SELECT id, REPLACE(TO_BASE64(%s), '\\n', '') FROM %s" % (column, table)
    rows = []
    for line in run_sql(sql).split("\n"):
        if not line.strip():
            continue
        row_id, b64 = line.split("\t", 1)
        rows.append((row_id, base64.b64decode("".join(b64.split())).decode("utf-8")))
    return rows


def needs_clean(field, value):
    """只有确实存在缺陷的字段才值得改写，避免把无关行也重写一遍。"""
    if field == "location":
        return PH in value or value.strip() == ""
    if PH in value:
        return True
    # description：存在"空段"或"只剩 📍"的段就是缺陷
    for segment in value.split("|"):
        text = segment.replace(PH, "").strip()
        if not text or text == ADDR_PREFIX:
            return True
    return False


def clean_field_value(field, value):
    """返回清洗后的取值；返回 None 表示"该字段应该被移除"。"""
    if field == "location":
        cleaned = value.replace(PH, "").strip()
        return cleaned or None
    # description：按 | 分段，空段和只剩 📍 的地址段丢掉
    kept = []
    for segment in value.split("|"):
        text = segment.replace(PH, "").strip()
        if text and text != ADDR_PREFIX:
            kept.append(text)
    return " | ".join(kept) if kept else None


def rewrite(raw):
    """对整份 JSON 文本做精确手术。返回 (新文本, 改动列表)。"""
    changes = []

    def make_sub(field):
        pattern = FIELD_RE[field]

        def sub(m):
            lead, value = m.group(1), m.group(2)
            if not needs_clean(field, value):
                return m.group(0)          # 没有缺陷 → 原样保留
            new_value = clean_field_value(field, value)
            if new_value is None:
                changes.append((field, value, None, PH in value))
                if lead == "{":
                    # 它是对象的第一个键：保留 {，后面的逗号由末尾的 {, -> { 修掉
                    return "{"
                return ""
            if new_value == value:
                return m.group(0)          # 清洗后没变化 → 不算改动
            changes.append((field, value, new_value, PH in value))
            return '%s"%s":"%s"' % (lead, field, new_value)

        return sub

    new = FIELD_RE["location"].sub(make_sub("location"), raw)
    new = FIELD_RE["description"].sub(make_sub("description"), new)

    # 处理"首个键被移除后留下 {"p","x":1} 这种畸形"的情况：把 {, 修成 {
    new = new.replace("{,", "{")
    return new, changes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="正式写库（默认只 dry-run）")
    args = ap.parse_args()

    total_rows = total_changed = total_fields = 0
    removed = kept_cleaned = 0
    by_ph = by_pre = 0          # 改动按成因拆分：占位串相关 / 既有问题
    updates = []
    samples = []
    # 更细的分解：字段 × 成因 × (移除/清空后保留)，用来核对改动构成
    detail = {}
    pre_samples = []            # 既有问题的样例，便于人工确认没有误伤

    def bump(field, is_ph, is_removed):
        k = (field, "占位串" if is_ph else "既有", "移除" if is_removed else "清空保留")
        detail[k] = detail.get(k, 0) + 1
    # 清洗前后的"坏味道"计数，用来证明清洗到底有没有做到位
    bad = {"ph": 0, "empty_loc": 0, "dangling_desc": 0}
    bad_after = {"ph": 0, "empty_loc": 0, "dangling_desc": 0}

    def count_bad(text, into):
        into["ph"] += text.count(PH)
        into["empty_loc"] += text.count('"location":""')
        into["dangling_desc"] += text.count('"description":"%s | ' % ADDR_PREFIX)

    for table, column in TARGETS:
        rows = fetch_rows(table, column)
        print("\n==== %s.%s：扫描 %d 行" % (table, column, len(rows)))
        t_changed = 0
        for row_id, raw in rows:
            total_rows += 1
            new, changes = rewrite(raw)
            count_bad(raw, bad)
            count_bad(new, bad_after)
            if not changes:
                continue
            # 安全闸：改完必须仍是合法 JSON，且解析后的结构差异只应来自被清的字段
            try:
                before = json.loads(raw)
                after = json.loads(new)
            except Exception as e:
                print("  !! id=%s 改写后不是合法 JSON，跳过：%s" % (row_id, e), file=sys.stderr)
                continue
            n_removed = sum(1 for _, _, v, _ in changes if v is None)
            n_cleaned = len(changes) - n_removed
            removed += n_removed
            kept_cleaned += n_cleaned
            total_fields += len(changes)
            by_ph += sum(1 for _, _, _, is_ph in changes if is_ph)
            by_pre += sum(1 for _, _, _, is_ph in changes if not is_ph)
            for f, old_v, new_v, is_ph in changes:
                bump(f, is_ph, new_v is None)
                if not is_ph and len(pre_samples) < 6:
                    pre_samples.append((table, row_id, f, old_v, new_v))
            t_changed += 1
            total_changed += 1
            if len(samples) < 5:
                f, old_v, new_v, is_ph = changes[0]
                samples.append((table, row_id, f, old_v, new_v, is_ph))
            b64 = base64.b64encode(new.encode("utf-8")).decode("ascii")
            updates.append("UPDATE %s SET %s = CONVERT(FROM_BASE64('%s') USING utf8mb4) WHERE id = %s;"
                           % (table, column, b64, row_id))
        print("  需改写 %d 行" % t_changed)

    print("\n==== 合计 ====")
    print("扫描行数 %d，需改写 %d 行" % (total_rows, total_changed))
    print("字段改动 %d 处：其中整体移除 %d 处、清空后保留 %d 处" % (total_fields, removed, kept_cleaned))
    print("  按成因：占位串相关 %d 处，既有问题（空地址/悬空 📍）%d 处" % (by_ph, by_pre))
    print("\n坏味道计数（清洗前 → 清洗后）：")
    print("  占位串                    %d → %d" % (bad["ph"], bad_after["ph"]))
    print('  "location":""             %d → %d' % (bad["empty_loc"], bad_after["empty_loc"]))
    print('  "description":"%s | "      %d → %d' % (ADDR_PREFIX, bad["dangling_desc"], bad_after["dangling_desc"]))

    print("\n改动构成分解：")
    for (field, cause, kind), n in sorted(detail.items()):
        print("  %-12s %-4s %-6s %d 处" % (field, cause, kind, n))

    if pre_samples:
        print("\n既有问题样例（确认没有误伤）：")
        for table, row_id, f, old_v, new_v in pre_samples:
            print("  %s.id=%s %s" % (table, row_id, f))
            print("    原值: %r" % old_v)
            print("    新值: %r" % new_v)
    print("\n样例：")
    for table, row_id, f, old_v, new_v, is_ph in samples:
        print("  %s.id=%s %s（%s）" % (table, row_id, f, "占位串相关" if is_ph else "既有问题"))
        print("    原值: %r" % old_v)
        print("    新值: %r" % new_v)

    if not args.apply:
        print("\n[dry-run] 未写库。确认无误后加 --apply 正式执行。")
        return

    if not updates:
        print("\n没有需要写库的行。")
        return

    sql_file = "/tmp/backfill_coord_placeholder.sql"
    with open(sql_file, "w", encoding="utf-8") as f:
        f.write("\n".join(updates) + "\n")
    print("\n[apply] 执行 %d 条 UPDATE（SQL 文件 %s）..." % (len(updates), sql_file))
    r = subprocess.run(["mysql", "-uroot", "-p123456", DB], stdin=open(sql_file),
                       capture_output=True, text=True)
    if r.returncode != 0:
        print("写库失败：", r.stderr[:500], file=sys.stderr)
        sys.exit(1)
    print("[apply] 完成。")


if __name__ == "__main__":
    main()
