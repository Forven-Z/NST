# 在 start-project 已启动后，追加第二个 hospital-his 实例（演示 Nacos + Gateway 负载均衡）
# 版本：v1.0 | 2026-06-15
#
# 前提：Nacos :8848、Gateway :9000、hospital-his 主实例 :9102 已运行（通常先 .\scripts\start-project.ps1）
#
# Usage:
#   .\scripts\start-his-replica.ps1
#   .\scripts\start-his-replica.ps1 -EnvProfile cloud
#   .\scripts\start-his-replica.ps1 -ReplicaPort 9202

param(
    [ValidateSet('local', 'cloud')]
    [string]$EnvProfile = 'local',
    [int]$PrimaryPort = 9102,
    [int]$ReplicaPort = 9202,
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$envScript = Join-Path $PSScriptRoot "env-$EnvProfile.ps1"
if (-not (Test-Path $envScript)) {
    Write-Host "FAIL  env script not found: $envScript" -ForegroundColor Red
    exit 1
}
. $envScript

$ErrorActionPreference = 'Stop'
$backend = Join-Path $RepoRoot 'hospital-backend'
$logs = Join-Path $RepoRoot 'logs\project'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Test-Port($port) {
    $lines = netstat -ano 2>$null | Out-String
    return $lines -match ":$port\s" -and $lines -match 'LISTENING'
}

function Wait-Port($port, $label, $seconds) {
    if (-not $seconds) { $seconds = 120 }
    for ($i = 0; $i -lt $seconds; $i++) {
        if (Test-Port $port) {
            Write-Host "OK  $label listening on $port" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 1
    }
    Write-Host "FAIL  $label not up after ${seconds}s (port $port)" -ForegroundColor Red
    return $false
}

Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' hospital-his replica (load balance demo)' -ForegroundColor Cyan
Write-Host " EnvProfile=$EnvProfile  primary=$PrimaryPort  replica=$ReplicaPort" -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan

if (-not (Test-Port 8848)) {
    Write-Host 'FAIL  Nacos 8848 not listening — run start-project.ps1 first' -ForegroundColor Red
    exit 1
}
Write-Host 'OK  Nacos 8848' -ForegroundColor Green

if (-not (Test-Port $PrimaryPort)) {
    Write-Host "FAIL  hospital-his primary not on $PrimaryPort — run start-project.ps1 first" -ForegroundColor Red
    exit 1
}
Write-Host "OK  hospital-his primary on $PrimaryPort" -ForegroundColor Green

if (-not (Test-Port 9000)) {
    Write-Host 'WARN  Gateway 9000 not listening — LB demo needs Gateway' -ForegroundColor Yellow
} else {
    Write-Host 'OK  Gateway 9000' -ForegroundColor Green
}

if (Test-Port $ReplicaPort) {
    Write-Host "OK  replica already on $ReplicaPort (Nacos hospital-his should show 2 instances)" -ForegroundColor Green
    exit 0
}

$targetDir = Join-Path $backend 'hospital-his\target'
$jar = Get-ChildItem $targetDir -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Select-Object -First 1
if (-not $jar) {
    Write-Host 'FAIL  hospital-his jar not found — run start-project.ps1 without -SkipBuild once' -ForegroundColor Red
    exit 1
}

$logFile = Join-Path $logs "hospital-his-replica-$ReplicaPort.log"
Write-Host "... starting hospital-his replica on $ReplicaPort" -ForegroundColor Yellow
$javaArgs = "/c java -jar `"$($jar.FullName)`" --server.port=$ReplicaPort > `"$logFile`" 2>&1"
Start-Process -FilePath 'cmd.exe' -ArgumentList $javaArgs -WindowStyle Hidden

if (-not (Wait-Port $ReplicaPort 'hospital-his-replica' 120)) {
    Write-Host "     log: $logFile" -ForegroundColor Yellow
    exit 1
}

Write-Host ''
Write-Host '========================================' -ForegroundColor Green
Write-Host ' HIS replica ready' -ForegroundColor Green
Write-Host " Primary: 127.0.0.1:$PrimaryPort" -ForegroundColor Green
Write-Host " Replica: 127.0.0.1:$ReplicaPort" -ForegroundColor Green
Write-Host ' Nacos:   hospital-his instance count should be 2' -ForegroundColor Green
Write-Host ' Demo:    call API via Gateway :9000 (not direct 9102/9202)' -ForegroundColor Green
Write-Host " Logs:    $logFile" -ForegroundColor Green
Write-Host ' Stop:    .\scripts\stop-his-replica.ps1' -ForegroundColor Green
Write-Host '========================================' -ForegroundColor Green
