# Delete files migrated from hospital-his to patient/pharmacy
$ErrorActionPreference = 'Stop'
$HisJava = Join-Path (Split-Path -Parent $PSScriptRoot) 'hospital-backend\hospital-his\src\main\java\com\hospital\his'
$toDelete = @(
    'client\AuthTokenFeignClient.java', 'client\PacsInternalImagingClient.java',
    'client\dto\PatientTokenFeignRequest.java', 'client\dto\PatientTokenFeignResponse.java',
    'controller\patient\PatientAuthController.java', 'controller\patient\PatientExamSnapshotController.java',
    'controller\patient\PatientFamilyController.java', 'controller\patient\PatientOutpatientController.java',
    'controller\patient\PatientProfileController.java', 'controller\registrar\RegistrarController.java',
    'controller\pharmacy\PharmacyController.java', 'controller\pharmacy\PharmacyDrugController.java',
    'dto\patient\AddFamilyMemberRequest.java', 'dto\patient\CreateRegisterRequest.java',
    'dto\patient\MockPaymentRequest.java', 'dto\patient\PatientLoginRequest.java',
    'dto\patient\PatientProfileResponse.java', 'dto\patient\PatientProfileUpdateRequest.java',
    'dto\patient\SwitchAccountRequest.java', 'dto\patient\WechatBindRequest.java',
    'dto\patient\WechatLoginRequest.java', 'dto\patient\WechatLoginResponse.java',
    'dto\registrar\CancelRegisterRequest.java', 'dto\registrar\RefundRequest.java',
    'dto\registrar\WindowChargeRequest.java', 'dto\registrar\WindowRegisterRequest.java',
    'dto\pharmacy\CreateDrugRequest.java', 'dto\pharmacy\RejectPrescriptionRequest.java', 'dto\pharmacy\UpdateDrugRequest.java',
    'repository\BillRepository.java', 'repository\PatientFamilyRepository.java', 'repository\PatientRepository.java',
    'repository\PaymentRepository.java', 'repository\RefundRepository.java',
    'repository\SettleCategoryRepository.java', 'repository\DepartmentRepository.java',
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
    'service\CheckReportQueryService.java', 'service\LabReportQueryService.java', 'service\DisposalRecordQueryService.java',
    'service\PharmacyDrugService.java', 'service\PharmacyService.java',
    'support\NoonTypeSupport.java', 'support\CheckReportImagingSupport.java',
    'util\IdCardUtils.java', 'util\BizNoGenerator.java'
)
foreach ($f in $toDelete) {
    $p = Join-Path $HisJava $f
    if (Test-Path $p) {
        Remove-Item $p -Force
        Write-Host "Deleted $f"
    }
}
Write-Host 'Cleanup done.' -ForegroundColor Green
