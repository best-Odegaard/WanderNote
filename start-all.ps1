<#
.SYNOPSIS
    WanderNote 行笺 —— 本地一键启动 / 停止。

.DESCRIPTION
    按依赖顺序拉起三件套：
      1) Java 后端      http://localhost:8080   （需要 MySQL 3306 + Redis 6379 已就绪）
      2) Python AI Agent http://localhost:8002   （需要 MAAS_API_KEY）
      3) 前端 H5        http://localhost:5173   （uni-app dev server）

    每个服务在独立窗口里跑，方便看日志、单独 Ctrl+C。

.PARAMETER ApiKey
    大模型 API Key。不传则依次尝试：环境变量 MAAS_API_KEY → travel_self_agent/.env.local。

.PARAMETER Rebuild
    强制重新打包后端（默认只在源码比 jar 新时打包）。

.PARAMETER Stop
    停掉本脚本启动的三个服务（按端口杀进程），不做启动。

.PARAMETER CheckOnly
    只做环境体检，不启动任何服务。

.PARAMETER NoBrowser
    启动完成后不自动打开浏览器。

.PARAMETER SkipFrontend
    不启动前端（只跑后端 + Agent）。

.PARAMETER SkipAgent
    不启动 AI Agent（只跑后端 + 前端；此时对话会失败）。

.EXAMPLE
    .\start-all.ps1
    .\start-all.ps1 -ApiKey sk-xxxxxxxx
    .\start-all.ps1 -Rebuild -NoBrowser
    .\start-all.ps1 -Stop
#>
[CmdletBinding()]
param(
    [string]$ApiKey = '',
    [switch]$Rebuild,
    [switch]$Stop,
    [switch]$CheckOnly,
    [switch]$NoBrowser,
    [switch]$SkipFrontend,
    [switch]$SkipAgent
)

$ErrorActionPreference = 'Stop'

# ── 与代码保持一致的端口 / 路径（改这里前先确认对应配置）──
# 前端端口：frontend/vite.config.ts 的 server.port
# 后端端口：backend/gkv-server/src/main/resources/application.yml 的 server.port
# Agent 端口：travel_self_agent/main.py 的 uvicorn.run(port=8002)
# 后端连 Agent：application.yml 的 agent.chat-url / agent.plan-url
$Root         = $PSScriptRoot
$BackendPort  = 8080
$AgentPort    = 8002
$WebPort      = 5173
$BackendDir   = Join-Path $Root 'backend'
$AgentDir     = Join-Path $Root 'travel_self_agent'
$FrontendDir  = Join-Path $Root 'frontend'
$BackendJar   = Join-Path $BackendDir 'gkv-server\target\gkv-server-1.0-SNAPSHOT.jar'

# ── 输出小工具（统一前缀，方便一眼分辨是脚本还是服务日志）──
function Write-Title($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }
function Write-Ok($text)    { Write-Host "  [OK]   $text" -ForegroundColor Green }
function Write-Warn2($text) { Write-Host "  [WARN] $text" -ForegroundColor Yellow }
function Write-Err2($text)  { Write-Host "  [FAIL] $text" -ForegroundColor Red }
function Write-Info($text)  { Write-Host "         $text" -ForegroundColor DarkGray }

# ── 基础探测 ──
function Test-PortListening([int]$Port) {
    $conns = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    return [bool]$conns
}

function Get-PortOwners([int]$Port) {
    $conns = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    $out = @()
    foreach ($c in $conns) {
        $p = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
        if ($p) { $out += "$($p.ProcessName)(PID $($p.Id))" }
    }
    return $out
}

function Test-CommandExists([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Wait-Http([string]$Url, [int]$TimeoutSec = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { return $true }
        } catch {
            # 401 也算“服务起来了”：说明 Spring 已经在处理请求
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 401) { return $true }
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Open-ServiceWindow([string]$Title, [string]$WorkDir, [string]$Command, [string]$LogFile = '') {
    # 用 Start-Transcript 额外落一份日志到文件。
    #
    # 为什么：服务窗口里的报错**只能盯着那个窗口看**，排查时抓不到 ——
    # 这次 Agent 起不来，就因为看不到窗口内容而反复猜了很久。
    # Transcript 只是"顺带记一份"，窗口里该显示的照常显示，不影响原有使用方式。
    $prelude = ''
    if ($LogFile) {
        $logDir = Split-Path $LogFile -Parent
        if ($logDir -and -not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
        $prelude = "try { Start-Transcript -Path '$LogFile' -Force | Out-Null } catch {}; "
    }
    $inner = "`$Host.UI.RawUI.WindowTitle = '$Title'; $prelude$Command"

    # 用 -EncodedCommand（UTF-16LE 的 Base64）而不是 -Command。
    #
    # 原因：Start-Process 会把 -ArgumentList 用空格拼成一条命令行，再交给新进程**重新解析**，
    # 命令串里的**单引号会被吞掉**。Agent 那条命令恰好带引号
    # （标题 'WanderNote Agent :8002' 与 key 'sk-...'），于是解析失败、python 根本没跑起来 ——
    # 表现为「窗口开了，但 8002 一直不监听，脚本只报 60 秒未就绪」。
    # 后端/前端那条命令不含引号，所以一直正常，把这个 bug 掩盖了很久。
    # EncodedCommand 完全绕开命令行解析与编码问题（顺带也不再担心中文路径）。
    $encoded = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($inner))

    # 窗口样式**必须显式指定**，而且要在无交互控制台时降级。
    #
    # 实测（同一台机器，两种上下文各跑一次）：
    #   有交互控制台（用户自己开终端跑）    : 不指定 / Normal 都能正常执行命令
    #   无交互控制台（被其它程序拉起的 shell）: 不指定 / Normal **命令一条都不执行**
    #                                         Minimized / Hidden 才执行
    # 症状极具迷惑性：窗口进程建出来了（进程列表里看得到），但里面 python/java/node
    # 一个都没起来，脚本只报「60 秒内未就绪」。这次排查 Agent 起不来就是栽在这里。
    $style = if ($script:HasInteractiveConsole) { 'Normal' } else { 'Minimized' }
    Start-Process -FilePath 'powershell' `
        -ArgumentList @('-NoExit', '-EncodedCommand', $encoded) `
        -WorkingDirectory $WorkDir -WindowStyle $style | Out-Null
}

# 是否具备交互式控制台：决定服务窗口用 Normal 还是 Minimized（原因见 Open-ServiceWindow 注释）
$script:HasInteractiveConsole = $true
try { $null = [Console]::WindowWidth } catch { $script:HasInteractiveConsole = $false }

# 服务日志目录（Start-Transcript 落盘位置）
$LogDir = Join-Path $PSScriptRoot 'logs'

# ── 停止模式 ──
if ($Stop) {
    Write-Title '停止本地服务'
    $any = $false
    foreach ($item in @(
            @{ Port = $BackendPort; Name = '后端' },
            @{ Port = $AgentPort;   Name = 'AI Agent' },
            @{ Port = $WebPort;     Name = '前端' }
        )) {
        $owners = Get-PortOwners $item.Port
        if ($owners.Count -eq 0) {
            Write-Info "$($item.Name) :$($item.Port) 未在运行"
            continue
        }
        foreach ($o in $owners) {
            Write-Warn2 "停止 $($item.Name) :$($item.Port) → $o"
        }
        $conns = Get-NetTCPConnection -State Listen -LocalPort $item.Port -ErrorAction SilentlyContinue
        foreach ($c in $conns) {
            Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
        }
        $any = $true
    }
    if ($any) { Write-Ok '已停止' } else { Write-Info '没有需要停止的服务' }
    return
}

# ── 1. 依赖体检 ──
Write-Title '环境体检'

$missing = @()
foreach ($c in @('java', 'python', 'node', 'npm')) {
    if (Test-CommandExists $c) { Write-Ok "$c 已安装" } else { Write-Err2 "$c 未找到"; $missing += $c }
}
if (-not (Test-CommandExists 'mvn')) {
    # 只有需要重新打包时才用得上 maven
    Write-Warn2 'mvn 未找到（若后端 jar 已存在且不需重打包，可以忽略）'
}
if ($missing.Count -gt 0) {
    Write-Err2 "缺少必需命令：$($missing -join ', ')，请先安装后重试"
    exit 1
}

# MySQL / Redis 由后端依赖，脚本不负责拉起
foreach ($dep in @(
        @{ Port = 3306; Name = 'MySQL' },
        @{ Port = 6379; Name = 'Redis' }
    )) {
    if (Test-PortListening $dep.Port) {
        Write-Ok "$($dep.Name) :$($dep.Port) 已在运行"
    } else {
        Write-Err2 "$($dep.Name) :$($dep.Port) 未监听 —— 后端会启动失败，请先启动它"
    }
}

# 前端依赖装了没
if (-not $SkipFrontend) {
    if (Test-Path (Join-Path $FrontendDir 'node_modules')) {
        Write-Ok '前端 node_modules 已存在'
    } else {
        Write-Warn2 '前端 node_modules 不存在，将先执行 npm install（较慢）'
    }
}

# Python 依赖
if (-not $SkipAgent) {
    $depOk = $false
    try {
        & python -c "import fastapi, uvicorn, langchain_openai" 2>$null
        $depOk = ($LASTEXITCODE -eq 0)
    } catch { $depOk = $false }
    if ($depOk) {
        Write-Ok 'Python 依赖（fastapi / uvicorn / langchain_openai）已安装'
    } else {
        Write-Warn2 'Python 依赖不完整，将执行：pip install fastapi uvicorn langchain langchain-openai pydantic pyyaml'
    }
}

# 地图 Key：为空时批次 2 的路线规划不可用（不阻断启动，只提示）
$envLocal = Join-Path $FrontendDir '.env.development.local'
$mapKeyEmpty = $false
if (Test-Path $envLocal) {
    $content = Get-Content $envLocal -Raw
    if ($content -match '(?m)^\s*VITE_MAP_WS_KEY\s*=\s*$') { $mapKeyEmpty = $true }
} else {
    $mapKeyEmpty = $true
}
if ($mapKeyEmpty) {
    Write-Warn2 '前端未配置地图 Key（VITE_MAP_WS_KEY 为空）—— 行程定位与路线规划会不可用'
    Write-Info '可从 .env.production.local 复制过来：'
    Write-Info '  cd frontend; (Get-Content .env.production.local | Select-String "VITE_MAP") | Add-Content .env.development.local'
}

# ── 2. 大模型 Key ──
# Agent 已经在跑就不用再要 Key（本地开发常见：Agent 开着，只想把后端拉起来）
if (-not $SkipAgent -and (Test-PortListening $AgentPort)) {
    Write-Title 'AI Agent 配置'
    Write-Ok "AI Agent :$AgentPort 已在运行，无需 MAAS_API_KEY"
} elseif (-not $SkipAgent) {
    Write-Title 'AI Agent 配置'
    if (-not $ApiKey) {
        if ($env:MAAS_API_KEY) {
            $ApiKey = $env:MAAS_API_KEY
            Write-Ok 'MAAS_API_KEY 取自环境变量'
        } else {
            $agentEnv = Join-Path $AgentDir '.env.local'
            if (Test-Path $agentEnv) {
                $line = Get-Content $agentEnv | Where-Object { $_ -match '^\s*MAAS_API_KEY\s*=' } | Select-Object -First 1
                if ($line) {
                    $ApiKey = ($line -split '=', 2)[1].Trim().Trim('"').Trim("'")
                    if ($ApiKey) { Write-Ok "MAAS_API_KEY 取自 travel_self_agent/.env.local" }
                }
            }
        }
    } else {
        Write-Ok 'MAAS_API_KEY 由 -ApiKey 传入'
    }

    if (-not $ApiKey) {
        Write-Err2 '缺少大模型 API Key（Agent 启动即报错）'
        Write-Info '三种给法，任选其一：'
        Write-Info '  1) $env:MAAS_API_KEY="sk-xxx"  然后重跑本脚本'
        Write-Info '  2) .\start-all.ps1 -ApiKey sk-xxx'
        Write-Info '  3) 新建 travel_self_agent/.env.local，写入一行：MAAS_API_KEY=sk-xxx'
        exit 1
    }
}

# ── 3. 端口占用 ──
# 已占用的服务不重复启动、也不报错退出：本地开发最常见的场景就是
# 「Agent 和前端还开着，只想把后端拉起来」，此时沿用已有实例更省事。
Write-Title '端口检查'
$runningBackend = Test-PortListening $BackendPort
$runningAgent   = (-not $SkipAgent) -and (Test-PortListening $AgentPort)
$runningWeb     = (-not $SkipFrontend) -and (Test-PortListening $WebPort)

foreach ($item in @(
        @{ Port = $BackendPort; Name = '后端';  Running = $runningBackend; Skip = $false },
        @{ Port = $AgentPort;   Name = 'Agent'; Running = $runningAgent;   Skip = [bool]$SkipAgent },
        @{ Port = $WebPort;     Name = '前端';  Running = $runningWeb;     Skip = [bool]$SkipFrontend }
    )) {
    if ($item.Skip) { continue }
    if ($item.Running) {
        $owners = Get-PortOwners $item.Port
        Write-Warn2 "$($item.Name) :$($item.Port) 已在运行（$($owners -join ', ')）—— 沿用，不重复启动"
    } else {
        Write-Ok "$($item.Name) :$($item.Port) 空闲，将启动"
    }
}

if ($CheckOnly) {
    Write-Title '体检结束（-CheckOnly 不启动服务）'
    return
}

# ── 4. 打包后端（按需；已在运行则跳过）──
if ($runningBackend) {
    Write-Title '后端构建'
    Write-Info '后端已在运行，跳过打包'
} else {
    Write-Title '后端构建'
    $needBuild = $true
    if (Test-Path $BackendJar) {
        $jarTime = (Get-Item $BackendJar).LastWriteTime
        $newest = Get-ChildItem -Path $BackendDir -Recurse -Include *.java, *.yml, *.xml -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\target\\' } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $Rebuild -and $newest -and $newest.LastWriteTime -le $jarTime) {
            $needBuild = $false
        }
    }
    if ($needBuild) {
        if (-not (Test-CommandExists 'mvn')) {
            Write-Err2 '需要重新打包后端，但未找到 mvn'
            exit 1
        }
        Write-Info '执行 mvn -B -pl gkv-server -am -DskipTests package ...'
        Push-Location $BackendDir
        try {
            & mvn -B -pl gkv-server -am -DskipTests package
            if ($LASTEXITCODE -ne 0) { Write-Err2 '后端打包失败'; exit 1 }
        } finally {
            Pop-Location
        }
        Write-Ok '后端打包完成'
    } else {
        Write-Ok 'jar 已是最新，跳过打包（要强制重打包加 -Rebuild）'
    }
}

# ── 5. 启动 ──
Write-Title '启动服务'

if ($runningBackend) {
    Write-Info "后端 :$BackendPort 已在运行，跳过"
} else {
    Write-Info "启动后端 :$BackendPort ..."

    # 先把 jar 复制到 logs/run/ 再从副本启动。
    #
    # 为什么必须这样：直接跑 target/ 里的 jar 时，只要有人（IDE、mvn package、
    # 本脚本的 -Rebuild）重新构建，Maven 会 clean 掉 target 目录、重写这个 jar ——
    # 运行中的 JVM 是按需从 jar 里读 class 的，文件被换掉/删掉之后就抛
    # ClassNotFoundException 然后直接退出，**没有 shutdown 日志也没有堆栈**，
    # 看起来像「无缘无故挂了」（2026-10-02 和 2026-10-08 各踩过一次）。
    # 从副本启动后，构建再也影响不到正在跑的这个进程。
    $runDir = Join-Path $LogDir 'run'
    $runJar = Join-Path $runDir 'gkv-server.jar'
    if (-not (Test-Path $runDir)) { New-Item -ItemType Directory -Path $runDir -Force | Out-Null }
    Copy-Item $BackendJar $runJar -Force
    Write-Info "已复制运行副本：logs\run\gkv-server.jar"

    # 命令里刻意只用相对路径 + ASCII 标题：
    # 本脚本所在路径含中文（AI文旅），而 Start-Process 的命令串在 Windows PowerShell 下
    # 会受控制台编码影响，把中文路径拼进命令串有启动失败的风险。
    # -WorkingDirectory 是真正的参数（不走字符串解析），中文目录由它来承载。
    Open-ServiceWindow -Title "WanderNote Backend :$BackendPort" -WorkDir $Root -LogFile (Join-Path $LogDir 'backend.log') `
        -Command 'java -jar logs\run\gkv-server.jar'
}

if ($SkipAgent) {
    Write-Info '按 -SkipAgent 跳过 AI Agent'
} elseif ($runningAgent) {
    Write-Info "AI Agent :$AgentPort 已在运行，跳过"
} else {
    Write-Info "启动 AI Agent :$AgentPort ..."
    Open-ServiceWindow -Title "WanderNote Agent :$AgentPort" -WorkDir $AgentDir -LogFile (Join-Path $LogDir 'agent.log') `
        -Command "`$env:MAAS_API_KEY='$ApiKey'; python main.py"
}

if ($SkipFrontend) {
    Write-Info '按 -SkipFrontend 跳过前端'
} elseif ($runningWeb) {
    Write-Info "前端 :$WebPort 已在运行，跳过"
} else {
    if (-not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
        Write-Info '安装前端依赖（npm install）...'
        Push-Location $FrontendDir
        try {
            & npm install
            if ($LASTEXITCODE -ne 0) { Write-Err2 'npm install 失败'; exit 1 }
        } finally { Pop-Location }
    }
    Write-Info "启动前端 :$WebPort ..."
    Open-ServiceWindow -Title "WanderNote Web :$WebPort" -WorkDir $FrontendDir -LogFile (Join-Path $LogDir 'web.log') -Command 'npm run dev:h5'
}

# ── 6. 等就绪 ──
Write-Title '等待服务就绪'
Write-Info '后端冷启动约 25-40 秒（Spring 容器 + 连接池），请稍候...'
$apiReady = Wait-Http "http://localhost:$BackendPort/home/index" 120
if ($apiReady) { Write-Ok "后端已就绪 http://localhost:$BackendPort" }
else { Write-Warn2 "后端 120 秒内未就绪，请查看「WanderNote 后端」窗口的日志" }

if (-not $SkipAgent) {
    $agentReady = Wait-Http "http://localhost:$AgentPort/docs" 60
    if ($agentReady) { Write-Ok "AI Agent 已就绪 http://localhost:$AgentPort" }
    else { Write-Warn2 "AI Agent 60 秒内未就绪，请查看「WanderNote Agent」窗口的日志" }
}

if (-not $SkipFrontend) {
    $webReady = $false
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening $WebPort) { $webReady = $true; break }
        Start-Sleep -Seconds 2
    }
    if ($webReady) { Write-Ok "前端已就绪 http://localhost:$WebPort" }
    else { Write-Warn2 "前端 120 秒内未监听 $WebPort，请查看「WanderNote 前端」窗口的日志" }
}

# ── 7. 汇总 ──
Write-Title '启动完成'
Write-Host "  前端（用户端）  http://localhost:$WebPort" -ForegroundColor White
Write-Host "  后端接口文档    http://localhost:$BackendPort/doc.html" -ForegroundColor White
if (-not $SkipAgent) {
    Write-Host "  AI Agent 文档   http://localhost:$AgentPort/docs" -ForegroundColor White
}
Write-Host ''
Write-Host '  停止：.\start-all.ps1 -Stop' -ForegroundColor DarkGray
Write-Host '  各服务日志在各自弹出的窗口里；关掉那个窗口即等于停掉该服务。' -ForegroundColor DarkGray

if (-not $NoBrowser -and -not $SkipFrontend) {
    Start-Process "http://localhost:$WebPort" | Out-Null
}
