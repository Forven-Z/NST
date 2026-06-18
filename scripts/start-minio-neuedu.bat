@echo off
rem 本机 MinIO 启动（需先自备 minio.license，见 docs/DEV_ENV_SETUP.md §6.3.3）
set "ROOT=C:\Neuedu\minio"
set "DATA=C:\Neuedu\minio-data"
set "EXE=C:\Users\w3209\Downloads\minio.exe"

if not exist "%EXE%" (
  echo [错误] 未找到 %EXE%
  echo 请从 min.io 下载 minio.exe 或修改本脚本中的 EXE 路径
  exit /b 1
)
if not exist "%ROOT%\minio.license" (
  echo [错误] 缺少 %ROOT%\minio.license
  echo 请用本人邮箱在 https://min.io 申请 Free Tier，保存 license 到上述路径后重试
  exit /b 1
)

if not exist "%DATA%" mkdir "%DATA%"
set MINIO_ROOT_USER=minioadmin
set MINIO_ROOT_PASSWORD=minioadmin123
start "" "%EXE%" server "%DATA%" --license "%ROOT%\minio.license" --address ":9001" --console-address ":9002"
echo MinIO 启动中：API http://127.0.0.1:9001  控制台 http://127.0.0.1:9002
echo 启动后请创建 bucket: imaging
