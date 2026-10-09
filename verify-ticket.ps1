# 订票链路自测（本地或任意环境）
#
# 覆盖：火车/机票候选查询 / 往返两个方向 / 未收录与空方向的降级 / 深链生成 /
#       行程未保存时只校验 / 选定落库与读回 / 重复选定只留一条 / 单程（清返程）/
#       自驾（无班次方式：无车次号、无深链，但有里程耗时）/ 越权
#
# 用法：
#   .\verify-ticket.ps1                                   # 后端默认 http://127.0.0.1:8080
#   .\verify-ticket.ps1 -Backend http://1.2.3.4:8080      # 打远端
#
# 前置：先执行 `部署表结构/V2.8__create_ticket.sql`（建两张表 + 45 条种子）。
#   MySQL 明细校验（同一方向只留一条、清空返程真的删了行）需要库连接信息，
#   缺省从环境变量取，取不到就跳过这两项：
#   $env:MYSQL_EXE / MYSQL_USER / MYSQL_PASSWORD / MYSQL_DB
#
# 注意：本脚本用 curl.exe 发请求并把响应**按 UTF-8 落盘再读**。
#   Windows PowerShell 5.1 下 Invoke-RestMethod 在响应没有 charset 时按 ISO-8859-1 解码，
#   中文会全变乱码；请求体也必须用字节发，否则中文会被编成 '?'。这两条都在 verify-e2e.ps1 里踩过。
param(
    [string]$Backend = 'http://127.0.0.1:8080',
    [string]$MysqlExe = $env:MYSQL_EXE,
    [string]$MysqlUser = $env:MYSQL_USER,
    [string]$MysqlPassword = $env:MYSQL_PASSWORD,
    [string]$Database = $(if ($env:MYSQL_DB) { $env:MYSQL_DB } else { 'geek012' })
)

$ErrorActionPreference = 'Stop'
$pass = 0; $fail = 0; $skip = 0

function Post-Json($url, $obj, $headers = @{}) {
    $json = $obj | ConvertTo-Json -Depth 8 -Compress
    $tag = [guid]::NewGuid().ToString('N')
    $bodyFile = Join-Path $env:TEMP "ticket_tb_$tag.json"
    $outFile = Join-Path $env:TEMP "ticket_to_$tag.txt"
    [System.IO.File]::WriteAllText($bodyFile, $json, (New-Object System.Text.UTF8Encoding($false)))
    $curlArgs = @('-sS', '-m', '60', '-X', 'POST', $url,
        '-H', 'Content-Type: application/json; charset=utf-8',
        '--data-binary', "@$bodyFile", '--output', $outFile)
    foreach ($k in $headers.Keys) { $curlArgs += @('-H', "${k}: $($headers[$k])") }
    try {
        & curl.exe @curlArgs 2>&1 | Out-Null
        return ([System.IO.File]::ReadAllText($outFile, [System.Text.Encoding]::UTF8) | ConvertFrom-Json)
    } finally { Remove-Item $bodyFile, $outFile -ErrorAction SilentlyContinue }
}

function Get-Json($url, $headers = @{}) {
    $tag = [guid]::NewGuid().ToString('N')
    $outFile = Join-Path $env:TEMP "ticket_go_$tag.txt"
    $curlArgs = @('-sS', '-m', '60', $url, '--output', $outFile)
    foreach ($k in $headers.Keys) { $curlArgs += @('-H', "${k}: $($headers[$k])") }
    try {
        & curl.exe @curlArgs 2>&1 | Out-Null
        return ([System.IO.File]::ReadAllText($outFile, [System.Text.Encoding]::UTF8) | ConvertFrom-Json)
    } finally { Remove-Item $outFile -ErrorAction SilentlyContinue }
}

# 直接问库（不经应用层），用来验证明细行数这类「接口看不到」的事实
function Query-Scalar($sql) {
    $out = cmd /c "`"$MysqlExe`" -u$MysqlUser -p`"$MysqlPassword`" --default-character-set=utf8mb4 -N -B $Database -e `"$sql`" 2>nul"
    return (($out | Out-String).Trim())
}

function Check($name, $cond, $detail = '') {
    if ($cond) { $script:pass++; Write-Host "  PASS  $name" }
    else { $script:fail++; Write-Host "  FAIL  $name  $detail" }
}

Write-Host "订票链路自测 → $Backend"

$gz = [uri]::EscapeDataString('广州')
$zq = [uri]::EscapeDataString('肇庆')
$cd = [uri]::EscapeDataString('成都')

# ── 0. 探针账号（两个：一个用来验证越权） ──
$u1 = "ticketprobe$((Get-Random -Maximum 99999))"
Post-Json "$Backend/user/register" @{ username = $u1; password = 'probe123456'; nickname = 'probe' } | Out-Null
$login = Post-Json "$Backend/user/login" @{ username = $u1; password = 'probe123456' }
$token = $login.data.token
Check '登录拿到 token' ($null -ne $token -and $token.Length -gt 10) "resp=$($login | ConvertTo-Json -Compress)"
$hdr = @{ Authentication = $token }

$u2 = "ticketprobe$((Get-Random -Maximum 99999))"
Post-Json "$Backend/user/register" @{ username = $u2; password = 'probe123456'; nickname = 'probe' } | Out-Null
$hdr2 = @{ Authentication = (Post-Json "$Backend/user/login" @{ username = $u2; password = 'probe123456' }).data.token }

# ── 1. 火车票候选与 12306 深链 ──
$r = Get-Json "$Backend/ticket/search?type=train&from=$gz&to=$zq&date=2026-10-20" $hdr
Check '广州→肇庆 火车 3 个班次' ($r.code -eq 1 -and $r.data.Count -eq 3) "count=$($r.data.Count) msg=$($r.msg)"

if ($r.data.Count -gt 0) {
    $first = $r.data[0]
    Check '按出发时刻升序（07:12 在最前）' ($first.departTime -eq '07:12') "first=$($first.departTime)"
    Check '每个班次都有购买深链' (($r.data | Where-Object { -not $_.purchaseUrl }).Count -eq 0)
    Check '12306 深链是绝对 https 地址' ($first.purchaseUrl -match '^https://')
    Check '12306 深链带出发站' ($first.purchaseUrl -match [uri]::EscapeDataString('广州南'))
    Check '12306 深链带到达站' ($first.purchaseUrl -match [uri]::EscapeDataString('肇庆东'))
    Check '12306 深链带日期' ($first.purchaseUrl -match 'date=2026-10-20')
    Check '票种是 train' ($first.transportType -eq 'train')
    Check '价格是参考价语义（接口无余票/库存字段）' (
        $first.PSObject.Properties.Name -notcontains 'stock' -and
        $first.PSObject.Properties.Name -notcontains 'seatLeft')

    # 前端 utils/tickets.ts 的 toTicketOption 会逐字段读这些值，
    # 少一个就表现为「列表空白 / 价格 0 / 点不动 12306」——在接口层先卡住
    $need = @('id', 'transportType', 'carrier', 'ticketNo', 'fromCity', 'toCity', 'fromStation',
        'toStation', 'departTime', 'arriveTime', 'durationMin', 'seatClass', 'price', 'stops',
        'tags', 'purchaseUrl')
    $missing = @()
    foreach ($f in $need) {
        if ($first.PSObject.Properties.Name -notcontains $f) { $missing += $f }
    }
    Check '响应字段齐全（前端逐字段读取）' ($missing.Count -eq 0) "missing=$($missing -join ',')"
    Check 'tags 是数组（页面 v-for 读它）' ($first.tags -is [array]) "type=$($first.tags.GetType().Name)"

    # ★ 合规：接口里绝不能出现证件号/账号这类购票凭证字段
    $forbidden = @('idCard', 'idNo', 'passport', 'mobile', 'phone', 'account', 'password', '12306Account')
    $leak = @()
    foreach ($f in $first.PSObject.Properties.Name) {
        if ($forbidden -contains $f) { $leak += $f }
    }
    Check '响应里没有任何购票凭证字段（合规红线）' ($leak.Count -eq 0) "leak=$($leak -join ',')"
}

# ── 2. 机票候选与携程深链 ──
$rf = Get-Json "$Backend/ticket/search?type=flight&from=$gz&to=$cd&date=2026-10-21" $hdr
Check '广州→成都 机票 2 个航班' ($rf.data.Count -eq 2) "count=$($rf.data.Count)"
if ($rf.data.Count -gt 0) {
    Check '机票深链带出发城市' ($rf.data[0].purchaseUrl -match [uri]::EscapeDataString('广州'))
    Check '机票深链带到达城市' ($rf.data[0].purchaseUrl -match [uri]::EscapeDataString('成都'))
    Check '机票深链带日期' ($rf.data[0].purchaseUrl -match 'ddate=2026-10-21')
    Check '票种是 flight' ($rf.data[0].transportType -eq 'flight')
}

# ── 3. 往返：返程方向反向查得到 ──
$rb = Get-Json "$Backend/ticket/search?type=train&from=$zq&to=$gz&date=2026-10-22" $hdr
Check '肇庆→广州 返程 2 个班次' ($rb.data.Count -eq 2) "count=$($rb.data.Count)"

# 不传 type 时不限票种（同一线路的火车与飞机都返回）
$rAll = Get-Json "$Backend/ticket/search?from=$gz&to=$cd" $hdr
Check '不限票种时火车与飞机混排 4 条' ($rAll.data.Count -eq 4) "count=$($rAll.data.Count)"

# ── 4. 降级：未收录线路 / 缺方向 ──
$rEmpty = Get-Json "$Backend/ticket/search?type=train&from=$gz&to=$([uri]::EscapeDataString('拉萨'))" $hdr
Check '未收录线路返回空数组（前端降级为跳官网）' ($rEmpty.code -eq 1 -and $rEmpty.data.Count -eq 0)

$rNoTo = Get-Json "$Backend/ticket/search?type=train&from=$gz" $hdr
Check '缺目的地返回空数组（没有方向的班次列表无意义）' ($rNoTo.code -eq 1 -and $rNoTo.data.Count -eq 0)

# 城市写法容错：带「市」后缀也要能查到（后端 normalizeCity）
$rCity = Get-Json "$Backend/ticket/search?type=train&from=$([uri]::EscapeDataString('广州市'))&to=$([uri]::EscapeDataString('肇庆市'))" $hdr
Check '「广州市→肇庆市」也能命中 3 个班次' ($rCity.data.Count -eq 3) "count=$($rCity.data.Count)"

# ── 5. 先选票、后保存行程（前端 store.persistSelectedTickets 的两步链路） ──
$preTrip = Post-Json "$Backend/ticket/select" @{
    direction = 'outbound'; ticketCode = 'train-gz-zq-c7001'; departDate = '2026-10-20'
} $hdr
Check '行程未保存时选定只回结果、不落库' (
    $preTrip.code -eq 1 -and $null -eq $preTrip.data.tripId -and $preTrip.data.purchaseUrl) "msg=$($preTrip.msg)"
Check '未保存时也带上了方向' ($preTrip.data.direction -eq 'outbound')

# ── 6. 选定落库与读回（往返两条） ──
$trip = Post-Json "$Backend/trip/save" @{
    title = '订票链路自测行程'; fromCity = '广州'; toCity = '肇庆'; days = 3
    startDate = '2026-10-20'; endDate = '2026-10-22'
    dayPlans = @(@{ day = 1; title = '第1天'; schedules = @() })
} $hdr
$tripId = $trip.data.id
Check '行程创建成功' ($null -ne $tripId) "msg=$($trip.msg)"

$selOut = Post-Json "$Backend/ticket/select" @{ tripId = $tripId; direction = 'outbound'; ticketCode = 'train-gz-zq-c7001' } $hdr
Check '选定去程返回车次' ($selOut.code -eq 1 -and $selOut.data.ticketNo -eq 'C7001') "msg=$($selOut.msg)"
Check '去程日期取行程出发日' ($selOut.data.departDate -eq '2026-10-20') "date=$($selOut.data.departDate)"
Check '去程深链带 12306 站点' ($selOut.data.purchaseUrl -match [uri]::EscapeDataString('广州南'))

$selBack = Post-Json "$Backend/ticket/select" @{ tripId = $tripId; direction = 'return'; ticketCode = 'train-zq-gz-c7028' } $hdr
Check '选定返程返回车次' ($selBack.code -eq 1 -and $selBack.data.ticketNo -eq 'C7028') "msg=$($selBack.msg)"
Check '返程日期取行程结束日' ($selBack.data.departDate -eq '2026-10-22') "date=$($selBack.data.departDate)"

$got = Get-Json "$Backend/ticket/trip/$tripId" $hdr
Check '读回两条票务（往返）' ($got.code -eq 1 -and $got.data.Count -eq 2) "count=$($got.data.Count)"
Check '去程排在前、返程排在后' ($got.data[0].direction -eq 'outbound' -and $got.data[1].direction -eq 'return')

# 再选一次去程：应更新同一条（upsert），不新增
Post-Json "$Backend/ticket/select" @{ tripId = $tripId; direction = 'outbound'; ticketCode = 'train-gz-zq-d1832' } $hdr | Out-Null
$got2 = Get-Json "$Backend/ticket/trip/$tripId" $hdr
Check '去程已更新为后选的那班' ($got2.data[0].ticketNo -eq 'D1832') "no=$($got2.data[0].ticketNo)"
Check '更新去程不影响返程' ($got2.data[1].ticketNo -eq 'C7028') "no=$($got2.data[1].ticketNo)"

if ($MysqlExe -and $MysqlUser -and $MysqlPassword) {
    $cnt = Query-Scalar "select count(*) from trip_ticket where trip_id=$tripId"
    Check '同一行程两个方向共两条记录' ($cnt -eq '2') "count=$cnt"
} else {
    $skip++
    Write-Host "  SKIP  同一行程两个方向共两条记录（未提供 MYSQL_* 环境变量）"
}

# ── 7. 单程：清掉返程只剩去程 ──
Post-Json "$Backend/ticket/clear?tripId=$tripId&direction=return" @{} $hdr | Out-Null
$afterClear = Get-Json "$Backend/ticket/trip/$tripId" $hdr
Check '清返程后只剩去程（单程语义）' ($afterClear.data.Count -eq 1 -and $afterClear.data[0].direction -eq 'outbound') "count=$($afterClear.data.Count)"

if ($MysqlExe -and $MysqlUser -and $MysqlPassword) {
    $cnt2 = Query-Scalar "select count(*) from trip_ticket where trip_id=$tripId and direction='return'"
    Check '返程记录确实从库里删掉了' ($cnt2 -eq '0') "count=$cnt2"
} else {
    $skip++
    Write-Host "  SKIP  返程记录确实从库里删掉了（未提供 MYSQL_* 环境变量）"
}

# ── 8. 自驾（无班次方式）：没有车次号、没有购买链接，但有里程与耗时 ──
$drive = Post-Json "$Backend/ticket/select" @{
    tripId = $tripId; direction = 'outbound'; transportType = 'drive'
    fromCity = '广州'; toCity = '肇庆'; departDate = '2026-10-20'
    durationMin = 75; distanceKm = 120.5; price = 96
} $hdr
Check '自驾（无车次号）也能落库' ($drive.code -eq 1 -and $drive.data.transportType -eq 'drive') "msg=$($drive.msg)"
Check '自驾不生成购买深链' ([string]::IsNullOrWhiteSpace($drive.data.purchaseUrl)) "url=$($drive.data.purchaseUrl)"
Check '自驾带回里程' ([double]$drive.data.distanceKm -eq 120.5) "km=$($drive.data.distanceKm)"
Check '自驾带回耗时' ($drive.data.durationMin -eq 75) "min=$($drive.data.durationMin)"

$driveBack = Get-Json "$Backend/ticket/trip/$tripId" $hdr
Check '自驾覆盖同方向的班次（不是新增一条）' (
    $driveBack.data.Count -eq 1 -and $driveBack.data[0].transportType -eq 'drive') "count=$($driveBack.data.Count)"
Check '读回也带里程' ([double]$driveBack.data[0].distanceKm -eq 120.5) "km=$($driveBack.data[0].distanceKm)"

if ($MysqlExe -and $MysqlUser -and $MysqlPassword) {
    $km = Query-Scalar "select distance_km from trip_ticket where trip_id=$tripId and direction='outbound'"
    Check '里程落到了 trip_ticket.distance_km' ($km -eq '120.5') "km=$km"
} else {
    $skip++
    Write-Host "  SKIP  里程落到了 trip_ticket.distance_km（未提供 MYSQL_* 环境变量）"
}

# 不是无班次方式时，仍然必须有车次号（回归：别把校验放宽成谁也不管）
$noNo = Post-Json "$Backend/ticket/select" @{ tripId = $tripId; direction = 'return'; transportType = 'train' } $hdr
Check '火车票没车次号仍报「班次不存在」' ($noNo.code -ne 1 -and $noNo.msg -match '班次不存在') "msg=$($noNo.msg)"

# ── 9. 越权与兜底班次 ──
$other = Get-Json "$Backend/ticket/trip/$tripId" $hdr2
Check '别人读不到该行程的票' ($other.code -ne 1) "code=$($other.code) msg=$($other.msg)"

$custom = Post-Json "$Backend/ticket/select" @{
    tripId = $tripId; direction = 'return'; transportType = 'flight'; ticketNo = 'MU9999'
    carrier = '东方航空'; fromCity = '肇庆'; toCity = '广州'
    fromStation = '肇庆东'; toStation = '白云机场T2'
    departDate = '2026-10-22'; departTime = '21:30'; arriveTime = '22:40'
    durationMin = 70; seatClass = '经济舱'; price = 399
} $hdr
Check '候选库没有的班次也能落库（兜底班次）' ($custom.code -eq 1 -and $custom.data.ticketNo -eq 'MU9999') "msg=$($custom.msg)"
Check '兜底班次也生成了购买深链' (-not [string]::IsNullOrWhiteSpace($custom.data.purchaseUrl))
Check '兜底机票走携程机票模板' ($custom.data.purchaseUrl -match 'ctrip')

$bad = Post-Json "$Backend/ticket/select" @{ tripId = $tripId; direction = 'outbound' } $hdr
Check '既无编码又无车次时报「班次不存在」' ($bad.code -ne 1 -and $bad.msg -match '班次不存在') "msg=$($bad.msg)"

$badDir = Post-Json "$Backend/ticket/select" @{ tripId = $tripId; direction = '乱七八糟'; ticketCode = 'train-gz-zq-c7001' } $hdr
Check '方向写错时按去程处理（不因脏参数报错）' ($badDir.code -eq 1 -and $badDir.data.direction -eq 'outbound') "dir=$($badDir.data.direction)"

# ── 10. 清理探针数据 ──
& curl.exe -sS -m 30 -X DELETE "$Backend/trip/$tripId" -H "Authentication: $token" | Out-Null
if ($MysqlExe -and $MysqlUser -and $MysqlPassword) {
    Query-Scalar "delete from trip_ticket where trip_id=$tripId" | Out-Null
}

Write-Host ""
Write-Host "PASS=$pass FAIL=$fail SKIP=$skip"
if ($fail -gt 0) { exit 1 }
