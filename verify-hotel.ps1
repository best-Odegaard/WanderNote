# 酒店链路自测（本地或任意环境）
#
# 覆盖：候选查询 / 档次与位置筛选 / 深链生成 / 选定落库 / 读回 / 行程回写 / 越权 / 非法日期降级
#
# 用法：
#   .\verify-hotel.ps1                                   # 后端默认 http://127.0.0.1:8080
#   .\verify-hotel.ps1 -Backend http://1.2.3.4:8080      # 打远端
#
# MySQL 校验（重复选定只保留一条）需要库连接信息，缺省从环境变量取，取不到就跳过该项：
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
    $bodyFile = Join-Path $env:TEMP "hotel_tb_$tag.json"
    $outFile = Join-Path $env:TEMP "hotel_to_$tag.txt"
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
    $outFile = Join-Path $env:TEMP "hotel_go_$tag.txt"
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

Write-Host "酒店链路自测 → $Backend"

# ── 0. 探针账号（两个：一个用来验证越权） ──
$u1 = "hotelprobe$((Get-Random -Maximum 99999))"
Post-Json "$Backend/user/register" @{ username = $u1; password = 'probe123456'; nickname = 'probe' } | Out-Null
$login = Post-Json "$Backend/user/login" @{ username = $u1; password = 'probe123456' }
$token = $login.data.token
Check '登录拿到 token' ($null -ne $token -and $token.Length -gt 10) "resp=$($login | ConvertTo-Json -Compress)"
$hdr = @{ Authentication = $token }

$u2 = "hotelprobe$((Get-Random -Maximum 99999))"
Post-Json "$Backend/user/register" @{ username = $u2; password = 'probe123456'; nickname = 'probe' } | Out-Null
$hdr2 = @{ Authentication = (Post-Json "$Backend/user/login" @{ username = $u2; password = 'probe123456' }).data.token }

# ── 1. 候选查询与深链 ──
$zq = [uri]::EscapeDataString('肇庆')
$r = Get-Json "$Backend/hotel/search?city=$zq&checkin=2026-10-20&checkout=2026-10-21" $hdr
Check '肇庆返回 4 家候选' ($r.code -eq 1 -and $r.data.Count -eq 4) "count=$($r.data.Count) msg=$($r.msg)"

if ($r.data.Count -gt 0) {
    $first = $r.data[0]
    Check '每条候选都有携程深链' (($r.data | Where-Object { -not $_.ctripUrl }).Count -eq 0)
    Check '深链带城市与日期' ($first.ctripUrl -match 'cityName=%E8%82%87%E5%BA%86' -and $first.ctripUrl -match 'checkin=2026-10-20')
    Check '深链关键词含酒店名' ($first.ctripUrl -match [uri]::EscapeDataString($first.name))
    Check '候选带坐标（路线闭环要用）' ($first.lng -ne 0 -and $first.lat -ne 0)
    Check '价格是参考价语义（接口无库存字段）' ($first.PSObject.Properties.Name -notcontains 'stock')

    # 前端 utils/hotels.ts 的 toHotelOption 会逐字段读这些值，
    # 少一个就表现为「列表空白 / 价格 0 / 点不动携程」——在接口层先卡住
    $need = @('id', 'name', 'city', 'level', 'rating', 'price', 'address', 'nearbyLandmark',
        'distanceKm', 'tags', 'cover', 'lng', 'lat', 'desc', 'ctripUrl')
    $missing = @()
    foreach ($f in $need) {
        if ($first.PSObject.Properties.Name -notcontains $f) { $missing += $f }
    }
    Check '响应字段齐全（前端逐字段读取）' ($missing.Count -eq 0) "missing=$($missing -join ',')"

    $allowed = @('经济型', '舒适型', '高档型', '特色民宿')
    $levels = @($r.data | ForEach-Object { $_.level } | Select-Object -Unique)
    Check '档次取值都在前端枚举内' (@($levels | Where-Object { $allowed -notcontains $_ }).Count -eq 0) "levels=$($levels -join ',')"
    Check 'tags 是数组（页面 v-for 读它）' ($first.tags -is [array]) "type=$($first.tags.GetType().Name)"
    Check '深链是绝对 https 地址' ($first.ctripUrl -match '^https://')
    Check '附近地标非空（深链关键词要用）' (-not [string]::IsNullOrWhiteSpace($first.nearbyLandmark))
}

# ── 1.5 先选酒店、后保存行程（前端 store.persistSelectedHotel 的两步链路） ──
$preTrip = Post-Json "$Backend/hotel/select" @{
    hotelCode = 'zq-jj-1'; city = '肇庆'; checkin = '2026-10-20'; checkout = '2026-10-21'
} $hdr
Check '行程未保存时选定只回结果、不落库' ($preTrip.code -eq 1 -and $null -eq $preTrip.data.tripId -and $preTrip.data.ctripUrl) "msg=$($preTrip.msg)"

# ── 2. 筛选与放宽 ──
$lv = [uri]::EscapeDataString('高档型')
$r2 = Get-Json "$Backend/hotel/search?city=$zq&style=$lv" $hdr
Check '档次筛选：肇庆高档型 1 家' ($r2.data.Count -eq 1 -and $r2.data[0].level -eq '高档型') "count=$($r2.data.Count)"

$gl = [uri]::EscapeDataString('桂林')
$r3 = Get-Json "$Backend/hotel/search?city=$gl&area=$([uri]::EscapeDataString('遇龙河'))" $hdr
Check '位置筛选：桂林遇龙河 2 家' ($r3.data.Count -eq 2) "count=$($r3.data.Count)"

$r4 = Get-Json "$Backend/hotel/search?city=$gl&area=$([uri]::EscapeDataString('不存在的地标'))" $hdr
Check '位置命不中时放宽到全城（不返回空）' ($r4.data.Count -eq 4) "count=$($r4.data.Count)"

$r5 = Get-Json "$Backend/hotel/search?city=$([uri]::EscapeDataString('不存在的城市'))" $hdr
Check '未收录城市返回空数组（前端走本地兜底）' ($r5.code -eq 1 -and $r5.data.Count -eq 0)

$r6 = Get-Json "$Backend/hotel/search?city=$zq&checkin=2026-10-21&checkout=2026-10-20" $hdr
Check '离店早于入住时深链不带日期' (($r6.data | Where-Object { $_.ctripUrl -match 'checkin=2026-10-21' }).Count -eq 0)

# ── 3. 选定落库与读回 ──
$trip = Post-Json "$Backend/trip/save" @{
    title = '酒店链路自测行程'; fromCity = '广州'; toCity = '肇庆'; days = 2
    startDate = '2026-10-20'; endDate = '2026-10-21'
    dayPlans = @(@{ day = 1; title = '第1天'; schedules = @() })
} $hdr
$tripId = $trip.data.id
Check '行程创建成功' ($null -ne $tripId) "msg=$($trip.msg)"

$sel = Post-Json "$Backend/hotel/select" @{ tripId = $tripId; hotelCode = 'zq-sf-1' } $hdr
Check '选定酒店返回名称' ($sel.code -eq 1 -and $sel.data.name -eq '星湖景畔酒店') "msg=$($sel.msg)"
Check '选定后深链带酒店名+地标' ($sel.data.ctripUrl -match [uri]::EscapeDataString('星湖景畔酒店') -and $sel.data.ctripUrl -match [uri]::EscapeDataString('星湖'))

$got = Get-Json "$Backend/hotel/trip/$tripId" $hdr
Check '读回已保存住宿' ($null -ne $got.data -and $got.data.name -eq '星湖景畔酒店')
Check '读回带坐标' ($null -ne $got.data -and $got.data.lng -ne 0 -and $got.data.lat -ne 0)
# 坐标必须与候选库一致：路线页用它做闭环起点/终点，偏了就画出错误路线
Check '坐标与候选库一致（闭环不画偏）' ($null -ne $got.data -and [math]::Abs([double]$got.data.lng - 112.4688) -lt 0.0001 -and [math]::Abs([double]$got.data.lat - 23.0588) -lt 0.0001) "lng=$($got.data.lng) lat=$($got.data.lat)"

$tripAfter = Get-Json "$Backend/trip/$tripId" $hdr
Check '回写 trip_plan.hotel 名称' ($tripAfter.data.hotel -eq '星湖景畔酒店') "hotel=$($tripAfter.data.hotel)"

# 再选一次：应更新同一条（upsert），不新增
Post-Json "$Backend/hotel/select" @{ tripId = $tripId; hotelCode = 'zq-gd-1' } $hdr | Out-Null
$got2 = Get-Json "$Backend/hotel/trip/$tripId" $hdr
Check '住宿已更新为后选的那家' ($got2.data.name -eq '七星岩温德姆酒店') "name=$($got2.data.name)"

if ($MysqlExe -and $MysqlUser -and $MysqlPassword) {
    $cnt = Query-Scalar "select count(*) from trip_hotel where trip_id=$tripId"
    Check '重复选定只保留一条住宿记录' ($cnt -eq '1') "count=$cnt"
} else {
    $skip++
    Write-Host "  SKIP  重复选定只保留一条住宿记录（未提供 MYSQL_* 环境变量）"
}

# ── 4. 越权与兜底 ──
$other = Get-Json "$Backend/hotel/trip/$tripId" $hdr2
Check '别人读不到该行程住宿' ($other.code -ne 1) "code=$($other.code) msg=$($other.msg)"

$custom = Post-Json "$Backend/hotel/select" @{
    tripId = $tripId; name = '某县城自选民宿'; address = '某县城老街 1 号'; city = '肇庆'; level = '特色民宿'
} $hdr
Check '候选库没有的酒店也能落库（兜底候选）' ($custom.code -eq 1 -and $custom.data.name -eq '某县城自选民宿') "msg=$($custom.msg)"

$bad = Post-Json "$Backend/hotel/select" @{ tripId = $tripId } $hdr
Check '既无编码又无名称时报「酒店不存在」' ($bad.code -ne 1 -and $bad.msg -match '酒店不存在') "msg=$($bad.msg)"

# ── 5. 清理探针数据 ──
& curl.exe -sS -m 30 -X DELETE "$Backend/trip/$tripId" -H "Authentication: $token" | Out-Null
if ($MysqlExe -and $MysqlUser -and $MysqlPassword) {
    Query-Scalar "delete from trip_hotel where trip_id=$tripId" | Out-Null
}

Write-Host ""
Write-Host "PASS=$pass FAIL=$fail SKIP=$skip"
if ($fail -gt 0) { exit 1 }
