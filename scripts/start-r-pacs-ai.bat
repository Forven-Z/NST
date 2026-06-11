@echo off
rem R-pacs + hospital-ai 本地启动（PG 密码 postgres）
rem 注意：路径含 \1 时 bat 会误解析，故用正斜杠
set "ROOT=C:/Neuedu/1/NST-main"
set "LOG=%ROOT%/logs/r-pacs-ai"
if not exist "%LOG%" mkdir "%LOG%"

set NACOS_SERVER_ADDR=127.0.0.1:8848
set DB_HOST=127.0.0.1
set DB_PORT=5432
set DB_NAME=hospital
set DB_USER=postgres
set DB_PASSWORD=postgres
set MINIO_ENDPOINT=http://127.0.0.1:9001
set MINIO_ACCESS_KEY=minioadmin
set MINIO_SECRET_KEY=minioadmin123
set MINIO_BUCKET=imaging

for %%P in (9101 9107 9102 9104 9000 8000) do (
  for /f "tokens=5" %%A in ('netstat -ano ^| findstr ":%%P " ^| findstr LISTENING') do taskkill /PID %%A /F >nul 2>&1
)
timeout /t 2 /nobreak >nul

start "" /b java -jar "%ROOT%/hospital-backend/hospital-auth/target/hospital-auth-1.0-SNAPSHOT.jar" > "%LOG%/hospital-auth.log" 2>&1
timeout /t 12 /nobreak >nul
start "" /b java -jar "%ROOT%/hospital-backend/hospital-management/target/hospital-management-1.0-SNAPSHOT.jar" > "%LOG%/hospital-management.log" 2>&1
timeout /t 8 /nobreak >nul
start "" /b java -jar "%ROOT%/hospital-backend/hospital-his/target/hospital-his-1.0-SNAPSHOT.jar" > "%LOG%/hospital-his.log" 2>&1
timeout /t 8 /nobreak >nul
start "" /b java -jar "%ROOT%/hospital-backend/hospital-pacs/target/hospital-pacs-1.0-SNAPSHOT.jar" > "%LOG%/hospital-pacs.log" 2>&1
timeout /t 8 /nobreak >nul
start "" /b java -jar "%ROOT%/hospital-backend/hospital-gateway/target/hospital-gateway-1.0-SNAPSHOT.jar" > "%LOG%/hospital-gateway.log" 2>&1
timeout /t 10 /nobreak >nul

cd /d "%ROOT%\hospital-ai"
start "" /b "%ROOT%/hospital-ai/.venv/Scripts/python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8000 > "%LOG%/hospital-ai.log" 2>&1

echo ROOT=%ROOT%
echo Java + hospital-ai starting. Logs: %LOG%
echo 若 CNN 很慢，请先运行: powershell -File "%ROOT%/scripts/setup-hospital-ai.ps1"
echo 健康检查 device 应为 cuda: http://127.0.0.1:8000/v1/health
echo Frontend: cd /d "%ROOT%/hospital-frontend" ^&^& npm run dev
echo Open http://127.0.0.1:5173  login check01 / 123456
