# verify-e2e.ps1 —— 一键验证「前端 → 后端 → 智能体」整条真实链路。
#
# 为什么要有它：这条链路的故障表现极容易误判 ——
#   Agent 调模型失败时，Java 侧只看到 `Premature EOF`（看不到 401/404 等真实原因）；
#   .env 里 VITE_USE_MOCK=true 时，前端会返回**写死的假回复**，看起来像"AI 不理会我说的话"。
# 这两种"看起来像别的问题"的故障，本脚本都会直接把真正的原因指出来。
#
# 用法（先把 key 填进 travel_self_agent/.env.local，或保证 8002 已在跑）：
#   .\verify-e2e.ps1
[CmdletBinding()]
param(
    [string]$ProbeUser = 'probe_e2e_verify',
    [string]$ProbePass = 'probe123456'
)

$ErrorActionPreference = 'Continue'
$Root = $PSScriptRoot
$Backend = 'http://localhost:8080'
$Agent = 'http://127.0.0.1:8002'
$Front = 'http://localhost:5173'

$pass = 0; $fail = 0; $warn = 0
function Ok($m)   { Write-Host "  [OK]   $m" -ForegroundColor Green;  $script:pass++ }
function Bad($m)  { Write-Host "  [FAIL] $m" -ForegroundColor Red;    $script:fail++ }
function Warn($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow; $script:warn++ }
function Section($m) { Write-Host ''; Write-Host "=== $m ===" -ForegroundColor Cyan }

function Test-Port($p) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $p -ErrorAction SilentlyContinue | Select-Object -First 1)
}

# 统一用字节体发 JSON：Windows PowerShell 5.1 下把中文字符串直接当 Body 会按 ISO-8859-1 编码，
# 中文会变成 "?"，导致后端收到乱码后"怎么都不识别目的地"。
# 发 JSON 并**按 UTF-8 解码**响应。
#
# 为什么不用 Invoke-RestMethod：Windows PowerShell 5.1 下，后端返回 application/json 且没带 charset 时，
# 它会按 ISO-8859-1 解码，中文全变成 "æªç¥éè¯¯" 这种乱码 —— 数据本身没错，
# 但报告读不了，排查时反而添乱。这里用 curl 落盘再按 UTF-8 读，绕开这个坑。
# （请求体同理要用字节发：5.1 把中文字符串直接当 Body 会按 ISO-8859-1 编码成 "?"）
function Post-Json($url, $obj, $headers = @{}) {
    $json = $obj | ConvertTo-Json -Depth 8 -Compress
    $tag = [guid]::NewGuid().ToString('N')
    $bodyFile = Join-Path $env:TEMP "ve2e_body_$tag.json"
    $outFile = Join-Path $env:TEMP "ve2e_out_$tag.json"
    [System.IO.File]::WriteAllText($bodyFile, $json, (New-Object System.Text.UTF8Encoding($false)))

    $curlArgs = @(
        '-sS', '-m', '120', '-X', 'POST', $url,
        '-H', 'Content-Type: application/json; charset=utf-8',
        '--data-binary', "@$bodyFile",
        '--output', $outFile
    )
    foreach ($k in $headers.Keys) { $curlArgs += @('-H', "${k}: $($headers[$k])") }

    try {
        & curl.exe @curlArgs 2>&1 | Out-Null
        $text = if (Test-Path $outFile) { [System.IO.File]::ReadAllText($outFile, [System.Text.Encoding]::UTF8) } else { '' }
    } finally {
        Remove-Item $bodyFile, $outFile -ErrorAction SilentlyContinue
    }
    if ([string]::IsNullOrWhiteSpace($text)) { throw "服务无响应或返回空（$url）" }
    return ($text | ConvertFrom-Json)
}

# 发 JSON、按 UTF-8 读**纯文本**响应。
#
# 为什么需要它：Agent 的 /api/chat 是流式端点，返回的是 text/plain 而不是 JSON，
# 用 ConvertFrom-Json 去解必然抛「Invalid JSON primitive」。这条踩过。
function Post-Text($url, $obj, $headers = @{}) {
    $json = $obj | ConvertTo-Json -Depth 8 -Compress
    $tag = [guid]::NewGuid().ToString('N')
    $bodyFile = Join-Path $env:TEMP "ve2e_tb_$tag.json"
    $outFile = Join-Path $env:TEMP "ve2e_to_$tag.txt"
    [System.IO.File]::WriteAllText($bodyFile, $json, (New-Object System.Text.UTF8Encoding($false)))
    $curlArgs = @(
        '-sS', '-m', '120', '-X', 'POST', $url,
        '-H', 'Content-Type: application/json; charset=utf-8',
        '--data-binary', "@$bodyFile", '--output', $outFile
    )
    foreach ($k in $headers.Keys) { $curlArgs += @('-H', "${k}: $($headers[$k])") }
    try {
        & curl.exe @curlArgs 2>&1 | Out-Null
        if (Test-Path $outFile) { return [System.IO.File]::ReadAllText($outFile, [System.Text.Encoding]::UTF8) }
        return ''
    } finally {
        Remove-Item $bodyFile, $outFile -ErrorAction SilentlyContinue
    }
}

# 取字符串的“二字片段”集合，用于粗略衡量两段中文是否谈到了同样的东西。
# 用途：判断「猜你想问」的候选是否与这一轮的回答有内容关联。
# 之所以用近似判断而不是精确断言：模型措辞可能和回复不完全一致，
# 判不准时只给 WARN 并把内容打出来让人眼判断，避免测试给出误导性的红/绿。
function Get-Bigrams([string]$s) {
    $set = @{}
    $t = ($s -replace '[\s\p{P}\p{S}]', '')
    for ($i = 0; $i -lt $t.Length - 1; $i++) { $set[$t.Substring($i, 2)] = $true }
    return @($set.Keys)
}

Section '1. 服务端口'
if (Test-Port 8080) { Ok '后端 8080 在跑' } else { Bad '后端 8080 没起 —— 先跑 .\start-all.ps1' }
if (Test-Port 5173) { Ok '前端 5173 在跑' } else { Bad '前端 5173 没起 —— 先跑 .\start-all.ps1' }
$agentUp = Test-Port 8002
if ($agentUp) { Ok '智能体 8002 在跑' } else { Bad '智能体 8002 没起 —— 它需要 MAAS_API_KEY，见最后一段' }

Section '2. 前端是否真的在连后端（而不是 mock）'
try {
    $c = & curl.exe -sS -m 15 "$Front/src/utils/constant.ts" 2>&1 | Out-String
    if ($c -match '"VITE_USE_MOCK":\s*"false"') {
        Ok 'VITE_USE_MOCK = false（真实链路）'
    } elseif ($c -match '"VITE_USE_MOCK":\s*"true"') {
        Bad 'VITE_USE_MOCK = true —— 前端在返回假数据！改 frontend/.env.development.local 为 false 并重启前端'
    } else {
        Warn '没能读到 dev server 的 env（前端没跑？）'
    }
} catch { Warn "读取前端 env 失败: $($_.Exception.Message)" }

Section '3. 智能体直连（真模型调用）'
if (-not $agentUp) {
    Warn '跳过：智能体没在跑'
} else {
    try {
        # /api/chat 是流式纯文本，不是 JSON
        $text = Post-Text "$Agent/api/chat" @{ session_id = 'verify-probe'; user_input = '你好'; base_info = @{} }
        $reply = ($text -replace '\s+', ' ').Trim()
        if ($reply) {
            if ($reply -match 'AuthenticationError|invalid_api_key|model_not_found|Error code: 4\d\d') {
                Bad "模型调用失败：$($reply.Substring(0, [Math]::Min(160, $reply.Length)))"
                Write-Host '         → 先跑 .\check-agent-key.ps1 确认 key 与模型名' -ForegroundColor DarkGray
            } else {
                Ok "模型返回正常，回复开头：$($reply.Substring(0, [Math]::Min(40, $reply.Length)))"
            }
        } else {
            Bad '智能体没返回任何内容（连接建立了但读不到数据）'
        }
    } catch {
        Bad "调用智能体失败：$($_.Exception.Message)"
        Write-Host '         → 真正的原因在**智能体的那个窗口**里（Java 侧只会看到 Premature EOF）' -ForegroundColor DarkGray
        Write-Host '         → 或者先单独验 key 与模型名：.\check-agent-key.ps1' -ForegroundColor DarkGray
    }
}

Section '4. 端到端：登录 → 对话 → 槽位 → 猜你想问'
$token = $null
$sessionId = "e2e-$([guid]::NewGuid().ToString('N').Substring(0,8))"
try {
    try {
        Post-Json "$Backend/user/register" @{ username = $ProbeUser; password = $ProbePass; nickname = 'probe' } | Out-Null
    } catch { }
    $login = Post-Json "$Backend/user/login" @{ username = $ProbeUser; password = $ProbePass }
    $token = $login.data.token
    if ($token) { Ok "登录成功（token 长度 $($token.Length)）" } else { Bad "登录没拿到 token：$($login | ConvertTo-Json -Compress)" }
} catch { Bad "登录失败：$($_.Exception.Message)" }

if ($token) {
    $hdr = @{ Authentication = $token }

    # 第 1 轮：说一个**不在热门选项卡里**的城市，专门验证识别词典与 {city} 占位符
    try {
        $r1 = Post-Json "$Backend/travel/chat" @{
            session_id = $sessionId
            user_input = '我想去深圳'
            base_info  = @{}
        } $hdr
        if ($r1.code -eq 1) {
            Ok '第 1 轮对话返回 code=1'
            $reply = [string]$r1.data.reply
            if ($reply.Trim().Length -gt 0) {
                Ok "AI 有实际回复（$($reply.Length) 字）：$($reply.Substring(0, [Math]::Min(30, $reply.Length)).Replace("`n", ' '))"
            } else { Bad 'AI 回复为空' }

            $dest = $r1.data.slot_state.slots.destination.value
            if ($dest -eq '深圳') { Ok '目的地识别正确：深圳（识别词典生效）' }
            elseif ($dest) { Warn "目的地识别为「$dest」，期望「深圳」" }
            else { Bad '目的地没识别出来（深圳应能从句子里抽出）' }

            $sug = @($r1.data.suggested_questions)
            if ($sug.Count -ge 2) { Ok "「猜你想问」$($sug.Count) 条：$($sug -join ' / ')" }
            else { Bad "「猜你想问」只有 $($sug.Count) 条（至少要 2 条）" }

            # 候选应当与**这一轮的回答**有内容关联（而不是与目的地相关的通用问题）。
            # 用二字片段重合做近似判断；判不准只给 WARN，并把内容打出来供人眼确认。
            $related = 0
            $replyBigrams = Get-Bigrams $reply
            foreach ($q in $sug) {
                $qb = Get-Bigrams $q
                $hit = @($qb | Where-Object { $replyBigrams -contains $_ })
                if ($hit.Count -gt 0) { $related++ }
            }
            if ($sug.Count -gt 0) {
                if ($related -ge 1) {
                    Ok "候选与回复有内容关联（$related/$($sug.Count) 条命中与回复重合的词）"
                } else {
                    Warn '候选与回复没有重合词 —— 需人眼确认是否相关（也可能是模型换了个说法）'
                }
                Write-Host "         回复全文：$($reply.Substring(0, [Math]::Min(150, $reply.Length)))" -ForegroundColor DarkGray
            }
        } else {
            Bad "第 1 轮失败 code=$($r1.code) msg=$($r1.msg)"
        }
    } catch { Bad "第 1 轮请求异常：$($_.Exception.Message)" }

    # 第 2 轮：验证多轮上下文与每轮都给候选
    try {
        $r2 = Post-Json "$Backend/travel/chat" @{
            session_id = $sessionId
            user_input = '玩3天'
            base_info  = @{}
        } $hdr
        if ($r2.code -eq 1) {
            $days = $r2.data.slot_state.slots.days.value
            if ($days -eq '3') { Ok '第 2 轮天数识别正确：3' } else { Warn "天数识别为「$days」，期望 3" }
            $sug2 = @($r2.data.suggested_questions)
            if ($sug2.Count -ge 2) { Ok "第 2 轮仍有「猜你想问」$($sug2.Count) 条" } else { Bad "第 2 轮猜你想问只有 $($sug2.Count) 条" }
            $dup = $sug2 | Where-Object { $_ -in @($r1.data.suggested_questions) }
            if (@($dup).Count -eq 0) { Ok '两轮候选无重复' } else { Warn "两轮间有重复候选：$($dup -join ' / ')" }
        } else {
            Bad "第 2 轮失败 code=$($r2.code) msg=$($r2.msg)"
        }
    } catch { Bad "第 2 轮请求异常：$($_.Exception.Message)" }

    # 第 3 轮：新会话先说"你好"（不提城市）→ AI 会推荐几个地方，
    # 此时「你想去哪里？」的选项应当**优先展示它刚推荐的城市**，而不是固定的热门城市顺序。
    try {
        $sid2 = "e2e-city-$([guid]::NewGuid().ToString('N').Substring(0, 6))"
        $r3 = Post-Json "$Backend/travel/chat" @{
            session_id = $sid2
            user_input = '你好'
            base_info  = @{}
        } $hdr
        if ($r3.code -eq 1) {
            $reply3 = [string]$r3.data.reply
            $nq = $r3.data.slot_state.nextQuestion
            if ($nq -and $nq.slot -eq 'destination') {
                $opts = @($nq.options | ForEach-Object { $_.value })
                $lead = @($opts | Select-Object -First 3)
                $mentioned = @($lead | Where-Object { $_ -and $reply3.Contains($_) })
                if ($mentioned.Count -gt 0) {
                    Ok "目的地选项优先展示回复提到的城市：$($lead -join ' / ')"
                } else {
                    Warn "选项前三个不是回复里提到的城市：$($lead -join ' / ')（若回复确实没提城市则属正常）"
                }
                Write-Host "         本轮回复：$($reply3.Substring(0, [Math]::Min(90, $reply3.Length)).Replace("`n", ' '))" -ForegroundColor DarkGray
            } else {
                Warn "第 3 轮没拿到目的地问题（slot=$($nq.slot)），跳过选项排序检查"
            }
        } else {
            Warn "第 3 轮失败 code=$($r3.code)"
        }
        $script:ExtraSession = $sid2
    } catch { Warn "第 3 轮请求异常：$($_.Exception.Message)" }
}

Section '5. 清理探针数据'
try {
    $yml = Get-Content (Join-Path $Root 'backend\gkv-server\src\main\resources\application-dev.yml') -Raw
    if ($yml -match '(?ms)datasource:.*?password:\s*(\S+)') {
        $dbPass = $matches[1].Trim('"').Trim("'")
        $mysql = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe'
        if (Test-Path $mysql) {
            & $mysql -uroot "-p$dbPass" -e "DELETE FROM geek012.sys_user WHERE username='$ProbeUser'; DELETE FROM geek012.chat_history WHERE session_id LIKE 'e2e-%';" 2>&1 |
                Where-Object { $_ -notmatch 'Warning' } | Out-Null
            Ok '已删除探针账号与测试会话'
        } else { Warn "没找到 mysql 客户端，探针账号 $ProbeUser 需手动删" }
    } else { Warn '没读到数据库密码，跳过清理' }
} catch { Warn "清理失败：$($_.Exception.Message)" }

Section '结果'
Write-Host "  通过 $pass / 失败 $fail / 警告 $warn" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })

if ($fail -gt 0) {
    Write-Host ''
    Write-Host '如果卡在「智能体 8002 没起」或「模型没返回内容」：' -ForegroundColor Yellow
    Write-Host '  1) 先把 key 填进 travel_self_agent/.env.local（一行 MAAS_API_KEY=sk-xxx）'
    Write-Host '  2) .\start-all.ps1          # 不带参数即可，脚本会读到 .env.local'
    Write-Host '  3) 再跑 .\verify-e2e.ps1'
    Write-Host ''
    Write-Host '  只想单独验 key 与模型名：.\check-agent-key.ps1'
}
exit $(if ($fail -gt 0) { 1 } else { 0 })
