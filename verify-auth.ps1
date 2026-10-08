# 鉴权语义回归：匿名可读内容 + 「我的」/写接口必须登录 + 登录用户回归
#
# 为什么单独一个脚本：现在的登录语义是「按 GET + 内容路径放行匿名，写接口与 /journal/my/** 照旧拦截」
# （见 backend/gkv-server/src/main/java/com/gkv/config/JwtTokenUserInterceptor.java）。
# 这类规则一旦被顺手改宽，不会报错，只会静默泄漏或静默把用户踢去登录页，所以用可执行断言钉住。
#
# 用法： .\verify-auth.ps1  [-Backend http://127.0.0.1:8080]
# 依赖： curl.exe；可选 MYSQL_EXE/MYSQL_USER/MYSQL_PASSWORD/MYSQL_DB（用于清理 p0probe* 探针账号）
$ErrorActionPreference = 'Stop'
$Backend = 'http://127.0.0.1:8080'
$pass = 0; $fail = 0

function Status($method, $url, $headers = @{}) {
  $tag = [guid]::NewGuid().ToString('N')
  $outFile = Join-Path $env:TEMP "p0_$tag.txt"
  $args = @('-sS', '-m', '30', '-X', $method, $url, '-o', $outFile, '-w', '%{http_code}')
  foreach ($k in $headers.Keys) { $args += @('-H', "${k}: $($headers[$k])") }
  try {
    $code = & curl.exe @args 2>$null
    $body = if (Test-Path $outFile) { [System.IO.File]::ReadAllText($outFile, [System.Text.Encoding]::UTF8) } else { '' }
    return @{ code = "$code".Trim(); body = $body }
  } finally { Remove-Item $outFile -ErrorAction SilentlyContinue }
}

function PostJson($url, $obj, $headers = @{}) {
  $json = $obj | ConvertTo-Json -Depth 6 -Compress
  $tag = [guid]::NewGuid().ToString('N')
  $bodyFile = Join-Path $env:TEMP "p0b_$tag.json"
  $outFile = Join-Path $env:TEMP "p0o_$tag.txt"
  [System.IO.File]::WriteAllText($bodyFile, $json, (New-Object System.Text.UTF8Encoding($false)))
  $args = @('-sS', '-m', '30', '-X', 'POST', $url, '-H', 'Content-Type: application/json; charset=utf-8',
    '--data-binary', "@$bodyFile", '-o', $outFile, '-w', '%{http_code}')
  foreach ($k in $headers.Keys) { $args += @('-H', "${k}: $($headers[$k])") }
  try {
    $code = & curl.exe @args 2>$null
    $body = if (Test-Path $outFile) { [System.IO.File]::ReadAllText($outFile, [System.Text.Encoding]::UTF8) } else { '' }
    return @{ code = "$code".Trim(); body = $body }
  } finally { Remove-Item $bodyFile, $outFile -ErrorAction SilentlyContinue }
}

function Check($name, $cond, $detail = '') {
  if ($cond) { $script:pass++; Write-Host "  PASS  $name" }
  else { $script:fail++; Write-Host "  FAIL  $name  $detail" }
}

Write-Host '== 1) 匿名 GET 内容读接口应放行（200 + code=1） =='
foreach ($p in @('/featured/list', '/journal/list', '/journal/hot', '/scenic/list', '/scenic/hot?limit=3', '/activity/list', '/activity/hot')) {
  $r = Status 'GET' "$Backend$p"
  $ok = $r.code -eq '200' -and ($r.body -match '"code":1')
  Check "匿名 GET $p" $ok "http=$($r.code) body=$($r.body.Substring(0,[Math]::Min(120,$r.body.Length)))"
}

Write-Host '== 2) 匿名访问「我的」与写接口仍必须 401 =='
$r = Status 'GET' "$Backend/journal/my/list"
Check '匿名 GET /journal/my/list 仍 401' ($r.code -eq '401') "http=$($r.code)"
$r = Status 'GET' "$Backend/journal/my/collects"
Check '匿名 GET /journal/my/collects 仍 401' ($r.code -eq '401') "http=$($r.code)"
$r = Status 'GET' "$Backend/trip/list"
Check '匿名 GET /trip/list 仍 401' ($r.code -eq '401') "http=$($r.code)"
$r = Status 'GET' "$Backend/user/info"
Check '匿名 GET /user/info 仍 401' ($r.code -eq '401') "http=$($r.code)"
$r = PostJson "$Backend/journal/publish" @{ title = 'x'; content = 'y' }
Check '匿名 POST /journal/publish 仍 401' ($r.code -eq '401') "http=$($r.code)"
$r = PostJson "$Backend/journal/comment" @{ journalId = 1; content = 'x' }
Check '匿名 POST /journal/comment 仍 401' ($r.code -eq '401') "http=$($r.code)"
$r = PostJson "$Backend/activity/1/enroll" @{}
Check '匿名 POST /activity/1/enroll 仍 401' ($r.code -eq '401') "http=$($r.code)"
$r = PostJson "$Backend/activity/1/collect" @{}
Check '匿名 POST /activity/1/collect 仍 401' ($r.code -eq '401') "http=$($r.code)"

Write-Host '== 3) 带过期/伪造令牌读内容：不应把用户弹去登录（放行），写接口仍 401 =='
$bad = @{ Authentication = 'this.is.a.bogus.token' }
$r = Status 'GET' "$Backend/featured/list" $bad
Check '伪造令牌 GET /featured/list 放行' ($r.code -eq '200') "http=$($r.code)"
$r = PostJson "$Backend/journal/publish" @{ title = 'x'; content = 'y' } $bad
Check '伪造令牌 POST /journal/publish 仍 401' ($r.code -eq '401') "http=$($r.code)"

Write-Host '== 4) 登录用户回归：令牌有效时读接口与「我的」都正常 =='
$u = "p0probe$((Get-Random -Maximum 99999))"
PostJson "$Backend/user/register" @{ username = $u; password = 'probe123456'; nickname = 'p0' } | Out-Null
$login = PostJson "$Backend/user/login" @{ username = $u; password = 'probe123456' }
$token = ($login.body | ConvertFrom-Json).data.token
Check '登录拿到 token' ($null -ne $token -and $token.Length -gt 10)
$hdr = @{ Authentication = $token }
$r = Status 'GET' "$Backend/journal/my/list" $hdr
Check '登录后 GET /journal/my/list 200' ($r.code -eq '200' -and $r.body -match '"code":1') "http=$($r.code) body=$($r.body.Substring(0,[Math]::Min(120,$r.body.Length)))"
$r = Status 'GET' "$Backend/featured/list" $hdr
Check '登录后 GET /featured/list 200' ($r.code -eq '200') "http=$($r.code)"
$r = Status 'GET' "$Backend/trip/list" $hdr
Check '登录后 GET /trip/list 200' ($r.code -eq '200' -and $r.body -match '"code":1') "http=$($r.code)"

# 清理探针账号
$mysql = $env:MYSQL_EXE
if ($mysql -and $env:MYSQL_PASSWORD) {
  cmd /c "`"$mysql`" -u$($env:MYSQL_USER) -p`"$($env:MYSQL_PASSWORD)`" -N -B $($env:MYSQL_DB) -e `"delete from sys_user where username like 'p0probe%'`" 2>nul" | Out-Null
}

Write-Host ""
Write-Host "PASS=$pass FAIL=$fail"
if ($fail -gt 0) { exit 1 }
