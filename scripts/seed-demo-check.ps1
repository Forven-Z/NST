# Seed demo check requests #62001 (head CT) and #62002 (chest CT)
param(
    [string]$DbHost = 'localhost',
    [string]$DbName = 'hospital',
    [string]$DbUser = 'postgres',
    [string]$DbPassword = 'postgres'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sql = Join-Path $root 'docs\sql\seed-demo-check.sql'

function Resolve-PsqlPath {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($candidate in @(
            'C:\Program Files\PostgreSQL\16\bin\psql.exe',
            'C:\Program Files\PostgreSQL\15\bin\psql.exe'
        )) {
        if (Test-Path $candidate) { return $candidate }
    }
    throw 'psql not found. Add PostgreSQL\bin to PATH or install PostgreSQL 16.'
}

$psql = Resolve-PsqlPath

if (-not (Test-Path $sql)) {
    throw "SQL file not found: $sql"
}

$env:PGPASSWORD = $DbPassword
Write-Host "Seeding demo check_request #62001 / #62002 -> ${DbName}@${DbHost} ..."
& $psql -U $DbUser -h $DbHost -d $DbName -f $sql
if ($LASTEXITCODE -ne 0) {
    throw "seed-demo-check.sql failed (exit $LASTEXITCODE)"
}
Write-Host 'Done. Set VITE_USE_MOCK=false in hospital-frontend/.env.development and restart npm run dev.' -ForegroundColor Green
