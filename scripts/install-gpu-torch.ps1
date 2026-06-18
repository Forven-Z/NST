# GPU PyTorch for hospital-ai (CUDA 12.8, supports RTX 50xx / sm_120)
# Usage: powershell -ExecutionPolicy Bypass -File scripts\install-gpu-torch.ps1
$ErrorActionPreference = "Stop"
$env:PIP_DEFAULT_TIMEOUT = "1800"
$env:PYTHONUTF8 = "1"

$AiDir = "C:\Neuedu\NST-work\hospital-ai"
$Pip = Join-Path $AiDir ".venv\Scripts\pip.exe"
$Py = Join-Path $AiDir ".venv\Scripts\python.exe"
$Mirror = "https://pypi.tuna.tsinghua.edu.cn/simple"
$TorchIndex = "https://download.pytorch.org/whl/cu128"

Set-Location $AiDir

Write-Host "=== NOTE ===" -ForegroundColor Yellow
Write-Host "RTX 5060/50xx needs cu128 (not cu124). Stop hospital-ai on :8000 first."
Write-Host ""

Write-Host "[1/4] Installing torch+cu128 (~2.5GB, may use cache)..." -ForegroundColor Cyan
& $Pip install --default-timeout=1800 torch `
    --index-url $TorchIndex `
    --no-deps --force-reinstall

Write-Host ""
Write-Host "[2/4] Installing torchvision+cu128 (MUST use cu128 index, NOT Tsinghua)..." -ForegroundColor Cyan
& $Pip install --default-timeout=1800 torchvision `
    --index-url $TorchIndex `
    --no-deps --force-reinstall

Write-Host ""
Write-Host "[3/4] Installing torch deps from Tsinghua mirror..." -ForegroundColor Cyan
& $Pip install --default-timeout=600 `
    -i $Mirror `
    sympy fsspec networkx jinja2 typing-extensions filelock

Write-Host ""
Write-Host "[4/4] Installing hospital-ai requirements..." -ForegroundColor Cyan
& $Pip install --default-timeout=600 -i $Mirror -r requirements.txt

Write-Host ""
Write-Host "=== Verify ===" -ForegroundColor Green
& $Py -c "import torch; print('torch', torch.__version__); print('cuda_available', torch.cuda.is_available()); print('gpu', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NONE'); x=torch.randn(2,2,device='cuda'); print('tensor_ok', x.device)"

Write-Host ""
Write-Host "Done. Expected: torch *+cu128, cuda_available True, tensor_ok cuda:0" -ForegroundColor Green
