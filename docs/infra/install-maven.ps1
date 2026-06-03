# 智慧云脑诊疗平台 — Windows 命令行安装 Maven 3.9.10 + 阿里云镜像
# 用法：
#   .\install-maven.ps1                    # 默认 D:\dev
#   .\install-maven.ps1 -InstallRoot D:\dev
#   .\install-maven.ps1 -InstallRoot C:\dev

param(
    [string]$InstallRoot = "D:\dev"
)

$ErrorActionPreference = "Stop"

$MavenVersion = "3.9.10"
$MavenHome    = "$InstallRoot\apache-maven-$MavenVersion"
$ZipPath      = "$InstallRoot\apache-maven-$MavenVersion-bin.zip"

# 国内镜像（清华失败可改华为云，见脚本末尾注释）
$DownloadUrl  = "https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"

Write-Host "==> 检查 JDK 17..."
& java -version 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Error "未找到 java，请先安装 JDK 17 并配置 JAVA_HOME"
}

Write-Host "==> 创建目录 $InstallRoot"
New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null

if (-not (Test-Path "$MavenHome\bin\mvn.cmd")) {
    Write-Host "==> 从清华镜像下载 Maven..."
    if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
        curl.exe -fL -o $ZipPath $DownloadUrl
    } else {
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipPath -UseBasicParsing
    }

    Write-Host "==> 解压..."
    Expand-Archive -Path $ZipPath -DestinationPath $InstallRoot -Force
    Remove-Item $ZipPath -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "==> Maven 已存在于 $MavenHome，跳过下载"
}

Write-Host "==> 写入用户环境变量 MAVEN_HOME / Path"
[System.Environment]::SetEnvironmentVariable("MAVEN_HOME", $MavenHome, "User")
$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
$mavenBin = "$MavenHome\bin"
if ($userPath -notlike "*$mavenBin*") {
    [System.Environment]::SetEnvironmentVariable("Path", "$userPath;$mavenBin", "User")
}

Write-Host "==> 配置阿里云 Maven 镜像 (.m2\settings.xml)"
$m2Dir = Join-Path $env:USERPROFILE ".m2"
New-Item -ItemType Directory -Force -Path $m2Dir | Out-Null
$settingsTemplate = Join-Path $PSScriptRoot "maven-settings.example.xml"
if (-not (Test-Path $settingsTemplate)) {
    Write-Error "找不到 $settingsTemplate"
}
Copy-Item -Force $settingsTemplate (Join-Path $m2Dir "settings.xml")

# 当前会话立即生效
$env:MAVEN_HOME = $MavenHome
$env:Path = "$mavenBin;$env:Path"

Write-Host "==> 验证"
& mvn -version

Write-Host ""
Write-Host "完成。请关闭并重新打开 PowerShell 后，在项目根目录执行："
Write-Host "  mvn -pl hospital-backend/hospital-common,hospital-backend/hospital-gateway -am clean package -DskipTests"

# 若清华下载失败，手动执行（华为云）：
# curl.exe -fL -o D:\dev\apache-maven-3.9.10-bin.zip "https://mirrors.huaweicloud.com/apache/maven/maven-3/3.9.10/binaries/apache-maven-3.9.10-bin.zip"
