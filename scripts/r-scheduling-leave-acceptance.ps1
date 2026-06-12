# Scheduling leave acceptance via Gateway :9000
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
Write-Host " Scheduling Leave Acceptance  Gateway: $base"
Write-Host "========================================"

# L1 doctor login
$doc = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"doctor01","password":"123456"}'
Test-Step 'L1 doctor login' ($doc.code -eq 200 -and $doc.data.employeeId) ($doc | ConvertTo-Json -Compress)
$doctorId = $doc.data.employeeId
$docHeaders = @{ Authorization = "Bearer $($doc.data.accessToken)" }

# L2 my schedules — pick first shift eligible for leave
$today = (Get-Date).ToString('yyyy-MM-dd')
$l2 = Invoke-RestMethod -Uri "$base/staff/my-schedules?employeeId=$doctorId&workDateFrom=$today" -Headers $docHeaders
Test-Step 'L2 my schedules' ($l2.code -eq 200 -and $l2.data.list.Count -gt 0) ($l2 | ConvertTo-Json -Compress)
$target = @($l2.data.list | Where-Object { $_.canRequestLeave -eq $true })[0]
if (-not $target) {
    $target = $l2.data.list[0]
}
$schedulingId = $target.schedulingId

# L3 submit leave
$leaveBody = @{ employeeId = $doctorId; reason = "acceptance test leave $(Get-Random)" } | ConvertTo-Json -Compress
$leaveOk = $false
$leaveRequestId = $null
try {
    $l3 = Invoke-RestMethod -Uri "$base/staff/schedules/$schedulingId/leave-requests" -Method POST -Headers $docHeaders -ContentType 'application/json' -Body $leaveBody
    $leaveOk = $l3.code -eq 200
    $leaveRequestId = $l3.data.leaveRequestId
} catch {
    $leaveOk = $false
    $errMsg = $_.Exception.Message
}
Test-Step 'L3 submit leave' $leaveOk "schedulingId=$schedulingId err=$errMsg"

# L4 admin list pending
$adm = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}'
$adminHeaders = @{ Authorization = "Bearer $($adm.data.accessToken)" }
$l4 = Invoke-RestMethod -Uri "$base/admin/leave-requests?status=0" -Headers $adminHeaders
$foundLeave = @($l4.data.list | Where-Object { $_.leaveRequestId -eq $leaveRequestId }).Count -gt 0
Test-Step 'L4 admin pending leave' ($l4.code -eq 200 -and $foundLeave) ($l4 | ConvertTo-Json -Compress)

# L5 approve
if ($leaveRequestId) {
    $l5 = Invoke-RestMethod -Uri "$base/admin/leave-requests/$leaveRequestId/approve" -Method POST -Headers $adminHeaders -ContentType 'application/json' -Body '{"adminName":"admin"}'
    Test-Step 'L5 approve leave' ($l5.code -eq 200 -and $l5.data.status -eq 1) ($l5 | ConvertTo-Json -Compress)
} else {
    Test-Step 'L5 approve leave' $false 'no leaveRequestId from L3'
}

# L6 schedule needs substitute
$l6 = Invoke-RestMethod -Uri "$base/admin/scheduling?employeeId=$doctorId" -Headers $adminHeaders
$row = $l6.data.list | Where-Object { $_.schedulingId -eq $schedulingId } | Select-Object -First 1
Test-Step 'L6 needsSubstitute' ($row.needsSubstitute -eq $true) ($row | ConvertTo-Json -Compress)

# L7 AI stub suggest
$l7 = Invoke-RestMethod -Uri "$base/admin/scheduling/ai-suggest" -Method POST -Headers $adminHeaders -ContentType 'application/json' -Body '{}'
Test-Step 'L7 AI suggest stub' ($l7.code -eq 50301 -and $l7.success -eq $false) ($l7 | ConvertTo-Json -Compress)

# L8 AI stub replace
$l8 = Invoke-RestMethod -Uri "$base/admin/scheduling/$schedulingId/ai-replace" -Method POST -Headers $adminHeaders
Test-Step 'L8 AI replace stub' ($l8.code -eq 50301 -and $l8.success -eq $false) ($l8 | ConvertTo-Json -Compress)

Write-Host "Summary: PASS=$passed FAIL=$failed"
if ($failed -gt 0) { exit 1 }
