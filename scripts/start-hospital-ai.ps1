# 单独启动 hospital-ai（Python CNN · FastAPI :8000）
# 版本：v1.0 | 2026-06-15
#
# Usage:
#   .\scripts\start-hospital-ai.ps1
#   .\scripts\start-hospital-ai.ps1 -Restart
#
# 首次请先：powershell -ExecutionPolicy Bypass -File scripts/setup-hospital-ai.ps1
# 配合全栈：先 start-project.ps1（含 pacs :9104），再本脚本

param(
    [switch]$Restart,
    [string]$ListenHost = '0.0.0.0',
    [int]$Port = 8000,
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$AiDir = Join-Path $RepoRoot 'hospital-ai'
$VenvPy = Join-Path $AiDir '.venv\Scripts\python.exe'
$Logs = Join-Path $RepoRoot 'logs\hospital-ai'
$LogFile = Join-Path $Logs 'hospital-ai.log'

function Test-Port($p) {
    $lines = netstat -ano 2>$null | Out-String
    return $lines -match ":$p\s" -and $lines -match 'LISTENING'
}

function Stop-PortListeners($p) {
    $pids = @()
    try {
        $pids = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    } catch {
        # fallback below
    }
    if (-not $pids) {
        $pids = netstat -ano | Select-String ":$p\s" | Select-String 'LISTENING' | ForEach-Object {
            ($_.Line -split '\s+')[-1]
        } | Select-Object -Unique
    }
    foreach ($procId in $pids) {
        if ($procId -match '^\d+$' -and [int]$procId -gt 0) {
            Write-Host "Stopping port $p PID=$procId" -ForegroundColor Yellow
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

function Wait-Health($seconds) {
    if (-not $seconds) { $seconds = 120 }
    $url = "http://127.0.0.1:$Port/v1/health"
    Write-Host "... waiting hospital-ai $url" -ForegroundColor Yellow
    for ($i = 0; $i -lt $seconds; $i++) {
        try {
            $r = Invoke-RestMethod -Uri $url -Method GET -TimeoutSec 5
            if ($r.status -eq 'UP' -or $r.device) {
                $device = if ($r.device) { $r.device } else { 'unknown' }
                Write-Host "OK  hospital-ai UP (device: $device)" -ForegroundColor Green
                return $true
            }
        } catch {
            # still starting
        }
        Start-Sleep -Seconds 1
    }
    Write-Host "FAIL  hospital-ai not ready after ${seconds}s — see $LogFile" -ForegroundColor Red
    return $false
}

Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' hospital-ai (Python CNN) start' -ForegroundColor Cyan
Write-Host " http://127.0.0.1:$Port/v1/health" -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan

if (-not (Test-Path $AiDir)) {
    Write-Host "FAIL  directory not found: $AiDir" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $VenvPy)) {
    Write-Host 'FAIL  .venv not found — run setup first:' -ForegroundColor Red
    Write-Host '      powershell -ExecutionPolicy Bypass -File scripts/setup-hospital-ai.ps1' -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path (Join-Path $AiDir 'model\weights\best.pth'))) {
    Write-Host 'WARN  model/weights/best.pth missing — CNN inference may fail' -ForegroundColor Yellow
}

New-Item -ItemType Directory -Force -Path $Logs | Out-Null

if ($Restart -and (Test-Port $Port)) {
    Stop-PortListeners $Port
    Start-Sleep -Seconds 2
}

if (Test-Port $Port) {
    Write-Host "OK  hospital-ai already listening on $Port" -ForegroundColor Green
    exit 0
}

Write-Host "... starting uvicorn on $Port (log: $LogFile)" -ForegroundColor Yellow
$arg = "/c `"$VenvPy`" -m uvicorn app.main:app --host $ListenHost --port $Port > `"$LogFile`" 2>&1"
Start-Process -FilePath 'cmd.exe' -ArgumentList $arg -WorkingDirectory $AiDir -WindowStyle Hidden

if (-not (Wait-Health 120)) {
    exit 1
}

Write-Host ''
Write-Host '========================================' -ForegroundColor Green
Write-Host ' hospital-ai ready' -ForegroundColor Green
Write-Host " Health:  http://127.0.0.1:$Port/v1/health" -ForegroundColor Green
Write-Host ' Stop:    .\scripts\stop-hospital-ai.ps1' -ForegroundColor Green
Write-Host " Log:     $LogFile" -ForegroundColor Green
Write-Host ' Tip:     start-project.ps1 已含 pacs :9104 时可联调影像 CNN' -ForegroundColor DarkGray
Write-Host '========================================' -ForegroundColor Green
