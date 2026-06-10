# R-registrar 窗口挂号+收费验收（v2 两步式）— 全部经 Gateway :9000
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
$passed = 0
$failed = 0
$randPhone = "138{0:D8}" -f (Get-Random -Maximum 99999999)

function Test-Step($name, $condition, $detail) {
    if ($condition) {
        Write-Host "[PASS] $name" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "[FAIL] $name - $detail" -ForegroundColor Red
        $script:failed++
    }
}

Write-Host "========================================"
Write-Host " R-registrar v2 (register + charge)  Gateway: $base"
Write-Host " test phone: $randPhone"
Write-Host "========================================"

# R1 — registrar login
$r1 = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"registrar01","password":"123456"}'
Test-Step 'R1 registrar login' ($r1.code -eq 200 -and $r1.data.accessToken -and ($r1.data.roles -contains 'REGISTRAR')) ($r1 | ConvertTo-Json -Compress)
$registrarHeaders = @{ Authorization = "Bearer $($r1.data.accessToken)" }

# Get a schedule
$sched = $null
try {
    $code = "reg-accept-$(Get-Random)"
    $wx = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body (@{ code = $code; nickName = 'sched-helper' } | ConvertTo-Json)
    $patientHeaders = @{ Authorization = "Bearer $($wx.data.accessToken)" }
    $schedResp = Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1" -Headers $patientHeaders
    if ($schedResp.data.list.Count -gt 0) { $sched = $schedResp.data.list[0] }
} catch {
    Write-Host "WARN: could not fetch schedules - $_" -ForegroundColor Yellow
}

if (-not $sched) {
    @('R2 window register pending', 'R3 list pending bills', 'R4 window charge CASH', 'R5 needRecordBook', 'R6 no phone/idCard', 'R7 doctor queue', 'R8 pending cancel') | ForEach-Object {
        Test-Step $_ $false 'no schedule available in seed'
    }
    Write-Host "========================================"
    Write-Host " Passed: $passed  Failed: $failed"
    Write-Host "========================================"
    exit 1
}

# R2 — window register (pending payment)
$r2Body = @{
    patientName   = '窗口测试'
    phone         = $randPhone
    gender        = 1
    schedulingId  = $sched.schedulingId
    deptId        = $sched.deptId
    employeeId    = $sched.employeeId
    registLevelId = $sched.registLevelId
} | ConvertTo-Json
$r2 = Invoke-RestMethod -Uri "$base/registrar/registers" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body $r2Body
Test-Step 'R2 window register pending' ($r2.code -eq 200 -and $r2.data.visitState -eq 0 -and -not $r2.data.paymentId) ($r2 | ConvertTo-Json -Compress)
$registerId = $r2.data.registerId
$mrn = $r2.data.medicalRecordNo
$billIds = @($r2.data.billIds)

# R3 — list pending bills by MRN
$r3 = Invoke-RestMethod -Uri "$base/registrar/patients/$mrn/bills?status=0" -Headers $registrarHeaders
$hasRegisterBill = @($r3.data.list | Where-Object { $_.bizType -eq 'REGISTER' }).Count -gt 0
Test-Step 'R3 list pending bills' ($r3.code -eq 200 -and $hasRegisterBill) ($r3 | ConvertTo-Json -Compress)

# R4 — window charge CASH
$r4 = Invoke-RestMethod -Uri "$base/registrar/charges" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body (@{ billIds = $billIds; payChannel = 'CASH' } | ConvertTo-Json)
Test-Step 'R4 window charge CASH' ($r4.code -eq 200 -and $r4.data.paymentId) ($r4 | ConvertTo-Json -Compress)

# R5 — needRecordBook + batch charge
$bookPhone = "136{0:D8}" -f (Get-Random -Maximum 99999999)
$r5Body = @{
    patientName    = '病历本测试'
    phone          = $bookPhone
    schedulingId   = $sched.schedulingId
    deptId         = $sched.deptId
    employeeId     = $sched.employeeId
    registLevelId  = $sched.registLevelId
    needRecordBook = $true
} | ConvertTo-Json
$r5 = Invoke-RestMethod -Uri "$base/registrar/registers" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body $r5Body
$expectedBookAmount = [decimal]$sched.registFee + 1
Test-Step 'R5 needRecordBook register' ($r5.code -eq 200 -and $r5.data.billIds.Count -eq 2 -and [decimal]$r5.data.amount -eq $expectedBookAmount) "amount=$($r5.data.amount) expected=$expectedBookAmount"
if ($r5.code -eq 200) {
    $r5Charge = Invoke-RestMethod -Uri "$base/registrar/charges" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body (@{ billIds = @($r5.data.billIds); payChannel = 'WECHAT' } | ConvertTo-Json)
    Test-Step 'R5 needRecordBook charge' ($r5Charge.code -eq 200 -and [decimal]$r5Charge.data.paidAmount -eq $expectedBookAmount) ($r5Charge | ConvertTo-Json -Compress)
}

# R6 — no phone/idCard
$r6Fail = $false
try {
    $r6Body = @{
        patientName   = '无联系方式'
        schedulingId  = $sched.schedulingId
        deptId        = $sched.deptId
        employeeId    = $sched.employeeId
        registLevelId = $sched.registLevelId
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$base/registrar/registers" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body $r6Body
} catch {
    $r6Fail = $true
}
Test-Step 'R6 no phone/idCard' $r6Fail 'expected 400'

# R7 — doctor queue contains R2 register (after charge)
$doctorLogin = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
$doctorHeaders = @{ Authorization = "Bearer $($doctorLogin.data.accessToken)" }
$r7 = Invoke-RestMethod -Uri "$base/doctor/queues?visitState=1" -Headers $doctorHeaders
$inQueue = @($r7.data.list | Where-Object { $_.registerId -eq $registerId }).Count -gt 0
Test-Step 'R7 doctor queue' ($r7.code -eq 200 -and $inQueue) "registerId $registerId not in queue"

# R8 — pending cancel
$cancelPhone = "135{0:D8}" -f (Get-Random -Maximum 99999999)
$r8Reg = Invoke-RestMethod -Uri "$base/registrar/registers" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body (@{
    patientName   = '待支付退号'
    phone         = $cancelPhone
    schedulingId  = $sched.schedulingId
    deptId        = $sched.deptId
    employeeId    = $sched.employeeId
    registLevelId = $sched.registLevelId
} | ConvertTo-Json)
$r8Cancel = Invoke-RestMethod -Uri "$base/registrar/registers/$($r8Reg.data.registerId)/cancel" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body '{"reason":"测试退号"}'
Test-Step 'R8 pending cancel' ($r8Cancel.code -eq 200 -and $r8Cancel.data.visitState -eq 4) ($r8Cancel | ConvertTo-Json -Compress)

Write-Host "========================================"
Write-Host " Passed: $passed  Failed: $failed"
Write-Host "========================================"
if ($failed -gt 0) { exit 1 }
