# check-agent-key.ps1 —— 只验证「大模型 Key + 模型名」这一件事，不启动 Agent。
#
# 为什么需要它：Agent 调模型失败时，Java 侧只会看到 `Premature EOF`（连接提前关闭），
# 完全看不出原因——异常发生在流式响应开始之后，真实原因只在 Agent 窗口里。
# 这个脚本直接打一次模型，把 401 / 404 / 429 / 余额不足 一次分清，
# 省掉「起 Agent → 发消息 → 翻窗口日志」的来回。
#
# 用法：
#   .\check-agent-key.ps1 -ApiKey sk-xxx
#   .\check-agent-key.ps1              # 从 $env:MAAS_API_KEY 或 travel_self_agent/.env.local 取
#
# 注意：脚本会把 key 用在一次真实请求上（会消耗极少量额度）。
[CmdletBinding()]
param(
    [string]$ApiKey = '',
    # 指定要测的模型名；不传则读 travel_self_agent/config/agent.yml 里的 chat_model
    [string]$Model = ''
)

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$AgentDir = Join-Path $Root 'travel_self_agent'
$AgentYml = Join-Path $AgentDir 'config\agent.yml'

# 模型名默认从 Agent 的配置里读，保证测的就是实际会用的那个（避免"测了 A 却在用 B"）
$model = $Model
if (-not $model) {
    $model = 'qwen3.8-flash'
    if (Test-Path $AgentYml) {
        $line = Get-Content $AgentYml | Where-Object { $_ -match '^\s*chat_model\s*:' } | Select-Object -First 1
        if ($line) { $model = ($line -split ':', 2)[1].Trim().Trim('"') }
    }
}

# key 的三个来源，与 start-all.ps1 保持一致
if (-not $ApiKey) {
    if ($env:MAAS_API_KEY) {
        $ApiKey = $env:MAAS_API_KEY
    } else {
        $envLocal = Join-Path $AgentDir '.env.local'
        if (Test-Path $envLocal) {
            $l = Get-Content $envLocal | Where-Object { $_ -match '^\s*MAAS_API_KEY\s*=' } | Select-Object -First 1
            if ($l) { $ApiKey = ($l -split '=', 2)[1].Trim().Trim('"').Trim("'") }
        }
    }
}

if (-not $ApiKey) {
    Write-Host '[FAIL] 没有拿到 Key。三种给法：' -ForegroundColor Red
    Write-Host '       1) .\check-agent-key.ps1 -ApiKey sk-xxx'
    Write-Host '       2) $env:MAAS_API_KEY="sk-xxx"'
    Write-Host '       3) 新建 travel_self_agent/.env.local，写一行 MAAS_API_KEY=sk-xxx'
    exit 1
}

Write-Host "模型: $model"
Write-Host "Key : $($ApiKey.Substring(0, [Math]::Min(6, $ApiKey.Length)))...（长度 $($ApiKey.Length)）"
Write-Host '正在请求...'
Write-Host ''

$py = @'
import json, os, sys
from openai import OpenAI
base = "https://dashscope.aliyuncs.com/compatible-mode/v1"
c = OpenAI(api_key=os.environ["MAAS_API_KEY"], base_url=base)
try:
    r = c.chat.completions.create(
        model=os.environ["MAAS_MODEL"],
        messages=[{"role": "user", "content": "回复两个字：正常"}],
        timeout=30,
    )
    print("VERDICT=OK")
    print("REPLY=" + (r.choices[0].message.content or "").strip()[:40])
except Exception as e:
    print("VERDICT=FAIL")
    print("TYPE=" + type(e).__name__)
    print("MSG=" + str(e)[:600])
'@

$tmp = Join-Path $env:TEMP ('check_key_' + [guid]::NewGuid().ToString('N') + '.py')
[System.IO.File]::WriteAllText($tmp, $py, (New-Object System.Text.UTF8Encoding($false)))

$env:MAAS_API_KEY = $ApiKey
$env:MAAS_MODEL = $model
try {
    $out = & python $tmp 2>&1
} finally {
    Remove-Item $tmp -ErrorAction SilentlyContinue
}
$out | ForEach-Object { $_.ToString() }

Write-Host ''
if ($out -match 'VERDICT=OK') {
    Write-Host '[OK] Key 与模型都可用 —— 若 Agent 仍报错，问题不在 Key' -ForegroundColor Green
    exit 0
}

Write-Host '[FAIL] 调用失败。按下面这条判断怎么修：' -ForegroundColor Red
$text = ($out -join "`n")
if ($text -match 'invalid_api_key|401|Incorrect API key') {
    Write-Host '  → Key 无效或已失效：去阿里云百炼控制台确认 Key 还有效（常见：被删、被轮换、复制时少了字符）'
} elseif ($text -match 'model_not_found|404|does not exist') {
    Write-Host "  → 模型名「$model」在该账号下不可用：改 travel_self_agent/config/agent.yml 里的 chat_model / plan_model"
    Write-Host '     （注意 travel_agent.py 里也硬编码了同一个模型名，两处要一起改）'
} elseif ($text -match 'insufficient|quota|balance|欠费|余额') {
    Write-Host '  → 账号余额/额度不足：去百炼控制台充值或换 Key'
} elseif ($text -match 'rate limit|429') {
    Write-Host '  → 触发限流：稍后重试，或降低并发'
} elseif ($text -match 'Connection|timed out|SSL') {
    Write-Host '  → 网络问题：确认能访问 dashscope.aliyuncs.com（代理/防火墙）'
} else {
    Write-Host '  → 未归类，请把上面的 MSG 一整行贴出来'
}
exit 1
