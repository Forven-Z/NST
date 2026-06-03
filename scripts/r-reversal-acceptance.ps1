# Reversal acceptance — cancel register / refund / return drug
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
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
Write-Host " Reversal Acceptance  Gateway: $base"
Write-Host "========================================"

# --- 1. Cancel register (patient) ---
$code1 = "dev-cancel-$(Get-Random)"
$p1 = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body (@{ code = $code1; nickName = 'cancel-p' } | ConvertTo-Json -Compress)
$h1 = @{ Authorization = "Bearer $($p1.data.accessToken)" }
$sched = (Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1" -Headers $h1).data.list[0]
$reg1 = Invoke-RestMethod -Uri "$base/patient/registers" -Method POST -Headers $h1 -ContentType 'application/json' -Body (@{
    schedulingId = $sched.schedulingId; deptId = $sched.deptId; employeeId = $sched.employeeId
    registLevelId = $sched.registLevelId; settleCategoryId = 1
} | ConvertTo-Json -Compress)
Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $h1 -ContentType 'application/json' -Body "{`"billIds`":[$($reg1.data.billId)]}" | Out-Null
$cancel = Invoke-RestMethod -Uri "$base/patient/registers/$($reg1.data.registerId)/cancel" -Method POST -Headers $h1 -ContentType 'application/json' -Body '{"reason":"change plan"}'
Test-Step 'cancel register' ($cancel.code -eq 200 -and $cancel.data.visitState -eq 4) ($cancel | ConvertTo-Json -Compress)

# --- 2. Refund paid prescription (not dispensed) ---
$code2 = "dev-refund-$(Get-Random)"
$doctor = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
$dh = @{ Authorization = "Bearer $($doctor.data.accessToken)" }
$p2 = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body (@{ code = $code2; nickName = 'refund-p' } | ConvertTo-Json -Compress)
$h2 = @{ Authorization = "Bearer $($p2.data.accessToken)" }
$sched2 = (Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1" -Headers $h2).data.list[0]
$reg2 = Invoke-RestMethod -Uri "$base/patient/registers" -Method POST -Headers $h2 -ContentType 'application/json' -Body (@{
    schedulingId = $sched2.schedulingId; deptId = $sched2.deptId; employeeId = $sched2.employeeId
    registLevelId = $sched2.registLevelId; settleCategoryId = 1
} | ConvertTo-Json -Compress)
Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $h2 -ContentType 'application/json' -Body "{`"billIds`":[$($reg2.data.billId)]}" | Out-Null
Invoke-RestMethod -Uri "$base/doctor/call/$($reg2.data.registerId)" -Method POST -Headers $dh | Out-Null
$rx = Invoke-RestMethod -Uri "$base/doctor/prescriptions" -Method POST -Headers $dh -ContentType 'application/json' -Body (@{
    registerId = $reg2.data.registerId; remark = 'refund test'
    items = @(@{ drugId = 1; quantity = 1; usageMethod = 'oral'; dosage = '0.5g'; frequency = 'tid'; days = 3 })
} | ConvertTo-Json -Depth 5 -Compress)
Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $h2 -ContentType 'application/json' -Body "{`"billIds`":[$($rx.data.billId)]}" | Out-Null

$registrar = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"registrar01","password":"123456"}'
$rh = @{ Authorization = "Bearer $($registrar.data.accessToken)" }
$refund = Invoke-RestMethod -Uri "$base/registrar/refunds" -Method POST -Headers $rh -ContentType 'application/json' -Body "{`"billId`":$($rx.data.billId),`"reason`":`"not dispensed`"}"
Test-Step 'refund prescription' ($refund.code -eq 200 -and $refund.data.refundNo) ($refund | ConvertTo-Json -Compress)

# --- 3. Return drug + refund ---
$code3 = "dev-return-$(Get-Random)"
$p3 = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body (@{ code = $code3; nickName = 'return-p' } | ConvertTo-Json -Compress)
$h3 = @{ Authorization = "Bearer $($p3.data.accessToken)" }
$sched3 = (Invoke-RestMethod -Uri "$base/patient/schedules?deptId=1" -Headers $h3).data.list[0]
$reg3 = Invoke-RestMethod -Uri "$base/patient/registers" -Method POST -Headers $h3 -ContentType 'application/json' -Body (@{
    schedulingId = $sched3.schedulingId; deptId = $sched3.deptId; employeeId = $sched3.employeeId
    registLevelId = $sched3.registLevelId; settleCategoryId = 1
} | ConvertTo-Json -Compress)
Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $h3 -ContentType 'application/json' -Body "{`"billIds`":[$($reg3.data.billId)]}" | Out-Null
Invoke-RestMethod -Uri "$base/doctor/call/$($reg3.data.registerId)" -Method POST -Headers $dh | Out-Null
$rx3 = Invoke-RestMethod -Uri "$base/doctor/prescriptions" -Method POST -Headers $dh -ContentType 'application/json' -Body (@{
    registerId = $reg3.data.registerId; remark = 'return test'
    items = @(@{ drugId = 2; quantity = 1; usageMethod = 'oral'; dosage = '0.2g'; frequency = 'bid'; days = 3 })
} | ConvertTo-Json -Depth 5 -Compress)
Invoke-RestMethod -Uri "$base/patient/payments" -Method POST -Headers $h3 -ContentType 'application/json' -Body "{`"billIds`":[$($rx3.data.billId)]}" | Out-Null
$pharm = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"pharmacy01","password":"123456"}'
$ph = @{ Authorization = "Bearer $($pharm.data.accessToken)" }
Invoke-RestMethod -Uri "$base/pharmacy/prescriptions/$($rx3.data.prescriptionId)/dispense" -Method POST -Headers $ph | Out-Null
$ret = Invoke-RestMethod -Uri "$base/pharmacy/prescriptions/$($rx3.data.prescriptionId)/return-drug" -Method POST -Headers $ph
Test-Step 'return drug' ($ret.code -eq 200 -and $ret.data.status -eq 40) ($ret | ConvertTo-Json -Compress)
$refund2 = Invoke-RestMethod -Uri "$base/registrar/refunds" -Method POST -Headers $rh -ContentType 'application/json' -Body "{`"billId`":$($rx3.data.billId),`"reason`":`"return drug`"}"
Test-Step 'refund after return' ($refund2.code -eq 200) ($refund2 | ConvertTo-Json -Compress)

Write-Host ""
Write-Host "Summary: PASS=$passed FAIL=$failed"
if ($failed -eq 0) { Write-Host "REVERSAL ACCEPTANCE: PASSED" -ForegroundColor Green } else { Write-Host "REVERSAL ACCEPTANCE: FAILED" -ForegroundColor Red }
