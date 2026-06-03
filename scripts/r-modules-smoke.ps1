# Smoke test: management + ai-bridge health and dict
$base = 'http://127.0.0.1:9000/api/v1'
$passed = 0; $failed = 0

function Test-Step($name, $condition, $detail) {
    if ($condition) { Write-Host "[PASS] $name" -ForegroundColor Green; $script:passed++ }
    else { Write-Host "[FAIL] $name - $detail" -ForegroundColor Red; $script:failed++ }
}

$h1 = Invoke-RestMethod "$base/admin/health"
$h2 = Invoke-RestMethod "$base/ai/health"
Test-Step 'admin health' ($h1.code -eq 200) ($h1 | ConvertTo-Json -Compress)
Test-Step 'ai health' ($h2.code -eq 200 -and $h2.data.stub) ($h2 | ConvertTo-Json -Compress)

$admin = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}'
$adminHeaders = @{ Authorization = "Bearer $($admin.data.accessToken)" }
$d = Invoke-RestMethod -Uri "$base/admin/departments" -Headers $adminHeaders
Test-Step 'departments list' ($d.code -eq 200 -and $d.data.list.Count -gt 0) ($d | ConvertTo-Json -Compress)

$patient = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body '{"code":"ai-smoke","nickName":"ai-test"}'
$ptHeaders = @{ Authorization = "Bearer $($patient.data.accessToken)" }
$ai = Invoke-RestMethod -Uri "$base/ai/triage/chat" -Method POST -Headers $ptHeaders -ContentType 'application/json' -Body '{"message":"headache"}'
Test-Step 'ai triage stub' ($ai.code -eq 200 -and $ai.data.stub) ($ai | ConvertTo-Json -Compress)

Write-Host "Summary: PASS=$passed FAIL=$failed"
