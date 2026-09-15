#!/usr/bin/env python3
"""把智能体里写死的模型名批量换掉。

智能体的模型名是硬编码在代码里的（不是配置项），散布在几处：
  - travel_self_agent/agent/travel_agent.py      主规划 + 辅助生成
  - travel_self_agent/agent/tools/search_tools.py 门票/信息抽取
所以换模型必须同时改这几处，改完重启智能体才生效。

用法（在 agent 根目录，即包含 travel_self_agent/ 的那层执行）：
    python3 agent_set_model.py qwen3.8-flash

会先备份成 *.bak_<时间戳>，再把所有 model="..." 里的旧 qwen 系列模型名替换掉，
并打印每处的改动。注意：只替换 qwen 系列，其他模型名（如 embedding）不动。
"""
import re
import shutil
import sys
import time
from pathlib import Path

TARGETS = [
    "travel_self_agent/agent/travel_agent.py",
    "travel_self_agent/agent/tools/search_tools.py",
]
MODEL_PATTERN = re.compile(r'model="(qwen[^"]*)"')


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    new_model = sys.argv[1]
    stamp = time.strftime("%Y%m%d_%H%M%S")
    changed = 0
    for rel in TARGETS:
        path = Path(rel)
        if not path.exists():
            print(f"跳过（不存在）：{rel}")
            continue
        src = path.read_text(encoding="utf-8")
        before = MODEL_PATTERN.findall(src)
        if not before:
            print(f"未发现模型配置：{rel}")
            continue
        shutil.copy2(path, path.with_suffix(path.suffix + f".bak_{stamp}"))
        out = MODEL_PATTERN.sub(f'model="{new_model}"', src)
        path.write_text(out, encoding="utf-8")
        after = MODEL_PATTERN.findall(out)
        print(f"{rel}\n  改前: {before}\n  改后: {after}")
        changed += len(before)
    print(f"共替换 {changed} 处，目标模型：{new_model}")
    if changed:
        print("记得重启智能体：pkill -9 -f '[t]ravel_self_agent.main' 后重新 nohup 启动")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
