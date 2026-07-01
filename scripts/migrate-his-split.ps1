# ADR-019: Split hospital-his into patient + pharmacy + clinical
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$HisJava = Join-Path $Root 'hospital-backend\hospital-his\src\main\java\com\hospital\his'
$PatientJava = Join-Path $Root 'hospital-backend\hospital-patient\src\main\java\com\hospital\patient'
$PharmacyJava = Join-Path $Root 'hospital-backend\hospital-pharmacy\src\main\java\com\hospital\pharmacy'

function Ensure-Dir($path) {
    $dir = Split-Path -Parent $path
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
}

function Copy-RenamePackage($srcRel, $targetBase, $fromPkg, $toPkg) {
    $src = Join-Path $HisJava $srcRel
    if (-not (Test-Path $src)) { Write-Warning "Missing: $srcRel"; return }
    $dest = Join-Path $targetBase ($srcRel -replace '\\', [IO.Path]::DirectorySeparatorChar)
    Ensure-Dir $dest
    $content = Get-Content $src -Raw -Encoding UTF8
    $content = $content -replace [regex]::Escape($fromPkg), $toPkg
    $content = $content -replace 'HisProperties', $(if ($toPkg -eq 'com.hospital.patient') { 'PatientProperties' } elseif ($toPkg -eq 'com.hospital.pharmacy') { 'PharmacyProperties' } else { 'HisProperties' })
    $content = $content -replace 'hospital-his', $(if ($toPkg -eq 'com.hospital.patient') { 'hospital-patient' } elseif ($toPkg -eq 'com.hospital.pharmacy') { 'hospital-pharmacy' } else { 'hospital-his' })
    Set-Content -Path $dest -Value $content -Encoding utf8NoBOM
}

$patientFiles = @(
    'client\AuthTokenFeignClient.java', 'client\PacsInternalImagingClient.java',
    'client\dto\PatientTokenFeignRequest.java', 'client\dto\PatientTokenFeignResponse.java',
    'config\FeignConfig.java', 'config\HisClientConfig.java',
    'controller\patient\PatientAuthController.java', 'controller\patient\PatientExamSnapshotController.java',
    'controller\patient\PatientFamilyController.java', 'controller\patient\PatientOutpatientController.java',
    'controller\patient\PatientProfileController.java', 'controller\registrar\RegistrarController.java',
    'dto\patient\AddFamilyMemberRequest.java', 'dto\patient\CreateRegisterRequest.java',
    'dto\patient\MockPaymentRequest.java', 'dto\patient\PatientLoginRequest.java',
    'dto\patient\PatientProfileResponse.java', 'dto\patient\PatientProfileUpdateRequest.java',
    'dto\patient\SwitchAccountRequest.java', 'dto\patient\WechatBindRequest.java',
    'dto\patient\WechatLoginRequest.java', 'dto\patient\WechatLoginResponse.java',
    'dto\registrar\CancelRegisterRequest.java', 'dto\registrar\RefundRequest.java',
    'dto\registrar\WindowChargeRequest.java', 'dto\registrar\WindowRegisterRequest.java',
    'repository\BillRepository.java', 'repository\PatientFamilyRepository.java', 'repository\PatientRepository.java',
    'repository\PaymentRepository.java', 'repository\RefundRepository.java', 'repository\RegisterRepository.java',
    'repository\SchedulingRepository.java', 'repository\SettleCategoryRepository.java',
    'repository\DepartmentRepository.java', 'repository\EmployeeRepository.java',
    'repository\PrescriptionRepository.java', 'repository\InspectionRequestRepository.java',
    'repository\CheckRequestRepository.java', 'repository\DisposalRequestRepository.java',
    'repository\MedicalRecordRepository.java', 'repository\MedicalRecordDiseaseRepository.java',
    'repository\ImagingStudyRepository.java', 'repository\InspectionResultItemRepository.java',
    'schedule\RegisterLifecycleScheduler.java',
    'service\FinancialQueryService.java', 'service\PatientAuthService.java', 'service\PatientExamSnapshotService.java',
    'service\PatientFamilyService.java', 'service\PatientIdentityMergeService.java', 'service\PatientLoginPersistence.java',
    'service\PatientMedicalRecordQueryService.java', 'service\PatientPrescriptionQueryService.java',
    'service\PatientProfileService.java', 'service\PatientRegisterQueryService.java', 'service\PatientReportService.java',
    'service\PatientWechatBindService.java', 'service\PatientWechatService.java', 'service\PaymentService.java',
    'service\RegistrarChargeService.java', 'service\RegistrarQueryService.java', 'service\RegistrarRegisterService.java',
    'service\RegisterCancelService.java', 'service\RegisterLifecycleService.java', 'service\RegisterService.java',
    'service\RefundService.java', 'service\SchedulingService.java', 'service\WechatAuthService.java',
    'service\RegisterOrdersService.java', 'service\VisitRecordQueryService.java',
    'service\CheckReportQueryService.java', 'service\LabReportQueryService.java', 'service\DisposalRecordQueryService.java',
    'service\PatientRegisterMedicalRecordService.java',
    'support\NoonTypeSupport.java', 'support\CheckReportImagingSupport.java',
    'util\IdCardUtils.java', 'util\BizNoGenerator.java',
    'security\AuthContext.java', 'security\AuthContextHolder.java', 'security\JwtAuthFilter.java',
    'visit\PatientVisitLifecycleCoordinator.java'
)

$pharmacyFiles = @(
    'controller\pharmacy\PharmacyController.java', 'controller\pharmacy\PharmacyDrugController.java',
    'dto\pharmacy\CreateDrugRequest.java', 'dto\pharmacy\RejectPrescriptionRequest.java', 'dto\pharmacy\UpdateDrugRequest.java',
    'repository\DrugRepository.java', 'repository\PrescriptionRepository.java',
    'service\PharmacyDrugService.java', 'service\PharmacyService.java',
    'order\state\OrderStatusCoordinator.java',
    'security\AuthContext.java', 'security\AuthContextHolder.java', 'security\JwtAuthFilter.java',
    'config\FeignConfig.java'
)

foreach ($f in $patientFiles) {
    if ($f -eq 'visit\PatientVisitLifecycleCoordinator.java') { continue }
    Copy-RenamePackage $f $PatientJava 'com.hospital.his' 'com.hospital.patient'
}
foreach ($f in $pharmacyFiles) {
    Copy-RenamePackage $f $PharmacyJava 'com.hospital.his' 'com.hospital.pharmacy'
}

Write-Host 'Migration copy done.' -ForegroundColor Green
