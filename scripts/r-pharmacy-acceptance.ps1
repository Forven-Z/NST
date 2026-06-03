# Pharmacy acceptance — prescription + dispense via Gateway :9000
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
$code = "dev-rx-$(Get-Random)"
$passed = 0
$failed = 0
$drugId = 1

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
Write-Host " Pharmacy Acceptance  Gateway: $base"
Write-Host "========================================"

$doctor = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
$doctorHeaders = @{ Authorization = "Bearer $($doctor.data.accessToken)" }

$b1Body = @{ code = $code; nickName = 'rx-patient' } | ConvertTo-Json -Compress
$patient = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body $b1Body
$patientHeaders = @{ Authorization = "Bearer $($patient.data.accessToken)" }

$c1 = Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1" -Headers $patientHeaders
$sched = $c1.data.list[0]
$regBody = @{
    schedulingId = $sched.schedulingId; deptId = $sched.deptId
    employeeId = $sched.employeeId; registLevelId = $sched.registLevelId; settleCategoryId = 1
} | ConvertTo-Json -Compress
$c2 = Invoke-RestMethod -Uri "$base/patient/registers" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body $regBody
Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body "{`"billIds`":[$($c2.data.billId)]}" | Out-Null
$registerId = $c2.data.registerId

Invoke-RestMethod -Uri "$base/doctor/call/$registerId" -Method POST -Headers $doctorHeaders | Out-Null

$rxBody = @{
    registerId = $registerId
    remark = 'accept rx'
    items = @(@{
        drugId = $drugId
        quantity = 2
        usageMethod = 'oral'
        dosage = '0.5g'
        frequency = 'tid'
        days = 7
        entrust = 'after meal'
    })
} | ConvertTo-Json -Depth 5 -Compress
$rx = Invoke-RestMethod -Uri "$base/doctor/prescriptions" -Method POST -Headers $doctorHeaders -ContentType 'application/json' -Body $rxBody
Test-Step 'create prescription' ($rx.code -eq 200 -and $rx.data.prescriptionId -and $rx.data.status -eq 10 -and $rx.data.billId) ($rx | ConvertTo-Json -Compress)
$prescriptionId = $rx.data.prescriptionId
$rxBillId = $rx.data.billId

$pay = Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $patientHeaders -ContentType 'application/json' -Body "{`"billIds`":[$rxBillId]}"
Test-Step 'pay prescription' ($pay.code -eq 200) ($pay | ConvertTo-Json -Compress)

$pharm = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"pharmacy01","password":"123456"}'
$pharmHeaders = @{ Authorization = "Bearer $($pharm.data.accessToken)" }
$pending = Invoke-RestMethod -Uri "$base/pharmacy/pending?status=20" -Headers $pharmHeaders
$inPending = @($pending.data.list | Where-Object { $_.prescriptionId -eq $prescriptionId }).Count -gt 0
Test-Step 'pharmacy pending' ($pending.code -eq 200 -and $inPending) "rx $prescriptionId not pending"

$disp = Invoke-RestMethod -Uri "$base/pharmacy/prescriptions/$prescriptionId/dispense" -Method POST -Headers $pharmHeaders
Test-Step 'dispense' ($disp.code -eq 200 -and $disp.data.status -eq 30) ($disp | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "Summary: PASS=$passed FAIL=$failed prescriptionId=$prescriptionId"
if ($failed -eq 0) { Write-Host "PHARMACY ACCEPTANCE: PASSED" -ForegroundColor Green } else { Write-Host "PHARMACY ACCEPTANCE: FAILED" -ForegroundColor Red }
