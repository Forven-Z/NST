# Copy shared CNN weights into hospital-ai runtime directory
# Usage: powershell -ExecutionPolicy Bypass -File scripts\install-model-weights.ps1
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$Src = Join-Path $Root 'shared\model-weights'
$Dst = Join-Path $Root 'hospital-ai\model\weights'
New-Item -ItemType Directory -Force -Path $Dst | Out-Null
foreach ($name in @('best.pth', 'lung_artifact_best.pth', 'tumor_seg_best.pth')) {
    $from = Join-Path $Src $name
    if (-not (Test-Path $from)) {
        Write-Host "SKIP: not found $from" -ForegroundColor Yellow
        continue
    }
    Copy-Item $from (Join-Path $Dst $name) -Force
    Write-Host "OK: $name -> hospital-ai\model\weights\" -ForegroundColor Green
}
Write-Host ''
Write-Host 'Done. Run .\scripts\start-hospital-ai.ps1 and check http://127.0.0.1:8000/v1/health' -ForegroundColor Green
