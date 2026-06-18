# 一键启动智慧云脑诊疗平台（Nacos + 全部 Java 微服务 + PC 前端）
# 版本：v1.2 | 2026-06-15
#
# Usage:
#   .\scripts\start-project.ps1                      # 默认 local（本机 PG/MinIO）+ 启前端
#   .\scripts\start-project.ps1 -EnvProfile cloud    # 阿里云 ECS 库
#   .\scripts\start-project.ps1 -SkipBuild           # 跳过 mvn package
#   .\scripts\start-project.ps1 -SkipFrontend        # 只启后端
#   .\scripts\start-project.ps1 -Restart             # 先 stop-project 再启动

param(
    [switch]$SkipBuild,
    [switch]$SkipFrontend,
    [switch]$Restart,
    [ValidateSet('local', 'cloud')]
    [string]$EnvProfile = 'local',
    [string]$NacosHome = 'D:\dev\nacos',
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
$frontend = Join-Path $RepoRoot 'hospital-frontend'
$logs = Join-Path $RepoRoot 'logs\project'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

$mvnModules = 'hospital-auth,hospital-his,hospital-lis,hospital-pacs,hospital-disposal,hospital-management,hospital-ai-bridge,hospital-gateway'
$serviceChain = @(
    @{ Name = 'hospital-auth'; Port = 9101; Module = 'hospital-auth' },
    @{ Name = 'hospital-management'; Port = 9107; Module = 'hospital-management' },
    @{ Name = 'hospital-his'; Port = 9102; Module = 'hospital-his' },
    @{ Name = 'hospital-lis'; Port = 9103; Module = 'hospital-lis' },
    @{ Name = 'hospital-pacs'; Port = 9104; Module = 'hospital-pacs' },
    @{ Name = 'hospital-disposal'; Port = 9105; Module = 'hospital-disposal' },
    @{ Name = 'hospital-ai-bridge'; Port = 9106; Module = 'hospital-ai-bridge' },
    @{ Name = 'hospital-gateway'; Port = 9000; Module = 'hospital-gateway' }
)

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

function Wait-GatewayReady($seconds) {
    if (-not $seconds) { $seconds = 90 }
    $url = 'http://127.0.0.1:9000/api/v1/auth/staff/login'
    $body = '{"username":"doctor01","password":"123456"}'
    Write-Host '... waiting Gateway + auth (staff login)' -ForegroundColor Yellow
    for ($i = 0; $i -lt $seconds; $i++) {
        try {
            $r = Invoke-RestMethod -Uri $url -Method POST -ContentType 'application/json' -Body $body -TimeoutSec 5
            if ($r.code -eq 200 -and $r.data.accessToken) {
                Write-Host 'OK  Gateway ready (doctor01 login)' -ForegroundColor Green
                return $true
            }
        } catch {
            $status = $null
            if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
            if ($status -and $status -ne 503 -and $status -ne 502) {
                Write-Host 'OK  Gateway reachable' -ForegroundColor Green
                return $true
            }
        }
        Start-Sleep -Seconds 1
    }
    Write-Host 'FAIL  Gateway not ready - see logs/project/hospital-gateway.log' -ForegroundColor Red
    return $false
}

function Start-ServiceJar($name, $port, $module) {
    if (Test-Port $port) {
        Write-Host "OK  $name already on $port" -ForegroundColor Green
        return
    }
    $targetDir = Join-Path $backend "$module\target"
    $jar = Get-ChildItem $targetDir -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } |
        Select-Object -First 1
    if (-not $jar) {
        throw "Jar not found for $module - run without -SkipBuild"
    }
    $logFile = Join-Path $logs "$name.log"
    Write-Host "... starting $name on $port" -ForegroundColor Yellow
    $arg = "/c java -jar `"$($jar.FullName)`" > `"$logFile`" 2>&1"
    Start-Process -FilePath 'cmd.exe' -ArgumentList $arg -WindowStyle Hidden
    if (-not (Wait-Port $port $name 120)) {
        Write-Host "     log: $logFile" -ForegroundColor Yellow
        throw "$name failed to start"
    }
}

function Start-FrontendDev {
    if (-not (Test-Path (Join-Path $frontend 'package.json'))) {
        Write-Host 'WARN  hospital-frontend not found, skip frontend' -ForegroundColor Yellow
        return
    }
    if (Test-Port 5173) {
        Write-Host 'OK  frontend already on 5173' -ForegroundColor Green
        return
    }
    if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
        Write-Host '... npm install (first time)' -ForegroundColor Yellow
        Push-Location $frontend
        npm install
        if ($LASTEXITCODE -ne 0) {
            Pop-Location
            throw 'npm install failed'
        }
        Pop-Location
    }
    Write-Host '... starting PC frontend (new window)' -ForegroundColor Yellow
    $cmd = "Set-Location -LiteralPath '$frontend'; npm run dev"
    Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoExit', '-Command', $cmd
    Start-Sleep -Seconds 3
    if (Test-Port 5173) {
        Write-Host 'OK  frontend http://localhost:5173' -ForegroundColor Green
    } else {
        Write-Host 'WARN  frontend starting - check the new PowerShell window' -ForegroundColor Yellow
    }
}

Write-Host '========================================' -ForegroundColor Cyan
Write-Host " NST one-click start ($EnvProfile)" -ForegroundColor Cyan
Write-Host " DB_HOST=$env:DB_HOST" -ForegroundColor Cyan
Write-Host ' Gateway -> http://127.0.0.1:9000/api/v1' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan

if ($Restart) {
    $stopScript = Join-Path $PSScriptRoot 'stop-project.ps1'
    if (Test-Path $stopScript) {
        & $stopScript
        Start-Sleep -Seconds 2
    }
}

if ($EnvProfile -eq 'local') {
    if (-not (Test-Port 5432)) {
        Write-Host 'FAIL  local PostgreSQL 5432 not listening' -ForegroundColor Red
        Write-Host '      start postgresql-x64-16 service or use -EnvProfile cloud' -ForegroundColor Yellow
        exit 1
    }
    Write-Host 'OK  local PostgreSQL 5432' -ForegroundColor Green
    if (-not (Test-Port 9001)) {
        Write-Host 'WARN  local MinIO 9001 not listening - PACS/imaging may be unavailable' -ForegroundColor Yellow
    }
} else {
    Write-Host "OK  cloud DB via DB_HOST=$env:DB_HOST" -ForegroundColor Green
}

if (-not (Test-Port 8848)) {
    $startup = Join-Path $NacosHome 'bin\startup.cmd'
    if (-not (Test-Path $startup)) {
        Write-Host "FAIL  Nacos not found at $startup" -ForegroundColor Red
        exit 1
    }
    Write-Host '... starting Nacos' -ForegroundColor Yellow
    $nacosBin = Join-Path $NacosHome 'bin'
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', 'startup.cmd', '-m', 'standalone' -WorkingDirectory $nacosBin -WindowStyle Minimized
    if (-not (Wait-Port 8848 'Nacos' 120)) { exit 1 }
} else {
    Write-Host 'OK  Nacos 8848 already running' -ForegroundColor Green
}

if (-not $SkipBuild) {
    Write-Host "... mvn package ($mvnModules)" -ForegroundColor Yellow
    Push-Location $backend
    mvn -q -pl $mvnModules -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Pop-Location
        Write-Host 'FAIL  Maven build' -ForegroundColor Red
        exit 1
    }
    Pop-Location
    Write-Host 'OK  Maven package done' -ForegroundColor Green
}

try {
    foreach ($svc in $serviceChain) {
        Start-ServiceJar $svc.Name $svc.Port $svc.Module
    }
    if (-not (Wait-GatewayReady 90)) { exit 1 }
    if (-not $SkipFrontend) {
        Start-FrontendDev
    }
} catch {
    Write-Host "FAIL  $($_.Exception.Message)" -ForegroundColor Red
    Write-Host '      stop: .\scripts\stop-project.ps1' -ForegroundColor Yellow
    exit 1
}

Write-Host ''
Write-Host '========================================' -ForegroundColor Green
Write-Host ' Project ready' -ForegroundColor Green
Write-Host ' PC:      http://localhost:5173' -ForegroundColor Green
Write-Host ' API:     http://127.0.0.1:9000/api/v1' -ForegroundColor Green
Write-Host ' Login:   doctor01 / 123456' -ForegroundColor Green
Write-Host ' Stop:    .\scripts\stop-project.ps1' -ForegroundColor Green
Write-Host ' LB demo: .\scripts\start-his-replica.ps1 (after start, see RUNBOOK A.1)' -ForegroundColor Green
Write-Host ' Logs:    logs/project' -ForegroundColor Green
Write-Host '========================================' -ForegroundColor Green
