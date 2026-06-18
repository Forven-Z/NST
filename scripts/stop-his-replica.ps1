# 停止 start-his-replica.ps1 启动的 hospital-his 副本（默认 :9202）
# Usage: .\scripts\stop-his-replica.ps1
#        .\scripts\stop-his-replica.ps1 -ReplicaPort 9202

param(
    [int]$ReplicaPort = 9202
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

Write-Host "Stop hospital-his replica (port $ReplicaPort)" -ForegroundColor Cyan
Stop-PortListeners $ReplicaPort
Start-Sleep -Seconds 2

if (netstat -ano 2>$null | Select-String ":$ReplicaPort\s" | Select-String 'LISTENING') {
    Write-Host "WARN  port $ReplicaPort still listening" -ForegroundColor Yellow
} else {
    Write-Host 'OK  his replica stopped (primary :9102 unchanged)' -ForegroundColor Green
}
