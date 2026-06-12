# R-min local startup for miniapp / PC integration
# Usage: .\scripts\start-r-min.ps1
#        .\scripts\start-r-min.ps1 -SkipBuild

param(
    [switch]$SkipBuild,
    [switch]$Restart,
    [string]$NacosHome = 'D:\dev\nacos',
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$backend = Join-Path $RepoRoot 'hospital-backend'
$logs = Join-Path $RepoRoot 'logs\r-min'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

function Test-Port($port) {
    $lines = netstat -ano 2>$null | Out-String
    return $lines -match ":$port\s" -and $lines -match 'LISTENING'
}

function Wait-Port($port, $label, $seconds) {
    if (-not $seconds) { $seconds = 90 }
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

function Wait-GatewayLoginReady($seconds) {
    if (-not $seconds) { $seconds = 60 }
    $base = 'http://127.0.0.1:9000/api/v1/patient/auth/login'
    $phone = '138' + (Get-Random -Maximum 99999999).ToString('00000000')
    $idCard = '11010119900101' + (Get-Random -Maximum 9999).ToString('0000')
    $body = @{
        realName  = '就绪探测'
        idCard    = $idCard
        gender    = 1
        birthDate = '1990-01-01'
        phone     = $phone
        address   = ''
    } | ConvertTo-Json -Compress

    Write-Host '... waiting Gateway -> HIS (Nacos 注册 + login 可用)' -ForegroundColor Yellow
    for ($i = 0; $i -lt $seconds; $i++) {
        try {
            $r = Invoke-RestMethod -Uri $base -Method POST -ContentType 'application/json' -Body $body -TimeoutSec 5
            if ($r.code -eq 200 -and $r.data.accessToken) {
                Write-Host 'OK  login endpoint ready' -ForegroundColor Green
                return $true
            }
        } catch {
            $status = $null
            if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
            if ($status -ne 503 -and $status -ne 502) {
                # 非网关不可用类错误，说明路由已通
                Write-Host 'OK  login endpoint reachable' -ForegroundColor Green
                return $true
            }
        }
        Start-Sleep -Seconds 1
    }
    Write-Host 'FAIL  login still 503 after ${seconds}s — Gateway 已起但 HIS 未注册到 Nacos' -ForegroundColor Red
    Write-Host '      请运行: .\scripts\stop-r-min.ps1  然后  .\scripts\start-r-min.ps1' -ForegroundColor Yellow
    return $false
}

Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' R-min: auth + his + gateway + ai-bridge' -ForegroundColor Cyan
Write-Host ' Gateway http://127.0.0.1:9000/api/v1' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan

if ($Restart) {
    $stopScript = Join-Path $PSScriptRoot 'stop-r-min.ps1'
    if (Test-Path $stopScript) {
        & $stopScript
    }
}

if (-not (Test-Port 5432)) {
    Write-Host 'FAIL  PostgreSQL 5432 not listening' -ForegroundColor Red
    exit 1
}
Write-Host 'OK  PostgreSQL 5432' -ForegroundColor Green

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
    Write-Host '... mvn package auth his gateway ai-bridge' -ForegroundColor Yellow
    Push-Location $backend
    mvn -q -pl hospital-auth,hospital-his,hospital-gateway -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Pop-Location
        Write-Host 'FAIL  Maven build' -ForegroundColor Red
        exit 1
    }
    Pop-Location
    Write-Host 'OK  Maven package done' -ForegroundColor Green
}

function Start-ServiceJar($name, $port, $module) {
    if (Test-Port $port) {
        Write-Host "OK  $name already on $port" -ForegroundColor Green
        return
    }
    $targetDir = Join-Path $backend "$module\target"
    $jar = Get-ChildItem $targetDir -Filter '*.jar' | Where-Object { $_.Name -notmatch 'sources|javadoc' } | Select-Object -First 1
    if (-not $jar) {
        throw "Jar not found for $module - run mvn package first"
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

try {
    Start-ServiceJar 'hospital-auth' 9101 'hospital-auth'
    Start-ServiceJar 'hospital-his' 9102 'hospital-his'
    Start-ServiceJar 'hospital-ai-bridge' 9106 'hospital-ai-bridge'
    Start-ServiceJar 'hospital-gateway' 9000 'hospital-gateway'
    if (-not (Wait-GatewayLoginReady 90)) { exit 1 }
} catch {
    Write-Host "FAIL  $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host '========================================' -ForegroundColor Green
Write-Host ' R-min ready' -ForegroundColor Green
Write-Host ' Run: .\scripts\miniapp-smoke.ps1' -ForegroundColor Green
Write-Host ' Miniapp: config.local.js USE_MOCK=false' -ForegroundColor Green
Write-Host '========================================' -ForegroundColor Green
