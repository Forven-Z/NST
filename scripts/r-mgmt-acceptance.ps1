# Management core acceptance via Gateway :9000
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:9000/api/v1'
$suffix = "mgmt$(Get-Random)"
$deptCode = "TST_$suffix"
$empNo = "E_$suffix"
$username = "doc_$suffix"
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
Write-Host " Management Core Acceptance  Gateway: $base"
Write-Host " suffix: $suffix"
Write-Host "========================================"

# M1 admin login
$m1 = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}'
Test-Step 'M1 admin login' ($m1.code -eq 200 -and $m1.data.accessToken) ($m1 | ConvertTo-Json -Compress)
$adminHeaders = @{ Authorization = "Bearer $($m1.data.accessToken)" }

# M2 GET departments
$m2 = Invoke-RestMethod -Uri "$base/admin/departments?pageSize=20" -Headers $adminHeaders
Test-Step 'M2 list departments' ($m2.code -eq 200 -and $m2.data.list.Count -gt 0) ($m2 | ConvertTo-Json -Compress)

# M3 POST department
$deptBody = @{
    deptCode = $deptCode
    deptName = "TestDept_$suffix"
    deptType = 1
    sortNo   = 99
} | ConvertTo-Json -Compress
$m3 = Invoke-RestMethod -Uri "$base/admin/departments" -Method POST -Headers $adminHeaders -ContentType 'application/json' -Body $deptBody
Test-Step 'M3 create department' ($m3.code -eq 200 -and $m3.data.id) ($m3 | ConvertTo-Json -Compress)
$newDeptId = $m3.data.id

# M4 PUT department
$putDeptBody = @{
    deptName = "TestDeptUpdated_$suffix"
    deptType = 1
    sortNo   = 100
} | ConvertTo-Json -Compress
$m4 = Invoke-RestMethod -Uri "$base/admin/departments/$newDeptId" -Method PUT -Headers $adminHeaders -ContentType 'application/json' -Body $putDeptBody
Test-Step 'M4 update department' ($m4.code -eq 200 -and $m4.data.deptName -like '*Updated*') ($m4 | ConvertTo-Json -Compress)

# M5 POST employee + account
$empBody = @{
    empNo    = $empNo
    realName = "TestDoctor_$suffix"
    gender   = 1
    deptId   = $newDeptId
    title    = "Attending"
    roleType = "OUTPATIENT_DOCTOR"
    phone    = "13800000000"
    username = $username
    password = "123456"
} | ConvertTo-Json -Compress
$m5 = Invoke-RestMethod -Uri "$base/admin/employees" -Method POST -Headers $adminHeaders -ContentType 'application/json' -Body $empBody
Test-Step 'M5 create employee' ($m5.code -eq 200 -and $m5.data.employeeId -and $m5.data.username -eq $username) ($m5 | ConvertTo-Json -Compress)
$newEmployeeId = $m5.data.employeeId

# M6 new account login
$m6Body = @{ username = $username; password = "123456" } | ConvertTo-Json -Compress
$m6 = Invoke-RestMethod -Uri "$base/auth/staff/login" -Method POST -ContentType 'application/json' -Body $m6Body
Test-Step 'M6 employee login' ($m6.code -eq 200 -and ($m6.data.roles -contains "OUTPATIENT_DOCTOR")) ($m6 | ConvertTo-Json -Compress)

# Second doctor in same dept for substitute test
$subEmpNo = "SUB_$suffix"
$subUsername = "sub_$suffix"
$subBody = @{
    empNo    = $subEmpNo
    realName = "SubDoctor_$suffix"
    gender   = 1
    deptId   = $newDeptId
    title    = "Associate"
    roleType = "OUTPATIENT_DOCTOR"
    username = $subUsername
    password = "123456"
} | ConvertTo-Json -Compress
$subEmp = Invoke-RestMethod -Uri "$base/admin/employees" -Method POST -Headers $adminHeaders -ContentType 'application/json' -Body $subBody
$substituteEmployeeId = $subEmp.data.employeeId

# M7 POST scheduling draft
$tomorrow = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
$schedBody = @{
    employeeId    = $newEmployeeId
    registLevelId = 1
    workDate      = $tomorrow
    noonType      = 3
    totalQuota    = 15
} | ConvertTo-Json -Compress
$m7 = Invoke-RestMethod -Uri "$base/admin/scheduling" -Method POST -Headers $adminHeaders -ContentType 'application/json' -Body $schedBody
Test-Step 'M7 create scheduling draft' ($m7.code -eq 200 -and $m7.data.schedulingId -and $m7.data.publishStatus -eq 0) ($m7 | ConvertTo-Json -Compress)
$schedulingId = $m7.data.schedulingId

# M8 POST publish
$m8 = Invoke-RestMethod -Uri "$base/admin/scheduling/$schedulingId/publish" -Method POST -Headers $adminHeaders
Test-Step 'M8 publish scheduling' ($m8.code -eq 200 -and $m8.data.publishStatus -eq 1) ($m8 | ConvertTo-Json -Compress)

# M9 patient can see schedule
$code = "mgmt-patient-$suffix"
$b1Body = @{ code = $code; nickName = 'mgmt-patient' } | ConvertTo-Json -Compress
$b1 = Invoke-RestMethod -Uri "$base/patient/auth/wechat" -Method POST -ContentType 'application/json' -Body $b1Body
$patientHeaders = @{ Authorization = "Bearer $($b1.data.accessToken)" }
$m9 = Invoke-RestMethod -Uri "$base/patient/schedules?deptId=$newDeptId&workDate=$tomorrow" -Headers $patientHeaders
$found = @($m9.data.list | Where-Object { $_.schedulingId -eq $schedulingId }).Count -gt 0
Test-Step 'M9 patient sees published schedule' ($m9.code -eq 200 -and $found) "schedulingId $schedulingId not in patient list"

# M10 substitute doctor
$subBody2 = @{ employeeId = $substituteEmployeeId } | ConvertTo-Json -Compress
$m10 = Invoke-RestMethod -Uri "$base/admin/scheduling/$schedulingId" -Method PUT -Headers $adminHeaders -ContentType 'application/json' -Body $subBody2
Test-Step 'M10 substitute doctor' ($m10.code -eq 200 -and $m10.data.employeeId -eq $substituteEmployeeId) ($m10 | ConvertTo-Json -Compress)

# M11 DELETE employees
$m11a = Invoke-RestMethod -Uri "$base/admin/employees/$newEmployeeId" -Method DELETE -Headers $adminHeaders
$m11b = Invoke-RestMethod -Uri "$base/admin/employees/$substituteEmployeeId" -Method DELETE -Headers $adminHeaders
Test-Step 'M11 disable employees' ($m11a.code -eq 200 -and $m11b.code -eq 200) ($m11a | ConvertTo-Json -Compress)

# M12 DELETE department
$m12 = Invoke-RestMethod -Uri "$base/admin/departments/$newDeptId" -Method DELETE -Headers $adminHeaders
Test-Step 'M12 disable department' ($m12.code -eq 200) ($m12 | ConvertTo-Json -Compress)

Write-Host "========================================"
Write-Host "Summary: PASS=$passed FAIL=$failed"
Write-Host "========================================"

if ($failed -gt 0) { exit 1 }
