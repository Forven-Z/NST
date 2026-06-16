# Install hospital-ai venv with GPU PyTorch (CUDA 12.4)
# Usage: powershell -ExecutionPolicy Bypass -File scripts/setup-hospital-ai.ps1
# Tip: stop hospital-ai (port 8000) before reinstall to avoid WinError 5 file lock
$ErrorActionPreference = "Stop"
$env:PYTHONUTF8 = "1"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$AiDir = Join-Path $Root "hospital-ai"
$VenvPy = Join-Path $AiDir ".venv\Scripts\python.exe"
$VenvPip = Join-Path $AiDir ".venv\Scripts\pip.exe"

Set-Location $AiDir

if (-not (Test-Path $VenvPy)) {
    Write-Host "Creating .venv ..."
    python -m venv .venv
}

Write-Host "Installing GPU PyTorch (cu124) ..."
& $VenvPip install torch --index-url https://download.pytorch.org/whl/cu124 --force-reinstall

Write-Host "Installing other dependencies ..."
& $VenvPip install -r requirements.txt

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Copied .env.example to .env"
}

if (-not (Test-Path "model\weights\best.pth")) {
    Write-Warning "Missing model/weights/best.pth - copy weights before running CNN."
}

Write-Host ""
Write-Host "=== CUDA check ==="
& $VenvPy -c 'import torch; print("torch", torch.__version__); print("cuda_available", torch.cuda.is_available()); print("gpu", torch.cuda.get_device_name(0) if torch.cuda.is_available() else "NONE")'

Write-Host ""
Write-Host "Done. Run: scripts\start-r-pacs-ai.bat"
