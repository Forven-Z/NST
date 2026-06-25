# Pharmacy drug catalog acceptance — Gateway :9000
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

Write-Host '========================================'
Write-Host " Pharmacy Drug Acceptance  Gateway: $base"
Write-Host '========================================'

$pharm = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"pharmacy01","password":"123456"}'
$pharmHeaders = @{ Authorization = "Bearer $($pharm.data.accessToken)" }

$doctor = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
$doctorHeaders = @{ Authorization = "Bearer $($doctor.data.accessToken)" }

$createBody = '{"drugName":"AcceptTestDrug","retailPrice":9.90,"stockQty":50,"unit":"box"}'
$created = Invoke-RestMethod -Uri "$base/pharmacy/drugs" -Method POST -Headers $pharmHeaders -ContentType 'application/json' -Body $createBody
Test-Step 'create drug' ($created.code -eq 200 -and $created.data.drugCode -like 'DRG-*' -and $created.data.id) ($created | ConvertTo-Json -Compress)
$drugId = $created.data.id

$doctorDrugs = Invoke-RestMethod -Uri "$base/doctor/drugs?pageSize=100" -Headers $doctorHeaders
$visibleBefore = @($doctorDrugs.data.list | Where-Object { $_.id -eq $drugId }).Count -gt 0
Test-Step 'doctor sees new drug' $visibleBefore "drug $drugId not in doctor list"

$updateBody = '{"retailPrice":12.50,"stockQty":40}'
$updated = Invoke-RestMethod -Uri "$base/pharmacy/drugs/$drugId" -Method PUT -Headers $pharmHeaders -ContentType 'application/json' -Body $updateBody
Test-Step 'update drug' ($updated.code -eq 200 -and [decimal]$updated.data.retailPrice -eq 12.50 -and $updated.data.stockQty -eq 40) ($updated | ConvertTo-Json -Compress)

$disabled = Invoke-RestMethod -Uri "$base/pharmacy/drugs/$drugId/disable" -Method POST -Headers $pharmHeaders
Test-Step 'disable drug' ($disabled.code -eq 200 -and $disabled.data.disabled -eq $true) ($disabled | ConvertTo-Json -Compress)

$doctorDrugs2 = Invoke-RestMethod -Uri "$base/doctor/drugs?pageSize=100" -Headers $doctorHeaders
$visibleAfter = @($doctorDrugs2.data.list | Where-Object { $_.id -eq $drugId }).Count -gt 0
Test-Step 'doctor hides disabled drug' (-not $visibleAfter) "drug $drugId still visible to doctor"

$enabled = Invoke-RestMethod -Uri "$base/pharmacy/drugs/$drugId/enable" -Method POST -Headers $pharmHeaders
Test-Step 'enable drug' ($enabled.code -eq 200 -and $enabled.data.disabled -eq $false) ($enabled | ConvertTo-Json -Compress)

$list = Invoke-RestMethod -Uri "$base/pharmacy/drugs?includeDisabled=true&keyword=Accept" -Headers $pharmHeaders
$inList = @($list.data.list | Where-Object { $_.id -eq $drugId }).Count -gt 0
Test-Step 'pharmacy list with disabled' ($list.code -eq 200 -and $inList) ($list | ConvertTo-Json -Compress)

Write-Host ''
Write-Host "Summary: PASS=$passed FAIL=$failed drugId=$drugId"
if ($failed -eq 0) { Write-Host 'PHARMACY DRUG ACCEPTANCE: PASSED' -ForegroundColor Green } else { Write-Host 'PHARMACY DRUG ACCEPTANCE: FAILED' -ForegroundColor Red }
