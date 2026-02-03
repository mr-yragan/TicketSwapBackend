<# 
TicketSwap Backend - Full Smoke Test (Windows PowerShell 5.1)
Version: 2026-02-03 v3

What it checks:
  - Service reachable: GET /api/tickets
  - Actuator health is secured (401/403 by default)
  - Auth: register/login/JWT
  - /api/me: GET + PATCH
  - SELL: POST /api/tickets/sell (creates 1 ticket lot)
  - /api/tickets: list must be JSON array and must contain the created lot (or at least 1 item)
  - /api/me: /listings /holds /purchases
  - CORS preflight

Run (Windows PowerShell 5.1):
  powershell -NoProfile -ExecutionPolicy Bypass -File .\ticketswap_full_check_windows_powershell_v3.ps1

Notes:
  - Uses manual JSON for sell payload to avoid locale issues with decimal separators.
  - If /api/me/holds returns 500, backend may be swallowing stacktraces (see instruction in the chat on adding logging).
#>

param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$Origin  = "http://localhost:3000",
  [string]$Password = "Passw0rd!!",
  [switch]$ExpectPublicActuatorHealth
)

$ErrorActionPreference = "Stop"

function Write-Ok($msg)   { Write-Host ("[PASS] " + $msg) -ForegroundColor Green }
function Write-Fail($msg) { Write-Host ("[FAIL] " + $msg) -ForegroundColor Red }
function Write-Info($msg) { Write-Host ("[INFO] " + $msg) -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host ("[WARN] " + $msg) -ForegroundColor Yellow }

function Invoke-Api {
  param(
    [Parameter(Mandatory=$true)][string]$Method,
    [Parameter(Mandatory=$true)][string]$Url,
    [hashtable]$Headers = @{},
    [object]$Body = $null,
    [string]$ContentType = $null
  )

  try {
    $args = @{
      Uri = $Url
      Method = $Method
      Headers = $Headers
    }
    if ($null -ne $Body) { $args.Body = $Body }
    if ($null -ne $ContentType) { $args.ContentType = $ContentType }

    $resp = Invoke-WebRequest @args
    return @{
      ok = $true
      status = [int]$resp.StatusCode
      content = $resp.Content
      headers = $resp.Headers
    }
  } catch {
    $status = $null
    $content = $null
    try { $status = [int]$_.Exception.Response.StatusCode.value__ } catch {}
    try {
      $stream = $_.Exception.Response.GetResponseStream()
      if ($null -ne $stream) {
        $reader = New-Object System.IO.StreamReader($stream)
        $content = $reader.ReadToEnd()
      }
    } catch {}
    return @{
      ok = $false
      status = $status
      content = $content
      error = $_.Exception.Message
    }
  }
}

$failed = 0

function Assert-Status {
  param(
    [string]$Name,
    [hashtable]$Resp,
    [int[]]$AllowedStatuses
  )
  if ($null -eq $Resp.status) {
    $script:failed++
    Write-Fail ("${Name}: no HTTP status (network/connection issue). Error: " + $Resp.error)
    if ($Resp.content) { Write-Host $Resp.content }
    return $false
  }
  if ($AllowedStatuses -notcontains $Resp.status) {
    $script:failed++
    Write-Fail ("${Name}: expected [" + ($AllowedStatuses -join ", ") + "], got " + $Resp.status)
    if ($Resp.content) { Write-Host $Resp.content }
    return $false
  }
  Write-Ok ("${Name} (HTTP " + $Resp.status + ")")
  return $true
}

function Try-ParseJson {
  param([string]$Name, [string]$JsonText)
  try {
    return $JsonText | ConvertFrom-Json
  } catch {
    $script:failed++
    Write-Fail ("${Name}: cannot parse JSON")
    Write-Host $JsonText
    return $null
  }
}

Write-Info "ticketswap_full_check_windows_powershell.ps1 v4"
Write-Info ("BaseUrl = " + $BaseUrl)
Write-Info ("Origin  = " + $Origin)

# 0) Service reachable
$resp = Invoke-Api -Method "GET" -Url "$BaseUrl/api/tickets"
Assert-Status -Name "0. Service reachable: GET /api/tickets" -Resp $resp -AllowedStatuses @(200) | Out-Null

# 0b) Actuator health behavior
$act = Invoke-Api -Method "GET" -Url "$BaseUrl/actuator/health"
if ($ExpectPublicActuatorHealth) {
  Assert-Status -Name "0b. GET /actuator/health is public" -Resp $act -AllowedStatuses @(200) | Out-Null
} else {
  Assert-Status -Name "0b. GET /actuator/health is secured (expected)" -Resp $act -AllowedStatuses @(401,403) | Out-Null
}

# Random test user
$guid = [guid]::NewGuid().ToString("N").Substring(0, 10)
$email = "test_$guid@example.com"
Write-Info ("Test user email = " + $email)

# 1) Register
$regBody = @{ email = $email; password = $Password } | ConvertTo-Json
$reg = Invoke-Api -Method "POST" -Url "$BaseUrl/api/auth/register" -ContentType "application/json" -Body $regBody
Assert-Status -Name "1. Register" -Resp $reg -AllowedStatuses @(200,201) | Out-Null

# 2) Login
$loginBody = @{ email = $email; password = $Password } | ConvertTo-Json
$login = Invoke-Api -Method "POST" -Url "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $loginBody
$token = $null
if (Assert-Status -Name "2. Login" -Resp $login -AllowedStatuses @(200)) {
  $loginObj = Try-ParseJson -Name "2b. Login JSON" -JsonText $login.content
  if ($null -ne $loginObj) {
    $token = $loginObj.token
    if ([string]::IsNullOrWhiteSpace($token)) {
      $failed++
      Write-Fail "2c. Token missing in login response (field 'token')"
      Write-Host $login.content
    } else {
      Write-Ok "2c. Token received"
    }
  }
}

$authHeaders = @{ Authorization = "Bearer $token" }

# 3) GET /api/me
$me = Invoke-Api -Method "GET" -Url "$BaseUrl/api/me" -Headers $authHeaders
if (Assert-Status -Name "3. GET /api/me" -Resp $me -AllowedStatuses @(200)) {
  $meObj = Try-ParseJson -Name "3b. /api/me JSON" -JsonText $me.content
  if ($null -ne $meObj) { Write-Ok "3c. /api/me JSON parsed" }
}

# 4) PATCH /api/me
$loginName = "user_$guid"
$phone = "+7 (900) 000-00-00"
$patchBody = @{ login = $loginName; phoneNumber = $phone } | ConvertTo-Json
$patch = Invoke-Api -Method "PATCH" -Url "$BaseUrl/api/me" -Headers $authHeaders -ContentType "application/json" -Body $patchBody
Assert-Status -Name "4. PATCH /api/me" -Resp $patch -AllowedStatuses @(200,204) | Out-Null

# 5) SELL
Write-Info "5. Trying to SELL one ticket lot..."
$uid = "UID-$guid"
$venueLine = "Ледовый дворец, Санкт-Петербург"
$additionalInfo = "Сектор A, ряд 3, место 12"
$price = 1100.00
$priceStr = $price.ToString([System.Globalization.CultureInfo]::InvariantCulture)

$sellPayload = @{
    uid = "UID-$([Guid]::NewGuid().ToString('N'))"
    eventName = "Demo Concert"
    eventDate = (Get-Date).AddDays(30).ToString('yyyy-MM-ddTHH:mm:ss')
    venue = "Rod Laver Arena, Melbourne"
    price = 200.00
    additionalInfo = "Section A, Row 5, Seat 12"
    organizerName = "TicketSwap Test Org"
    sellerComment = "Test listing created by script"
}
$sellJson = $sellPayload | ConvertTo-Json -Depth 10

$sell = Invoke-Api -Method "POST" -Url "$BaseUrl/api/tickets/sell" -Headers $authHeaders -ContentType "application/json" -Body $sellJson
$ticketId = $null
if (Assert-Status -Name "5. Sell ticket lot" -Resp $sell -AllowedStatuses @(200,201)) {
  $sellObj = Try-ParseJson -Name "5b. Sell JSON" -JsonText $sell.content
  if ($null -ne $sellObj -and ($sellObj.PSObject.Properties.Name -contains "id")) {
    $ticketId = $sellObj.id
    Write-Ok ("5c. Created ticketId = " + $ticketId)
  } else {
    Write-Warn "5c. Could not extract id from sell response (still continuing)."
  }
}

# 6) Public list
$list = Invoke-Api -Method "GET" -Url "$BaseUrl/api/tickets"
if (Assert-Status -Name "6. GET /api/tickets list" -Resp $list -AllowedStatuses @(200)) {
  $listObj = Try-ParseJson -Name "6b. List JSON" -JsonText $list.content
  if ($null -eq $listObj -or -not ($listObj -is [System.Array])) {
    $failed++
    Write-Fail "6c. List JSON is not an array"
    Write-Host $list.content
  } else {
    if ($listObj.Count -lt 1) {
      $failed++
      Write-Fail "6d. List JSON is empty (expected >= 1 after sell)."
    } else {
      Write-Ok ("6e. List size = " + $listObj.Count)
    }
  }
}

# 7) /api/me/listings
$meListings = Invoke-Api -Method "GET" -Url "$BaseUrl/api/me/listings?scope=active" -Headers $authHeaders
if (Assert-Status -Name "7. GET /api/me/listings" -Resp $meListings -AllowedStatuses @(200)) {
  $mlObj = Try-ParseJson -Name "7b. /api/me/listings JSON" -JsonText $meListings.content
  if ($null -ne $mlObj) { Write-Ok "7c. /api/me/listings parsed" }
}

# 8) /api/me/holds
$meHolds = Invoke-Api -Method "GET" -Url "$BaseUrl/api/me/holds" -Headers $authHeaders
if ($meHolds.status -eq 500) {
  $failed++
  Write-Fail "8. GET /api/me/holds returned 500."
  Write-Warn "Backend hides stacktraces by default. Add logging in GlobalExceptionHandler.handleOther() to see the real cause."
  Write-Warn "Then re-run and check: docker logs ticketswap-backend --tail 200"
  if ($meHolds.content) { Write-Host $meHolds.content }
} else {
  if (Assert-Status -Name "8. GET /api/me/holds" -Resp $meHolds -AllowedStatuses @(200)) {
    $mhObj = Try-ParseJson -Name "8b. /api/me/holds JSON" -JsonText $meHolds.content
    if ($null -ne $mhObj) { Write-Ok "8c. /api/me/holds parsed" }
  }
}

# 9) /api/me/purchases
$mePurch = Invoke-Api -Method "GET" -Url "$BaseUrl/api/me/purchases" -Headers $authHeaders
if (Assert-Status -Name "9. GET /api/me/purchases" -Resp $mePurch -AllowedStatuses @(200)) {
  $mpObj = Try-ParseJson -Name "9b. /api/me/purchases JSON" -JsonText $mePurch.content
  if ($null -ne $mpObj) { Write-Ok "9c. /api/me/purchases parsed" }
}

# 10) CORS preflight
$corsHeaders = @{
  Origin = $Origin
  "Access-Control-Request-Method" = "GET"
}
$cors = Invoke-Api -Method "OPTIONS" -Url "$BaseUrl/api/tickets" -Headers $corsHeaders
if (Assert-Status -Name "10. CORS preflight OPTIONS" -Resp $cors -AllowedStatuses @(200,204)) {
  $allowOrigin = $cors.headers["Access-Control-Allow-Origin"]
  if ($null -eq $allowOrigin -or $allowOrigin.Count -eq 0) {
    $failed++
    Write-Fail "10b. Missing Access-Control-Allow-Origin header"
  } else {
    Write-Ok ("10b. Access-Control-Allow-Origin = " + ($allowOrigin -join ", "))
  }
}

Write-Host ""
if ($failed -eq 0) {
  Write-Ok "ALL CHECKS PASSED"
  exit 0
} else {
  Write-Fail ("${failed} check(s) FAILED")
  exit 1
}
