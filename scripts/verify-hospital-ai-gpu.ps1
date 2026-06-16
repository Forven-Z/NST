# Verify hospital-ai GPU PyTorch and optional health endpoint
$ErrorActionPreference = "Continue"
$Py = "C:\Neuedu\NST-work\hospital-ai\.venv\Scripts\python.exe"

Write-Host "=== torch / CUDA ===" -ForegroundColor Cyan
& $Py -c @"
import torch
print('torch', torch.__version__)
print('cuda_available', torch.cuda.is_available())
if torch.cuda.is_available():
    print('gpu', torch.cuda.get_device_name(0))
else:
    print('gpu', 'NONE')
"@

Write-Host ""
Write-Host "=== hospital-ai health (if service is up) ===" -ForegroundColor Cyan
try {
    $h = Invoke-RestMethod -Uri "http://127.0.0.1:8000/v1/health" -TimeoutSec 5
    $h | ConvertTo-Json -Compress
} catch {
    Write-Host "hospital-ai not running on :8000 (start scripts\start-r-pacs-ai.bat after GPU ok)"
}
