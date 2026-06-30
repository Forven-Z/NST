# Start hospital-ai (Python CNN / FastAPI :8000)
# Version: v1.1 | 2026-06-04
#
# Usage:
#   .\scripts\start-hospital-ai.ps1
#   .\scripts\start-hospital-ai.ps1 -Restart
#
# First time: powershell -ExecutionPolicy Bypass -File scripts/setup-hospital-ai.ps1
# Full stack: start-project.ps1 (pacs :9104) then this script for imaging CNN

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
$ErrFile = Join-Path $Logs 'hospital-ai.err.log'
$HealthWaitSeconds = 180

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
    if (-not $seconds) { $seconds = $HealthWaitSeconds }
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
    Write-Host "FAIL  hospital-ai not ready after ${seconds}s" -ForegroundColor Red
    Write-Host "      stdout log: $LogFile" -ForegroundColor Yellow
    Write-Host "      stderr log: $ErrFile" -ForegroundColor Yellow
    if (Test-Path $ErrFile) {
        Get-Content $ErrFile -Tail 15 | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
    }
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
    Write-Host 'FAIL  .venv not found - run setup first:' -ForegroundColor Red
    Write-Host '      powershell -ExecutionPolicy Bypass -File scripts/setup-hospital-ai.ps1' -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path (Join-Path $AiDir 'model\weights\best.pth'))) {
    Write-Host 'WARN  model/weights/best.pth missing - CNN inference may fail' -ForegroundColor Yellow
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
if (Test-Path $LogFile) { Remove-Item $LogFile -Force -ErrorAction SilentlyContinue }
if (Test-Path $ErrFile) { Remove-Item $ErrFile -Force -ErrorAction SilentlyContinue }
# Start python directly; redirect stdout/stderr (cmd.exe redirect via Start-Process is unreliable)
Start-Process -FilePath $VenvPy `
    -ArgumentList @('-u', '-m', 'uvicorn', 'app.main:app', '--host', $ListenHost, '--port', "$Port") `
    -WorkingDirectory $AiDir `
    -WindowStyle Hidden `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError $ErrFile

if (-not (Wait-Health $HealthWaitSeconds)) {
    exit 1
}

Write-Host ''
Write-Host '========================================' -ForegroundColor Green
Write-Host ' hospital-ai ready' -ForegroundColor Green
Write-Host " Health:  http://127.0.0.1:$Port/v1/health" -ForegroundColor Green
Write-Host ' Stop:    .\scripts\stop-hospital-ai.ps1' -ForegroundColor Green
Write-Host " Log:     $LogFile (+ $ErrFile)" -ForegroundColor Green
Write-Host ' Tip:     for imaging CNN, also run start-project.ps1 (pacs :9104 + MinIO)' -ForegroundColor DarkGray
Write-Host '========================================' -ForegroundColor Green
