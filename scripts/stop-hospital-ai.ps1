# Stop hospital-ai (Python CNN on :8000)
# Version: v1.1 | 2026-06-04
# Usage: .\scripts\stop-hospital-ai.ps1

param(
    [int]$Port = 8000
)

$ErrorActionPreference = 'Continue'

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
            Write-Host "Stopping port $p PID=$procId"
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host '========================================' -ForegroundColor Cyan
Write-Host " Stop hospital-ai (port $Port)" -ForegroundColor Cyan
Write-Host ' Java / frontend not affected - use stop-project.ps1' -ForegroundColor DarkGray
Write-Host '========================================' -ForegroundColor Cyan

Stop-PortListeners $Port
Start-Sleep -Seconds 2

$listening = netstat -ano 2>$null | Select-String ":$Port\s" | Select-String 'LISTENING'
if ($listening) {
    Write-Host "WARN  port $Port still listening" -ForegroundColor Yellow
} else {
    Write-Host 'OK  hospital-ai stopped' -ForegroundColor Green
}
