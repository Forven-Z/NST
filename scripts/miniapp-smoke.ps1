# Patient miniapp smoke test via Gateway :9000
# Usage: .\scripts\miniapp-smoke.ps1

$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
$passed = 0
$failed = 0

function Test-Step($name, $condition, $detail) {
    if ($condition) {
        Write-Host "PASS  $name" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "FAIL  $name - $detail" -ForegroundColor Red
        $script:failed++
    }
}

Write-Host '========================================'
Write-Host " Miniapp smoke  $base"
Write-Host '========================================'

try {
    Invoke-WebRequest -Uri 'http://127.0.0.1:9000' -UseBasicParsing -TimeoutSec 5 | Out-Null
    Test-Step 'Gateway 9000' $true ''
} catch {
    $status = $null
    if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    if ($status -eq 404 -or $status -eq 401 -or $status -eq 403) {
        Test-Step 'Gateway 9000' $true ''
    } else {
        Test-Step 'Gateway 9000' $false 'Run scripts/start-r-min.ps1 first'
        exit 1
    }
}

$code = "miniapp-smoke-$(Get-Random)"
$idCard = '11010119900101' + (Get-Random -Maximum 9999).ToString('0000')
$phone = '138' + (Get-Random -Maximum 99999999).ToString('00000000')
$loginBody = @{
    realName  = '联调测试'
    idCard    = $idCard
    gender    = 1
    birthDate = '1990-01-01'
    phone     = $phone
    address   = '联调地址'
} | ConvertTo-Json -Compress

try {
    $login = Invoke-RestMethod -Uri "$base/patient/auth/login" -Method POST -ContentType 'application/json' -Body $loginBody
    Test-Step 'POST patient/auth/login' ($login.code -eq 200 -and $login.data.accessToken) ($login | ConvertTo-Json -Compress)
    $token = $login.data.accessToken
    $headers = @{ Authorization = "Bearer $token" }
} catch {
    Test-Step 'POST patient/auth/login' $false $_.Exception.Message
    exit 1
}

try {
    $profile = Invoke-RestMethod -Uri "$base/patient/profile" -Headers $headers
    Test-Step 'GET patient/profile' ($profile.code -eq 200 -and $profile.data.realName) ($profile | ConvertTo-Json -Compress)
} catch {
    Test-Step 'GET patient/profile' $false $_.Exception.Message
}

try {
    $depts = Invoke-RestMethod -Uri "$base/patient/departments?deptType=1" -Headers $headers
    Test-Step 'GET patient/departments' ($depts.code -eq 200 -and $depts.data.list.Count -gt 0) 'no dept seed'
} catch {
    Test-Step 'GET patient/departments' $false $_.Exception.Message
}

try {
    $family = Invoke-RestMethod -Uri "$base/patient/family-members" -Headers $headers
    Test-Step 'GET patient/family-members' ($family.code -eq 200 -and $family.data.list.Count -ge 1) ($family | ConvertTo-Json -Compress)
} catch {
    Test-Step 'GET patient/family-members' $false $_.Exception.Message
}

try {
    $today = (Get-Date).ToString('yyyy-MM-dd')
    $sched = Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1&workDate=$today" -Headers $headers
    Test-Step 'GET patient/schedules' ($sched.code -eq 200 -and $sched.data.list.Count -gt 0) 'no schedule seed - re-run docs/sql/seed-dict.sql'
} catch {
    Test-Step 'GET patient/schedules' $false $_.Exception.Message
}

$patientId = $login.data.patientId

try {
    $regs = Invoke-RestMethod -Uri "$base/patient/registers?patientId=$patientId" -Headers $headers
    Test-Step 'GET patient/registers' ($regs.code -eq 200) ($regs | ConvertTo-Json -Compress)
    if ($regs.code -eq 200 -and $regs.data.list.Count -gt 0) {
        $registerId = $regs.data.list[0].registerId
        $orders = Invoke-RestMethod -Uri "$base/patient/registers/$registerId/orders" -Headers $headers
        $ordersOk = $orders.code -eq 200 -and $null -ne $orders.data.list -and $null -ne $orders.data.registerId
        Test-Step 'GET patient/registers/{id}/orders' $ordersOk ($orders | ConvertTo-Json -Compress)
        $visits = Invoke-RestMethod -Uri "$base/patient/visits?patientId=$patientId" -Headers $headers
        Test-Step 'GET patient/visits' ($visits.code -eq 200 -and $null -ne $visits.data.list) ($visits | ConvertTo-Json -Compress)
        $hub = Invoke-RestMethod -Uri "$base/patient/visits/$registerId/hub" -Headers $headers
        $hubOk = $hub.code -eq 200 -and $null -ne $hub.data.registerSummary -and $null -ne $hub.data.orders
        Test-Step 'GET patient/visits/{id}/hub' $hubOk ($hub | ConvertTo-Json -Compress)
    }
} catch {
    Test-Step 'GET patient/registers' $false $_.Exception.Message
}

try {
    $bills = Invoke-RestMethod -Uri "$base/patient/bills?status=0&patientId=$patientId" -Headers $headers
    Test-Step 'GET patient/bills' ($bills.code -eq 200) ($bills | ConvertTo-Json -Compress)
} catch {
    Test-Step 'GET patient/bills' $false $_.Exception.Message
}

try {
    $reports = Invoke-RestMethod -Uri "$base/patient/reports?patientId=$patientId" -Headers $headers
    Test-Step 'GET patient/reports' ($reports.code -eq 200 -and $null -ne $reports.data.pendingCount) ($reports | ConvertTo-Json -Compress)
} catch {
    Test-Step 'GET patient/reports' $false $_.Exception.Message
}

try {
    $records = Invoke-RestMethod -Uri "$base/patient/medical-records?patientId=$patientId" -Headers $headers
    Test-Step 'GET patient/medical-records' ($records.code -eq 200) ($records | ConvertTo-Json -Compress)
} catch {
    Test-Step 'GET patient/medical-records' $false $_.Exception.Message
}

try {
    $payments = Invoke-RestMethod -Uri "$base/patient/payments?patientId=$patientId" -Headers $headers
    Test-Step 'GET patient/payments' ($payments.code -eq 200) ($payments | ConvertTo-Json -Compress)
    if ($payments.code -eq 200 -and $payments.data.list.Count -gt 0) {
        $pid = $payments.data.list[0].paymentId
        $detail = Invoke-RestMethod -Uri "$base/patient/payments/$pid?patientId=$patientId" -Headers $headers
        Test-Step 'GET patient/payments/{id}' ($detail.code -eq 200 -and $detail.data.bills) ($detail | ConvertTo-Json -Compress)
    }
} catch {
    Test-Step 'GET patient/payments' $false $_.Exception.Message
}

Write-Host ''
Write-Host "Passed $passed / Failed $failed"
if ($failed -eq 0) {
    Write-Host 'Backend ready. Open hospital-patient-miniapp with config.local.js USE_MOCK=false' -ForegroundColor Green
}
exit $(if ($failed -eq 0) { 0 } else { 1 })
