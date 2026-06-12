# R-disposal acceptance — Gateway :9000 (extends R-min flow with disposal)
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
$code = "dev-disposal-$(Get-Random)"
$passed = 0
$failed = 0
$medTechId = 3

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
Write-Host " R-disposal Acceptance  Gateway: $base"
Write-Host "========================================"

# A: staff login
$a1 = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
Test-Step 'A1 doctor login' ($a1.code -eq 200 -and $a1.data.accessToken) ($a1 | ConvertTo-Json -Compress)
$doctorToken = $a1.data.accessToken
$doctorHeaders = @{ Authorization = "Bearer $doctorToken" }

# B: patient login
$b1Body = @{ code = $code; nickName = 'disposal-patient' } | ConvertTo-Json -Compress
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

# E1: doctor order disposal
$e1Body = @{
    registerId = $registerId
    medicalTechnologyId = $medTechId
    purpose = 'accept disposal'
    bodyPart = 'stomach'
} | ConvertTo-Json -Compress
$e1 = Invoke-RestMethod -Uri "$base/doctor/disposal-requests" -Method POST -Headers $doctorHeaders -ContentType 'application/json' -Body $e1Body
Test-Step 'E1 order disposal' ($e1.code -eq 200 -and $e1.data.disposalRequestId -and $e1.data.billId -and $e1.data.status -eq 10) ($e1 | ConvertTo-Json -Compress)
$disposalId = $e1.data.disposalRequestId
$disBillId = $e1.data.billId

# E2: patient pay disposal bill
$e2 = Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body "{`"billIds`":[$disBillId]}"
Test-Step 'E2 pay disposal' ($e2.code -eq 200) ($e2 | ConvertTo-Json -Compress)

# E3: disposal queue + three-phase result
$disLogin = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"disposal01","password":"123456"}'
$disHeaders = @{ Authorization = "Bearer $($disLogin.data.accessToken)" }
$e3q = Invoke-RestMethod -Uri "$base/disposal/queue?status=20" -Headers $disHeaders
$inQueue = @($e3q.data.list | Where-Object { $_.disposalRequestId -eq $disposalId }).Count -gt 0
Test-Step 'E3 disposal queue' ($e3q.code -eq 200 -and $inQueue) "disposal $disposalId not in queue"

$e3x = Invoke-RestMethod -Uri "$base/disposal/requests/$disposalId/execute" -Method POST -Headers $disHeaders
Test-Step 'E3 execute' ($e3x.code -eq 200 -and $e3x.data.status -eq 30) ($e3x | ConvertTo-Json -Compress)

$e3d = Invoke-RestMethod -Uri "$base/disposal/requests/$disposalId/result-detail" -Headers $disHeaders
Test-Step 'E3 result-detail' ($e3d.code -eq 200 -and $e3d.data.instrumentData) ($e3d | ConvertTo-Json -Compress)

$e3a = Invoke-RestMethod -Uri "$base/disposal/requests/$disposalId/ai-report" -Method POST -Headers $disHeaders
Test-Step 'E3 ai-report' ($e3a.code -eq 200 -and $e3a.data.aiReportStatus -eq 'READY') ($e3a | ConvertTo-Json -Compress)

$resultBody = @{
    aiReportText = 'AI disposal summary: gastric lavage completed smoothly'
    doctorReportText = 'Patient tolerated well, no aspiration observed'
} | ConvertTo-Json -Compress
$e3r = Invoke-RestMethod -Uri "$base/disposal/requests/$disposalId/result" -Method POST -Headers $disHeaders -ContentType 'application/json' -Body $resultBody
Test-Step 'E3 save result (dual-field)' ($e3r.code -eq 200 -and $e3r.data.status -eq 40) ($e3r | ConvertTo-Json -Compress)

# E4: doctor read result with section 1.7 fields
$e4 = Invoke-RestMethod -Uri "$base/doctor/disposal-requests/$disposalId/result" -Headers $doctorHeaders
Test-Step 'E4 doctor read result' ($e4.code -eq 200 -and $e4.data.resultText -and $e4.data.instrumentData -and $e4.data.aiReportText) ($e4 | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "Summary: PASS=$passed FAIL=$failed disposalId=$disposalId registerId=$registerId"
if ($failed -eq 0) { Write-Host "R-disposal ACCEPTANCE: PASSED" -ForegroundColor Green } else { Write-Host "R-disposal ACCEPTANCE: FAILED" -ForegroundColor Red }
