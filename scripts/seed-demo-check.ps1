# 灌入 CNN 演示检查单（赵大爷 · 头部 CT #62001）
param(
    [string]$DbHost = 'localhost',
    [string]$DbName = 'hospital',
    [string]$DbUser = 'postgres',
    [string]$DbPassword = 'postgres'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sql = Join-Path $root 'docs\sql\seed-demo-check.sql'

if (-not (Test-Path $sql)) {
    Write-Error "找不到 $sql"
}

$env:PGPASSWORD = $DbPassword
Write-Host "Seeding demo check_request #62001 -> $DbName@$DbHost ..."
& psql -U $DbUser -h $DbHost -d $DbName -f $sql
if ($LASTEXITCODE -ne 0) {
    Write-Error "seed-demo-check.sql 执行失败（exit $LASTEXITCODE）"
}
Write-Host "完成。请确认 hospital-frontend .env 中 VITE_USE_MOCK=false 并重启 npm run dev。" -ForegroundColor Green
