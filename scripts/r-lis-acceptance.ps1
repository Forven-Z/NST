# R-lis acceptance — Gateway :9000 (extends R-min flow with inspection)
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
$code = "dev-lis-$(Get-Random)"
$passed = 0
$failed = 0
$medTechId = 2

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
Write-Host " R-lis Acceptance  Gateway: $base"
Write-Host "========================================"

# A: staff login
$a1 = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
Test-Step 'A1 doctor login' ($a1.code -eq 200 -and $a1.data.accessToken) ($a1 | ConvertTo-Json -Compress)
$doctorToken = $a1.data.accessToken
$doctorHeaders = @{ Authorization = "Bearer $doctorToken" }

# B: patient login
$b1Body = @{ code = $code; nickName = 'lis-patient' } | ConvertTo-Json -Compress
$b1 = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body $b1Body
Test-Step 'B1 patient login' ($b1.code -eq 200 -and $b1.data.accessToken) ($b1 | ConvertTo-Json -Compress)
$patientToken = $b1.data.accessToken
$patientHeaders = @{ Authorization = "Bearer $patientToken" }

# C: register + pay
$c1 = Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1" -Headers $patientHeaders
$sched = $c1.data.list[0]
$regBody = @{
    schedulingId = $sched.schedulingId
    deptId = $sched.deptId
    employeeId = $sched.employeeId
    registLevelId = $sched.registLevelId
    settleCategoryId = 1
} | ConvertTo-Json -Compress
$c2 = Invoke-RestMethod -Uri "$base/patient/registers" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body $regBody
$c3 = Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body "{`"billIds`":[$($c2.data.billId)]}"
$registerId = $c2.data.registerId
Test-Step 'C register+pay' ($c3.code -eq 200) ($c3 | ConvertTo-Json -Compress)

# D: call patient
$d2 = Invoke-RestMethod -Uri "$base/doctor/call/$registerId" -Method POST -Headers $doctorHeaders
Test-Step 'D call patient' ($d2.code -eq 200 -and $d2.data.visitState -eq 2) ($d2 | ConvertTo-Json -Compress)

# E1: doctor order inspection
$e1Body = @{
    registerId = $registerId
    medicalTechnologyId = $medTechId
    purpose = 'accept lis'
    bodyPart = 'blood'
} | ConvertTo-Json -Compress
$e1 = Invoke-RestMethod -Uri "$base/doctor/inspection-requests" -Method POST -Headers $doctorHeaders -ContentType 'application/json' -Body $e1Body
Test-Step 'E1 order inspection' ($e1.code -eq 200 -and $e1.data.inspectionRequestId -and $e1.data.billId -and $e1.data.status -eq 10) ($e1 | ConvertTo-Json -Compress)
$inspectionId = $e1.data.inspectionRequestId
$insBillId = $e1.data.billId

# E2: patient pay inspection bill
$e2 = Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body "{`"billIds`":[$insBillId]}"
Test-Step 'E2 pay inspection' ($e2.code -eq 200) ($e2 | ConvertTo-Json -Compress)

# E3: lab queue + execute + refund guard + result
$labLogin = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"lab01","password":"123456"}'
$labHeaders = @{ Authorization = "Bearer $($labLogin.data.accessToken)" }
$e3q = Invoke-RestMethod -Uri "$base/lis/queue?status=20" -Headers $labHeaders
$inQueue = @($e3q.data.list | Where-Object { $_.inspectionRequestId -eq $inspectionId }).Count -gt 0
Test-Step 'E3 queue' ($e3q.code -eq 200 -and $inQueue) "inspection $inspectionId not in queue"

$e3x = Invoke-RestMethod -Uri "$base/lis/requests/$inspectionId/execute" -Method POST -Headers $labHeaders
Test-Step 'E3a execute' ($e3x.code -eq 200 -and $e3x.data.status -eq 30) ($e3x | ConvertTo-Json -Compress)

$e3dPre = Invoke-RestMethod -Uri "$base/lis/requests/$inspectionId/result-detail" -Headers $labHeaders
$hasCritical = @($e3dPre.data.criticalItems).Count -gt 0
Test-Step 'E3b criticalItems on detail' ($e3dPre.code -eq 200 -and $hasCritical) ($e3dPre | ConvertTo-Json -Compress)

$refundBlocked = $false
try {
    $rfBody = @{ billId = $insBillId; reason = 'after execute' } | ConvertTo-Json -Compress
    $e3rf = Invoke-RestMethod -Uri "$base/patient/refunds" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body $rfBody
    $refundBlocked = ($e3rf.code -ne 200)
} catch { $refundBlocked = $true }
Test-Step 'E3c refund blocked after execute' $refundBlocked "refund should fail when status=30"

$resultBody = @{ resultText = 'WBC 6.5 normal' } | ConvertTo-Json -Compress
$e3r = Invoke-RestMethod -Uri "$base/lis/requests/$inspectionId/result" -Method POST -Headers $labHeaders -ContentType 'application/json' -Body $resultBody
Test-Step 'E3d save result' ($e3r.code -eq 200 -and $e3r.data.status -eq 40) ($e3r | ConvertTo-Json -Compress)

$e3d = Invoke-RestMethod -Uri "$base/lis/requests/$inspectionId/result-detail" -Headers $labHeaders
Test-Step 'E3e result-detail' ($e3d.code -eq 200 -and $e3d.data.status -eq 40) ($e3d | ConvertTo-Json -Compress)

# E4: doctor read result (extended)
$e4 = Invoke-RestMethod -Uri "$base/doctor/inspection-requests/$inspectionId/result" -Headers $doctorHeaders
Test-Step 'E4 doctor read result' (
    $e4.code -eq 200 -and $e4.data.resultText -and $e4.data.reportTime -and $e4.data.instrumentData
) ($e4 | ConvertTo-Json -Compress)

# E5: patient reports (no instrumentData)
$e5 = Invoke-RestMethod -Uri "$base/patient/reports?type=lab" -Headers $patientHeaders
$labReport = @($e5.data.list | Where-Object { $_.requestId -eq $inspectionId })[0]
Test-Step 'E5 patient report summary' (
    $e5.code -eq 200 -and $labReport -and $labReport.summary -and -not $labReport.PSObject.Properties['instrumentData']
) ($e5 | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "Summary: PASS=$passed FAIL=$failed inspectionId=$inspectionId registerId=$registerId"
if ($failed -eq 0) { Write-Host "R-lis ACCEPTANCE: PASSED" -ForegroundColor Green } else { Write-Host "R-lis ACCEPTANCE: FAILED" -ForegroundColor Red }
