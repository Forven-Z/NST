# R-min 联调验收 — 全部经 Gateway :9000
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
$code = "dev-accept-$(Get-Random)"
$passed = 0
$failed = 0

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
Write-Host " R-min Acceptance  Gateway: $base"
Write-Host " wechat code: $code"
Write-Host "========================================"

# A1
$a1 = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
Test-Step 'A1 staff login' ($a1.code -eq 200 -and $a1.data.accessToken -and ($a1.data.roles -contains 'OUTPATIENT_DOCTOR')) ($a1 | ConvertTo-Json -Compress)
$doctorToken = $a1.data.accessToken

# A2
try {
    $a2 = Invoke-RestMethod -Uri "$base/auth/me" -Headers @{ Authorization = "Bearer $doctorToken" }
    Test-Step 'A2 auth/me' ($a2.code -eq 200 -and $a2.data.realName) ($a2 | ConvertTo-Json -Compress)
} catch {
    Test-Step 'A2 auth/me' $false $_.Exception.Message
}

# B1
$b1Body = @{ code = $code; nickName = 'accept-patient' } | ConvertTo-Json -Compress
$b1 = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body $b1Body
Test-Step 'B1 wechat login' ($b1.code -eq 200 -and $b1.data.accessToken -and $b1.data.patientId) ($b1 | ConvertTo-Json -Compress)
$patientToken = $b1.data.accessToken
$patientHeaders = @{ Authorization = "Bearer $patientToken" }

# C1
$c1 = Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1" -Headers $patientHeaders
Test-Step 'C1 schedules' ($c1.code -eq 200 -and $c1.data.list.Count -gt 0) 'no schedules in seed'
$sched = $c1.data.list[0]

# C2
$regBody = @{
    schedulingId   = $sched.schedulingId
    deptId         = $sched.deptId
    employeeId     = $sched.employeeId
    registLevelId  = $sched.registLevelId
    settleCategoryId = 1
} | ConvertTo-Json
$c2 = Invoke-RestMethod -Uri "$base/patient/registers" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body $regBody
Test-Step 'C2 register' ($c2.code -eq 200 -and $c2.data.registerId -and $c2.data.billId) ($c2 | ConvertTo-Json -Compress)
$registerId = $c2.data.registerId
$billId = $c2.data.billId

# C3
$c3 = Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body "{`"billIds`":[$billId]}"
Test-Step 'C3 mock payment' ($c3.code -eq 200 -and $c3.data.paymentId) ($c3 | ConvertTo-Json -Compress)

# D1
$doctorHeaders = @{ Authorization = "Bearer $doctorToken" }
$d1 = Invoke-RestMethod -Uri "$base/doctor/queues?visitState=1" -Headers $doctorHeaders
$inQueue = @($d1.data.list | Where-Object { $_.registerId -eq $registerId }).Count -gt 0
Test-Step 'D1 doctor queue' ($d1.code -eq 200 -and $inQueue) "registerId $registerId not in queue"

# D2
$d2 = Invoke-RestMethod -Uri "$base/doctor/call/$registerId" -Method POST -Headers $doctorHeaders
Test-Step 'D2 call patient' ($d2.code -eq 200 -and $d2.data.visitState -eq 2) ($d2 | ConvertTo-Json -Compress)

# D3 - save medical record (status should become 1, patient still cannot read)
$recBody = @{ readme = 'accept chief'; present = 'accept present'; diagnosis = 'accept dx'; cure = 'accept plan' } | ConvertTo-Json -Compress
$d3 = Invoke-RestMethod -Uri "$base/doctor/medical-records/$registerId" -Method PUT -Headers $doctorHeaders -ContentType 'application/json' -Body $recBody
Test-Step 'D3 save medical record' ($d3.code -eq 200 -and $d3.data.status -eq 1) ($d3 | ConvertTo-Json -Compress)

# D3b - patient cannot read before submit
$d3b = Invoke-RestMethod -Uri "$base/patient/medical-records/$registerId" -Headers $patientHeaders
$d3bOk = ($d3b.code -ne 200)
Test-Step 'D3b patient blocked before submit' $d3bOk ($d3b | ConvertTo-Json -Compress)

# D3c - submit diagnosis
$d3c = Invoke-RestMethod -Uri "$base/doctor/medical-records/$registerId/submit" -Method POST -Headers $doctorHeaders -ContentType 'application/json' -Body $recBody
Test-Step 'D3c submit medical record' ($d3c.code -eq 200 -and $d3c.data.status -eq 2) ($d3c | ConvertTo-Json -Compress)

# D4 - patient read after submit
$d4 = Invoke-RestMethod -Uri "$base/patient/medical-records/$registerId" -Headers $patientHeaders
Test-Step 'D4 patient read record' ($d4.code -eq 200 -and $d4.data.readme -eq 'accept chief') ($d4 | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "Summary: PASS=$passed FAIL=$failed registerId=$registerId"
if ($failed -eq 0) { Write-Host "R-min ACCEPTANCE: PASSED" -ForegroundColor Green } else { Write-Host "R-min ACCEPTANCE: FAILED" -ForegroundColor Red }
