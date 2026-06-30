# Stop R-min backend (gateway / auth / his)
# Usage: .\scripts\stop-r-min.ps1

param(
    [int[]]$Ports = @(9000, 9101, 9102, 9106)
)

$ErrorActionPreference = 'Continue'

function Stop-PortListeners($port) {
    $pids = @()
    try {
        $pids = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    } catch {
        # fallback
    }
    if (-not $pids) {
        $pids = netstat -ano | Select-String ":$port\s" | Select-String 'LISTENING' | ForEach-Object {
            ($_.Line -split '\s+')[-1]
        } | Select-Object -Unique
    }
    foreach ($procId in $pids) {
        if ($procId -match '^\d+$' -and [int]$procId -gt 0) {
            Write-Host "Stopping port $port PID=$procId"
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' Stop R-min (9000 / 9101 / 9102 / 9106)' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan

foreach ($port in $Ports) {
    Stop-PortListeners $port
}

Start-Sleep -Seconds 2

$stillUp = @()
foreach ($port in $Ports) {
    $listening = netstat -ano 2>$null | Select-String ":$port\s" | Select-String 'LISTENING'
    if ($listening) { $stillUp += $port }
}

if ($stillUp.Count -gt 0) {
    Write-Host "WARN  ports still listening: $($stillUp -join ', ')" -ForegroundColor Yellow
    Write-Host '      kill java.exe in Task Manager or retry as admin' -ForegroundColor Yellow
} else {
    Write-Host 'OK  R-min stopped' -ForegroundColor Green
}
