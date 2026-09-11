# -*- coding: utf-8 -*-
"""临时验证脚本：知识库按城市返回（验证后删除）"""
import sys
sys.path.insert(0, r"D:\geek\AI文旅\geek012\travel_self_agent")

from knowledge_base.attraction_kb import AttractionKB
from knowledge_base.xhs_note_kb import XhsNoteKB

# 肇庆：应返回库数据
zq = AttractionKB.query_by_city_tag("肇庆", ["自然风光"])
zq_city = AttractionKB.query_by_city_tag("肇庆市", ["美食探店"])  # 归一化 + 标签未命中回退全部
print("肇庆(自然风光):", [s["name"] for s in zq])
print("肇庆市(美食探店):", [s["name"] for s in zq_city])

# 其他城市：应返回空
bj = AttractionKB.query_by_city_tag("北京", ["自然风光"])
cd = AttractionKB.query_by_city_tag("成都", ["美食探店"])
print("北京:", bj)
print("成都:", cd)

# 笔记库
print("笔记-肇庆:", XhsNoteKB.search_note("肇庆", []))
print("笔记-北京:", repr(XhsNoteKB.search_note("北京", [])))

assert [s["name"] for s in zq] == ["七星岩", "鼎湖山"], "肇庆应返回库景点"
assert bj == [] and cd == [], "无库城市应返回空列表"
assert XhsNoteKB.search_note("北京", []) == "", "无库城市笔记应为空串"
print("\nALL KB TESTS PASSED")
