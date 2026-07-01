# 本地开发环境变量（PostgreSQL / MinIO 在本机）— start-project 默认 profile
# 版本：v1.1 | 2026-06-15
# 用法（当前 PowerShell 窗口有效）：
#   . .\scripts\env-local.ps1
# 或在 start-project.ps1 中： .\scripts\start-project.ps1 -EnvProfile local

$script:HospitalEnvProfile = 'local'

$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5432'
$env:DB_NAME = 'hospital'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = '123456'

$env:MINIO_ENDPOINT = 'http://127.0.0.1:9001'
$env:MINIO_ACCESS_KEY = 'minioadmin'
$env:MINIO_SECRET_KEY = 'minioadmin123'
$env:MINIO_BUCKET = 'imaging'

$env:NACOS_SERVER_ADDR = '127.0.0.1:8848'
$env:HOSPITAL_AI_BASE_URL = 'http://127.0.0.1:8000'
$env:HOSPITAL_AI_CALLBACK_URL = 'http://127.0.0.1:9104/internal/imaging/callback'

Write-Host '[env-local] DB/MinIO -> 127.0.0.1 | Nacos -> 127.0.0.1:8848' -ForegroundColor Cyan
