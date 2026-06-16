@echo off
rem 转发到本机已安装的社区版 MinIO（2026-06-11 装在 C:\dev，无需 license）
if not exist "C:\dev\start-minio-community.bat" (
  echo [ERROR] 未找到 C:\dev\start-minio-community.bat
  echo 请先确认 C:\dev\minio\minio-community.exe 存在
  pause
  exit /b 1
)
call "C:\dev\start-minio-community.bat"
