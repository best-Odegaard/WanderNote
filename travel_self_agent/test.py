import os
from agent.travel_agent import TravelAgent

# 初始化 Agent（自动读取环境变量）
agent = TravelAgent()

# 测试数据
base_info = """
{
  "city": "肇庆",
  "hobby": ["自然风光", "美食探店"],
  "budget": "2000",
  "days": "2天",
  "people": "两人"
}
"""
chat_history = ""
user_input = "帮我简单规划一下"

scenic_data = """
[
  {"name": "七星岩", "open_time": "08:00-17:30", "ticket": "78元", "feature_tag": "自然风光", "location": "肇庆市端州区"},
  {"name": "鼎湖山", "open_time": "08:00-18:00", "ticket": "60元", "feature_tag": "自然风光", "location": "肇庆市鼎湖区"}
]
"""
note_data = "推荐晚上去岩前村吃河鲜，裹蒸粽很有名。"

# 测试1：非流式聊天
print("=== 测试非流式聊天 ===")
reply = agent.chat_stage_sync(base_info, chat_history, user_input)
print("回复：", reply)

# 测试2：流式聊天
print("\n=== 测试流式聊天 ===")
for text in agent.chat_stage(base_info, chat_history, user_input):
    print(text, end="", flush=True)
print()  # 换行

# 测试3：规划生成
print("\n=== 测试规划 ===")
plan = agent.plan_stage(
    base_info=base_info,
    chat_history=chat_history,
    user_input="生成最终行程",
    scenic_data=scenic_data,
    note_data=note_data
)
print("行程标题：", plan["title"])
print("第一天景点数：", len(plan["day_list"][0]["schedule"]))