# 一键停止 start-project.ps1 启动的后端与前端
# 版本：v1.0 | 2026-06-15
# Usage: .\scripts\stop-project.ps1

param(
    [int[]]$Ports = @(9000, 9101, 9102, 9103, 9104, 9105, 9106, 9107, 5173)
)

$ErrorActionPreference = 'Continue'

function Stop-PortListeners($port) {
    $pids = @()
    try {
        $pids = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    } catch {
        # fallback below
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
Write-Host ' Stop project (Java + frontend)' -ForegroundColor Cyan
Write-Host ' Nacos not stopped — use D:\dev\nacos\bin\shutdown.cmd if needed' -ForegroundColor DarkGray
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
} else {
    Write-Host 'OK  project stopped' -ForegroundColor Green
}
