# 云端展示环境变量（PostgreSQL / MinIO 在阿里云 ECS）— 需显式 -EnvProfile cloud
# 版本：v1.1 | 2026-06-15
# 用法（当前 PowerShell 窗口有效）：
#   . .\scripts\env-cloud.ps1
# 或在 start-project.ps1 中： .\scripts\start-project.ps1 -EnvProfile cloud
#
# ECS 公网 IP（运维修改此处即可）
$script:HospitalCloudHost = '123.57.206.134'

$script:HospitalEnvProfile = 'cloud'

$env:DB_HOST = $script:HospitalCloudHost
$env:DB_PORT = '5432'
$env:DB_NAME = 'hospital'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = 'K7m6baizhou666'

$env:MINIO_ENDPOINT = "http://$($script:HospitalCloudHost):9001"
$env:MINIO_ACCESS_KEY = 'minioadmin'
$env:MINIO_SECRET_KEY = 'minioadmin123'
$env:MINIO_BUCKET = 'imaging'

$env:NACOS_SERVER_ADDR = '127.0.0.1:8848'
$env:HOSPITAL_AI_BASE_URL = 'http://127.0.0.1:8000'
$env:HOSPITAL_AI_CALLBACK_URL = 'http://127.0.0.1:9104/internal/imaging/callback'

Write-Host "[env-cloud] DB/MinIO -> $script:HospitalCloudHost | Nacos -> 127.0.0.1:8848" -ForegroundColor Cyan
