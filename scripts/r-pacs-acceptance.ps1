# R-pacs acceptance — check order flow via Gateway :9000
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
$code = "dev-pacs-$(Get-Random)"
$passed = 0
$failed = 0
$medTechId = 1

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
Write-Host " R-pacs Acceptance  Gateway: $base"
Write-Host "========================================"

$a1 = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
$doctorHeaders = @{ Authorization = "Bearer $($a1.data.accessToken)" }

$b1Body = @{ code = $code; nickName = 'pacs-patient' } | ConvertTo-Json -Compress
$b1 = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body $b1Body
$patientHeaders = @{ Authorization = "Bearer $($b1.data.accessToken)" }

$c1 = Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1" -Headers $patientHeaders
$sched = $c1.data.list[0]
$regBody = @{
    schedulingId = $sched.schedulingId; deptId = $sched.deptId
    employeeId = $sched.employeeId; registLevelId = $sched.registLevelId; settleCategoryId = 1
} | ConvertTo-Json -Compress
$c2 = Invoke-RestMethod -Uri "$base/patient/registers" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body $regBody
$c3 = Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body "{`"billIds`":[$($c2.data.billId)]}"
$registerId = $c2.data.registerId

$d2 = Invoke-RestMethod -Uri "$base/doctor/call/$registerId" -Method POST -Headers $doctorHeaders
Test-Step 'call patient' ($d2.code -eq 200) ($d2 | ConvertTo-Json -Compress)

$e1Body = @{
    registerId = $registerId; medicalTechnologyId = $medTechId
    purpose = 'accept pacs'; bodyPart = 'head'
} | ConvertTo-Json -Compress
$e1 = Invoke-RestMethod -Uri "$base/doctor/check-requests" -Method POST -Headers $doctorHeaders -ContentType 'application/json' -Body $e1Body
Test-Step 'order check' ($e1.code -eq 200 -and $e1.data.checkRequestId -and $e1.data.status -eq 10) ($e1 | ConvertTo-Json -Compress)
$checkId = $e1.data.checkRequestId
$checkBillId = $e1.data.billId

$e2 = Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body "{`"billIds`":[$checkBillId]}"
Test-Step 'pay check bill' ($e2.code -eq 200) ($e2 | ConvertTo-Json -Compress)

$checkLogin = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"check01","password":"123456"}'
$checkHeaders = @{ Authorization = "Bearer $($checkLogin.data.accessToken)" }
$q = Invoke-RestMethod -Uri "$base/pacs/queue?status=20" -Headers $checkHeaders
$inQueue = @($q.data.list | Where-Object { $_.checkRequestId -eq $checkId }).Count -gt 0
Test-Step 'pacs queue' ($q.code -eq 200 -and $inQueue) "check $checkId not in queue"

$resultBody = @{ resultText = 'CT normal' } | ConvertTo-Json -Compress
$r = Invoke-RestMethod -Uri "$base/pacs/requests/$checkId/result" -Method POST -Headers $checkHeaders -ContentType 'application/json' -Body $resultBody
Test-Step 'pacs result' ($r.code -eq 200 -and $r.data.status -eq 40) ($r | ConvertTo-Json -Compress)

$dr = Invoke-RestMethod -Uri "$base/doctor/check-requests/$checkId/result" -Headers $doctorHeaders
Test-Step 'doctor read result' ($dr.code -eq 200 -and $dr.data.resultText) ($dr | ConvertTo-Json -Compress)

$stub = Invoke-RestMethod -Uri "$base/pacs/imaging/upload" -Method POST -Headers $checkHeaders
Test-Step 'imaging stub' ($stub.code -eq 200 -and $stub.data.stub) ($stub | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "Summary: PASS=$passed FAIL=$failed checkId=$checkId"
if ($failed -eq 0) { Write-Host "R-pacs ACCEPTANCE: PASSED" -ForegroundColor Green } else { Write-Host "R-pacs ACCEPTANCE: FAILED" -ForegroundColor Red }
