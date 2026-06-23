# 一键启动智慧云脑诊疗平台（Nacos + MinIO + 全部 Java 微服务 + PC 前端）
# 版本：v1.3 | 2026-06-15
#
# Usage:
#   .\scripts\start-project.ps1                      # 默认 local（本机 PG/MinIO）+ 启前端
#   .\scripts\start-project.ps1 -EnvProfile cloud    # 阿里云 ECS 库/MinIO（仅探测远程）
#   .\scripts\start-project.ps1 -SkipBuild           # 跳过 mvn package
#   .\scripts\start-project.ps1 -SkipFrontend        # 只启后端
#   .\scripts\start-project.ps1 -SkipMinio           # 不测影像时可跳过 MinIO
#   .\scripts\start-project.ps1 -Restart             # 先 stop-project 再启动

param(
    [switch]$SkipBuild,
    [switch]$SkipFrontend,
    [switch]$SkipMinio,
    [switch]$Restart,
    [ValidateSet('local', 'cloud')]
    [string]$EnvProfile = 'local',
    [string]$NacosHome = 'D:\dev\nacos',
    [string]$MinioHome = 'D:\dev\minio',
    [string]$MinioData = 'D:\dev\minio-data',
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

function Wait-NacosReady($seconds) {
    if (-not $seconds) { $seconds = 120 }
    $url = 'http://127.0.0.1:8848/nacos/v1/console/health/readiness'
    Write-Host '... waiting Nacos readiness (HTTP + gRPC)' -ForegroundColor Yellow
    for ($i = 0; $i -lt $seconds; $i++) {
        try {
            $r = Invoke-RestMethod -Uri $url -TimeoutSec 3
            if ($r -eq 'OK') {
                Start-Sleep -Seconds 2
                Write-Host 'OK  Nacos ready' -ForegroundColor Green
                return $true
            }
        } catch {
            # Nacos still starting
        }
        Start-Sleep -Seconds 1
    }
    Write-Host 'WARN  Nacos readiness timeout — services may retry registration' -ForegroundColor Yellow
    return $false
}

function Wait-MinioHealth($endpoint, $label, $seconds) {
    if (-not $seconds) { $seconds = 60 }
    $base = $endpoint.TrimEnd('/')
    $url = "$base/minio/health/live"
    Write-Host "... waiting MinIO health ($label)" -ForegroundColor Yellow
    for ($i = 0; $i -lt $seconds; $i++) {
        try {
            $resp = Invoke-WebRequest -Uri $url -TimeoutSec 3 -UseBasicParsing
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 300) {
                Write-Host "OK  MinIO ready ($label)" -ForegroundColor Green
                return $true
            }
        } catch {
            # MinIO still starting or unreachable
        }
        Start-Sleep -Seconds 1
    }
    Write-Host "WARN  MinIO not ready after ${seconds}s ($label)" -ForegroundColor Yellow
    return $false
}

function Resolve-LocalMinioInstall {
    $candidates = @(
        @{ Exe = Join-Path $MinioHome 'minio.exe'; License = Join-Path $MinioHome 'minio.license'; Community = $false },
        @{ Exe = Join-Path $MinioHome 'minio-community.exe'; License = $null; Community = $true },
        @{ Exe = 'C:\dev\minio\minio-community.exe'; License = $null; Community = $true }
    )
    foreach ($item in $candidates) {
        if (-not (Test-Path $item.Exe)) { continue }
        if (-not $item.Community -and -not (Test-Path $item.License)) { continue }
        return $item
    }
    return $null
}

function Start-LocalMinio {
    if (Test-Port 9001) {
        Write-Host 'OK  local MinIO 9001 already running' -ForegroundColor Green
        return (Wait-MinioHealth 'http://127.0.0.1:9001' 'local' 15)
    }

    $install = Resolve-LocalMinioInstall
    if (-not $install) {
        Write-Host 'WARN  local MinIO not found — install under D:\dev\minio (see DEV_ENV_SETUP §6.3)' -ForegroundColor Yellow
        Write-Host '      need minio.exe + minio.license, or minio-community.exe' -ForegroundColor Yellow
        return $false
    }

    if (-not (Test-Path $MinioData)) {
        New-Item -ItemType Directory -Force -Path $MinioData | Out-Null
    }

    $env:MINIO_ROOT_USER = 'minioadmin'
    $env:MINIO_ROOT_PASSWORD = 'minioadmin123'
    $args = @(
        'server', $MinioData,
        '--address', ':9001',
        '--console-address', ':9002'
    )
    if (-not $install.Community) {
        $args += @('--license', $install.License)
    }

    Write-Host "... starting local MinIO ($($install.Exe))" -ForegroundColor Yellow
    Start-Process -FilePath $install.Exe -ArgumentList $args -WindowStyle Minimized
    if (-not (Wait-Port 9001 'MinIO' 30)) { return $false }
    return (Wait-MinioHealth 'http://127.0.0.1:9001' 'local' 30)
}

function Ensure-MinioReady {
    if ($SkipMinio) {
        Write-Host 'SKIP MinIO (-SkipMinio)' -ForegroundColor Yellow
        return
    }

    if ($EnvProfile -eq 'local') {
        if (-not (Start-LocalMinio)) {
            Write-Host 'WARN  local MinIO unavailable — PACS/imaging may fail (use -SkipMinio to silence)' -ForegroundColor Yellow
        }
        return
    }

    $endpoint = if ($env:MINIO_ENDPOINT) { $env:MINIO_ENDPOINT } else { 'http://127.0.0.1:9001' }
    if (Wait-MinioHealth $endpoint "cloud $endpoint" 30) {
        return
    }
    Write-Host 'WARN  cloud MinIO unreachable — start on ECS: docker-compose up -d minio (see RUNBOOK §4.5)' -ForegroundColor Yellow
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
Write-Host " MINIO=$env:MINIO_ENDPOINT" -ForegroundColor Cyan
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
} else {
    Write-Host "OK  cloud DB via DB_HOST=$env:DB_HOST" -ForegroundColor Green
}

Ensure-MinioReady

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
Wait-NacosReady 60 | Out-Null

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
        if ($svc.Name -eq 'hospital-ai-bridge') {
            Start-Sleep -Seconds 3
        }
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
