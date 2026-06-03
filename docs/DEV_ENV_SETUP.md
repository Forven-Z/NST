# 智慧云脑诊疗平台 — 开发环境配置手册

> **版本**：v1.1.8 | 2026-05  
> **文档索引**：[README.md](./README.md)  
> **适用对象**：项目全体开发人员（**Windows 10/11**，团队统一）  
> **部署方式**：**不安装 Docker Desktop**；PostgreSQL / MinIO / Nacos 均在 **Windows 本机**安装与启动  
> **关联**：[MICROSERVICES.md](./MICROSERVICES.md) §6.2（启动顺序）、`API.md` §〇、`PROJECT_REQUIREMENST.md` §0.1

---

## 一、环境总览

### 1.0 团队环境基线（已定稿）

> 下列为项目组**当前统一口径**；已满足的组件 **无需重装**，第二节只做校验。


| 组件                | 团队版本                      | 说明                                               |
| ----------------- | ------------------------- | ------------------------------------------------ |
| **JDK**           | **17**                    | Java 微服务                                         |
| **Python**        | **3.10.6**                | `hospital-ai`                                    |
| **PyTorch**       | **2.x** + **CUDA 12.6**   | CNN 推理（GPU）                                      |
| **Node.js**       | **v22.14.0**              | PC 前端；**≥22.14 或同 major 22.x 均可**                |
| **Redis**         | **5.0.14.1**（Windows 移植版） | 可选；网关限流、Token 黑名单；**不必升级到 7.x**                  |
| **Maven**         | **3.9.10**                | 构建 `hospital-backend`（**≥3.9.6** 均可，团队统一 3.9.10） |
| **Nacos**         | **2.2.3**                 | 注册中心 + 配置（`nacos-server-2.2.3.zip`）              |
| **IntelliJ IDEA** | 各成员自备                     | **不强制版本统一**（Ultimate / Community 均可）             |


### 1.1 已具备组件（仅校验，勿重装）

见 **§二** 检查清单；其中 Node / Redis 校验命令：

```powershell
node -v
# 期望：v22.14.0（或 v22.x.x）

redis-server --version
# 期望：Redis server v=5.0.14.1 ...

redis-cli ping
# 期望：PONG
```

### 1.2 仍需安装或未统一的组件


| 组件             | 推荐版本               | 优先级       | 用途                    |
| -------------- | ------------------ | --------- | --------------------- |
| **Git**        | 2.40+              | 必装        | 克隆仓库                  |
| **Maven**      | **3.9.10**（见 §1.0） | 新同学必装     | 构建 `hospital-backend` |
| **PostgreSQL** | **16.x**           | 必装        | 业务库（Windows 安装包）      |
| **pgvector**   | 与 PG16 匹配          | P4 前可暂缓   | RAG 向量扩展（见 §六）        |
| **MinIO**      | 最新 Windows 版       | 必装        | 医学影像对象存储              |
| **Nacos**      | **2.2.3**（见 §1.0）  | 新同学必装     | 注册中心 + 配置             |
| **微信开发者工具**    | 稳定版                | 必装（小程序同学） | **原生小程序**开发、预览、上传     |


> **Node.js / Redis**：多数成员已安装；新同学 Node 见 §五；Redis 见 §5.2（**5.0.14.1 移植版**）。  
> **不使用 Docker**：`docs/infra/docker-compose.yml` 为可选参考，**团队不必安装 Docker Desktop**（见 §六 本机安装）。

> **P1～P3 以 Java 为主**：Python 服务可暂不启动；基础设施（PG、MinIO、Nacos）需先就绪。

---

## 二、安装前检查清单

在 PowerShell 中执行（**§1.0 基线中已具备的项必跑**）：

```powershell
java -version
# 应包含：version "17.x.x"

python --version
# 应输出：Python 3.10.6

python -c "import torch; print('PyTorch', torch.__version__); print('CUDA', torch.version.cuda); print('CUDA available', torch.cuda.is_available())"
# 应输出：PyTorch 2.x、CUDA 12.6、CUDA available True（有 NVIDIA 显卡且驱动正常时）

mvn -version
# 期望：Apache Maven 3.9.10（或 ≥3.9.6）；未安装见第四节

node -v
npm -v
# 期望 node：v22.14.0 或 v22.x；未安装见第五节

redis-server --version
redis-cli ping
# 期望：v5.0.14.1、PONG；未安装见 §5.2

# 以下 §六 安装完成后验证：
psql --version
# 期望：psql (PostgreSQL) 16.x
```

**JDK 环境变量（Windows）**


| 变量          | 示例值                            |
| ----------- | ------------------------------ |
| `JAVA_HOME` | `C:\Program Files\Java\jdk-17` |
| `Path` 追加   | `%JAVA_HOME%\bin`              |


---

## 三、安装 Git

1. 打开 [https://git-scm.com/download/win](https://git-scm.com/download/win) 下载 64-bit 安装包。
2. 安装时勾选 **「Git from the command line and also from 3rd-party software」**。
3. 验证：

```powershell
git --version
```

1. 克隆项目（示例）：

```powershell
cd C:\Users\你的用户名\Desktop
git clone https://github.com/Forven-Z/NST.git
cd NST
```

---

## 四、安装 Maven（团队基线 3.9.10）

> **前提**：已安装 **JDK 17**，`java -version` 显示 17。  
> **安装目录**：**C:\dev 或 D:\dev 均可**（路径无中文、无空格）；下文以 `**D:\dev`** 为例，用 C 盘时把 `D:` 改成 `C:` 即可。  
> **推荐流程**：**CMD 下载解压**（§4.1）→ **图形界面配置环境变量**（§4.2）→ **验证**（§4.4）→ **编译时再配阿里云镜像**（§4.3，可选）。

> **两件事别混**：  
>
> - **安装 Maven**：下载 zip、解压、配 `MAVEN_HOME` / `Path`，让 `mvn -version` 能用。  
> - **阿里云镜像**：以后 `**mvn package` 拉 Spring Boot 等依赖 JAR** 时用，**安装 Maven 时不必立刻配置**。

### 4.0 快速路径（大多数同学照做即可）


| 步骤  | 做什么                                       | 章节   |
| --- | ----------------------------------------- | ---- |
| 1   | CMD 下载 + 解压到 `D:\dev`                     | §4.1 |
| 2   | **高级系统设置 → 环境变量** 配 `MAVEN_HOME` 与 `Path` | §4.2 |
| 3   | 新开 CMD，执行 `where mvn`、`mvn -version`      | §4.4 |
| 4   | （可选）复制 `settings.xml`，首次编译前再配             | §4.3 |


---

### 4.1 下载并解压（CMD · 纯命令）

在 **命令提示符**（提示符为 `C:\Users\你的用户名>`）中执行：

```cmd
mkdir D:\dev 2>nul

curl.exe -fL -o D:\dev\apache-maven-3.9.10-bin.zip "https://mirrors.huaweicloud.com/apache/maven/maven-3/3.9.10/binaries/apache-maven-3.9.10-bin.zip"

tar -xf D:\dev\apache-maven-3.9.10-bin.zip -C D:\dev

dir D:\dev\apache-maven-3.9.10\bin\mvn.cmd
```

最后一行应列出 `mvn.cmd`（约 8.6 MB 的 zip 下载成功）。

**国内下载地址（2026-05 实测）**


| 优先级       | 镜像                                                                                                                                  | 说明                        |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| **1（推荐）** | [华为云 apache-maven-3.9.10-bin.zip](https://mirrors.huaweicloud.com/apache/maven/maven-3/3.9.10/binaries/apache-maven-3.9.10-bin.zip) | §4.1 默认 URL               |
| 2         | [Apache 归档 archive.apache.org](https://archive.apache.org/dist/maven/maven-3/3.9.10/binaries/apache-maven-3.9.10-bin.zip)           | 官方归档，可能较慢                 |
| 3         | [Maven 官网下载页](https://maven.apache.org/download.cgi)                                                                                | 浏览器下载                     |
| ⚠️ 勿用     | ~~清华 tuna 同路径~~                                                                                                                     | **已 404**，会报 `curl: (22)` |


华为云失败时，CMD 备用命令：

```cmd
curl.exe -fL -o D:\dev\apache-maven-3.9.10-bin.zip "https://archive.apache.org/dist/maven/maven-3/3.9.10/binaries/apache-maven-3.9.10-bin.zip"
tar -xf D:\dev\apache-maven-3.9.10-bin.zip -C D:\dev
```

解压后目录结构：

```text
D:\dev\apache-maven-3.9.10\
    ├── bin\          ← mvn.cmd
    ├── conf\
    └── lib\
```

> 若解压多了一层（如 `...\apache-maven-3.9.10\apache-maven-3.9.10\bin`），请把内层内容上移，保证 `bin\mvn.cmd` 路径正确。

**浏览器下载（可选）**：从上表链接下载 zip，解压到 `D:\dev\`，效果与 CMD 相同。

---

### 4.2 配置环境变量（图形界面 · 推荐）

> **不推荐** 用 CMD 的 `setx Path ...` 追加 Path：容易截断、或误写入 `REG_EXPAND_SZ` 等乱码。**请在图形界面里改。**

1. **Win + S** 搜索 **「环境变量」** → **「编辑系统环境变量」** → **「环境变量(N)...」**。

#### A. 用户变量 — 新建 `MAVEN_HOME`

在 **上半区「用户变量」** 中：


| 项   | 值                            |
| --- | ---------------------------- |
| 变量名 | `MAVEN_HOME`                 |
| 变量值 | `D:\dev\apache-maven-3.9.10` |


#### B. 用户变量 — 编辑 `Path`

1. 选中 `**Path`** → **编辑** → **新建**
2. 填入：`D:\dev\apache-maven-3.9.10\bin`（或 `%MAVEN_HOME%\bin`）
3. 检查 Path 中 **没有** 以 `REG_EXPAND_SZ` 开头的异常条目；若有，**删除**

#### C. 若本机曾装过旧版 Maven — 删系统 Path 里的旧条目

若新开 CMD 后 `mvn -version` 仍显示 **3.8.x** 等旧版本，多半是 **系统变量 → Path** 里还留着旧 Maven，且排在前面。

1. 在 **下半区「系统变量」** 选中 `**Path`** → **编辑**（可能需要管理员）
2. **删除** 类似下面的旧条目（路径因机器而异）：

```text
D:\某旧目录\apache-maven-3.8.8-bin\apache-maven-3.8.8\bin
```

1. 保留用户变量里的 `D:\dev\apache-maven-3.9.10\bin`

#### D. 确认 JDK 17

同一界面检查 `**JAVA_HOME**` 指向 JDK 17（如 `D:\java\jdk-17.0.10`），且 **用户或系统 `Path`** 中含 `%JAVA_HOME%\bin`。

#### E. 保存并重启终端

一路 **确定** → **关闭所有 CMD/PowerShell** → **新开窗口** 再验证。

> **不要用 CMD `setx` 改 Path**；环境变量请在图形界面维护。

---

### 4.3 配置国内镜像（可选 · 编译项目时再用）

Maven **安装包** 已在 §4.1 下完；本节配置的是 **编译时** 从国内仓库拉 **Java 依赖 JAR**，国内网络下建议配置，但 **不阻碍 `mvn -version`**。

**何时配**：第一次执行 `mvn package` 前；若下载慢或超时再配亦可。

**方式 1 — 资源管理器（无需命令行）**

1. 打开 `C:\Users\<你的用户名>\.m2\`（没有则新建 `.m2` 文件夹）
2. 复制项目内 `[docs/infra/maven-settings.example.xml](./infra/maven-settings.example.xml)` 到该目录
3. **重命名**为 `settings.xml`

**方式 2 — CMD**

```cmd
cd /d C:\Users\你的用户名\Desktop\NST
if not exist "%USERPROFILE%\.m2" mkdir "%USERPROFILE%\.m2"
copy /Y docs\infra\maven-settings.example.xml "%USERPROFILE%\.m2\settings.xml"
```

**模板核心（了解即可）**

```xml
<mirror>
  <id>aliyun-public</id>
  <mirrorOf>*</mirrorOf>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

> 不要改 `D:\dev\apache-maven-3.9.10\conf\settings.xml`；用户目录 `**.m2\settings.xml**` 优先级更高，升级 Maven 不会丢配置。

**（可选）npm 国内镜像**（前端用，与 Maven 无关）：

```cmd
npm config set registry https://registry.npmmirror.com
```

---

### 4.4 验证安装

**新开 CMD**，依次执行：

```cmd
where mvn
mvn -version
java -version
```

**期望输出示例**：

```text
D:\dev\apache-maven-3.9.10\bin\mvn
D:\dev\apache-maven-3.9.10\bin\mvn.cmd

Apache Maven 3.9.10 (...)
Maven home: D:\dev\apache-maven-3.9.10
Java version: 17.x.x, ...
```

> `**where mvn` 出现两行是正常的**：同一目录下 Maven 自带 `mvn`（给 Git Bash/WSL）和 `mvn.cmd`（给 Windows CMD），**不是装了两套**。CMD 里实际执行的是 `mvn.cmd`。需警惕的是 **两个不同目录**（例如旧版 3.8.8 与新 3.9.10 并存）。


| 检查项         | 期望                                             |
| ----------- | ---------------------------------------------- |
| `where mvn` | 均指向 `D:\dev\apache-maven-3.9.10\bin\`（一行或两行均可） |
| Maven 版本    | **3.9.10**（≥3.9.6 亦可）                          |
| Java 版本     | **17**                                         |


若 `mvn` 找不到：检查 §4.2 是否保存、是否 **新开** 终端；或临时测试：

```cmd
D:\dev\apache-maven-3.9.10\bin\mvn.cmd -version
```

---

### 4.5 编译本项目（验证工程）

```cmd
cd /d C:\Users\你的用户名\Desktop\NST\hospital-backend
mvn -q -DskipTests package
```

或仅编 common + gateway：

```cmd
cd /d C:\Users\你的用户名\Desktop\NST
mvn -pl hospital-backend/hospital-common,hospital-backend/hospital-gateway -am clean package -DskipTests
```


| 现象              | 说明                                        |
| --------------- | ----------------------------------------- |
| 首次运行下载很多 jar    | 正常；慢则配置 §4.3 阿里云镜像                        |
| `BUILD SUCCESS` | Maven + JDK + POM 均 OK                    |
| 依赖下载失败          | 检查 `%USERPROFILE%\.m2\settings.xml`、网络/代理 |


> 依赖缓存在 `C:\Users\<用户名>\.m2\repository\`，**不要**提交到 Git。

---

### 4.6 其他安装方式（可选）

#### A. PowerShell 一键（含镜像）

```powershell
cd C:\Users\你的用户名\Desktop\NST\docs\infra
Set-ExecutionPolicy -Scope Process Bypass -Force
.\install-maven.ps1
# 指定目录：.\install-maven.ps1 -InstallRoot D:\tools
```

> 脚本默认下载 URL 若失效，请改用手动 §4.1 华为云链接，或编辑脚本内 `$DownloadUrl`。

#### B. PowerShell 分步（与 §4.1 等价）

```powershell
$MAVEN_ROOT = "D:\dev"
New-Item -ItemType Directory -Force -Path $MAVEN_ROOT
curl.exe -fL -o "$MAVEN_ROOT\apache-maven-3.9.10-bin.zip" "https://mirrors.huaweicloud.com/apache/maven/maven-3/3.9.10/binaries/apache-maven-3.9.10-bin.zip"
Expand-Archive -Path "$MAVEN_ROOT\apache-maven-3.9.10-bin.zip" -DestinationPath $MAVEN_ROOT -Force
```

环境变量仍建议用 **§4.2 图形界面** 配置。

---

### 4.7 常见问题


| 问题                                  | 处理                                                      |
| ----------------------------------- | ------------------------------------------------------- |
| `curl: (22) ... 404`                | 清华镜像该路径已失效；改用 §4.1 **华为云** 或 **archive.apache.org**     |
| `mvn -version` 仍是 3.8.x             | **系统变量 Path** 删旧 Maven bin；见 §4.2 C                     |
| `where mvn` 指向旧目录                   | 同上；用户 Path 里新 Maven 需在生效顺序中优先                           |
| `where mvn` 两行同目录 `mvn` + `mvn.cmd` | **正常**，见 §4.4 说明；非重复安装                                  |
| Path 出现 `REG_EXPAND_SZ`             | 图形界面删除该异常条目，只保留正常路径                                     |
| `mvn` 不是内部命令                        | §4.2 检查 `MAVEN_HOME`、`Path`；**新开** CMD                  |
| Maven 用了 Java 8                     | `JAVA_HOME` 改为 JDK 17，`%JAVA_HOME%\bin` 在 Path 靠前       |
| 编译下载很慢                              | 配置 §4.3 `.m2\settings.xml`                              |
| 不想用 `D:\dev`                        | 解压到如 `D:\tools\apache-maven-3.9.10`，`MAVEN_HOME` 改为对应路径 |
| CMD 里 `$MAVEN_ROOT = ...` 报错        | 那是 PowerShell 语法；下载用 §4.1 纯 CMD，或先输入 `powershell`       |


---

## 五、Node.js 与 Redis

### 5.1 Node.js（团队基线 v22.14.0）

**已安装且 `node -v` 为 v22.x 的同学：跳过安装，直接 §5.2。**

**新同学安装步骤（Windows）**

1. 打开 [https://nodejs.org/](https://nodejs.org/) 下载 **22.x**（与团队一致的 **22.14.0** 或同系列最新 22.x）。
2. 安装时勾选 **「Automatically install necessary tools」**（可选）。
3. 验证：

```powershell
node -v    # v22.14.0 或 v22.x.x
npm -v
```

> 不建议为本项目单独降级到 Node 20；若遇依赖兼容问题再在组内同步。

### 5.2 Redis（团队基线 v5.0.14.1 · 可选）

**已安装且 `redis-server --version` 为 5.0.14.1 的同学：确保服务已启动即可。**

```powershell
redis-cli ping
# PONG
```

**新同学（Windows）**：安装 [Redis for Windows 5.0.14.1](https://github.com/tporadowski/redis/releases) 移植版，安装时可勾选 **Windows 服务**，或手动运行 `redis-server.exe`。

本项目 **P1～P3 可不启 Redis**；启用时 Spring Boot 连接 `127.0.0.1:6379` 即可，**5.0.14.1 满足开发**。

### 5.3 启动 PC 前端

```powershell
cd <项目根目录>\hospital-frontend
npm install
npm run dev
```

浏览器访问终端提示地址（通常 `http://localhost:5173`）。

### 5.4 环境变量（前端调网关）

在 `hospital-frontend` 根目录创建 `.env.development`（团队统一）：

```env
VITE_API_BASE_URL=http://localhost:9000
```

---

## 六、Windows 本机安装基础设施（团队标准 · 不用 Docker）

> **团队定稿**：全员 Windows，**不装 Docker Desktop**。以下三步装齐 **PostgreSQL、MinIO、Nacos** 即可开发 Java 主链路。  
> **基础设施目录**：Nacos、MinIO、Maven 等默认 `**D:\dev\`**（与 §四 Maven 一致）；路径勿含中文、无空格。

### 6.0 安装顺序建议

```text
1. PostgreSQL 16  → 建库 hospital
2. Nacos 2.2.3    → standalone 启动（8848）
3. MinIO          → API 9001 / 控制台 9002（勿占 9000）
4. Redis          → 已有 5.0.14.1 则启动服务（可选）
```

**开发环境默认账号（勿用于生产）**


| 组件         | 账号           | 密码                    | 端口                        |
| ---------- | ------------ | --------------------- | ------------------------- |
| PostgreSQL | `postgres`   | `**123456`**（安装时统一填写） | 5432                      |
| MinIO      | `minioadmin` | `minioadmin123`       | API **9001**，控制台 **9002** |
| Nacos      | `nacos`      | `nacos`               | 8848                      |
| Redis      | —            | —                     | 6379                      |


---

### 6.1 PostgreSQL 16（Windows）

#### 6.1.1 安装

1. 打开 [PostgreSQL Windows 下载](https://www.postgresql.org/download/windows/)，使用 **EDB 安装程序** 安装 **PostgreSQL 16**。
2. 安装组件勾选 **PostgreSQL Server**、**Command Line Tools**、**pgAdmin 4**（可选，见 §6.1.8）。
3. 端口 **5432**；为超级用户 `postgres` 设置密码 `**123456`**（团队统一，便于联调）。
4. Stack Builder 界面：文本框选择 **PostgreSQL 16 (x64) on port 5432** → **Next**（其余扩展 P1 可不装）。

#### 6.1.2 将 `psql` 加入 Path（安装后必做）

安装完成后，若 CMD 执行 `psql` 报 **「不是内部或外部命令」**，说明 **Command Line Tools 的 `bin` 目录未加入 Path**。

**常见安装路径（按本机实际为准）**


| 盘符    | `psql.exe` 所在目录                      |
| ----- | ------------------------------------ |
| C 盘默认 | `C:\Program Files\PostgreSQL\16\bin` |
| D 盘默认 | `D:\Program Files\PostgreSQL\16\bin` |


**图形界面配置（推荐）**

1. **Win + S** → **环境变量** → **编辑系统环境变量** → **环境变量(N)...**
2. **用户变量** → 选中 **Path** → **编辑** → **新建**
3. 填入上表对应路径（例如 `D:\Program Files\PostgreSQL\16\bin`）
4. 确定 → **关闭所有 CMD** → **新开 CMD**

验证：

```cmd
psql --version
```

期望：`psql (PostgreSQL) 16.x`

> 临时测试（不改 Path）：`set PATH=D:\Program Files\PostgreSQL\16\bin;%PATH%`（路径按本机修改）。

#### 6.1.3 首次连接 `psql`（易错点必读）

**两种提示符别混**


| 提示符                | 所在环境           | 该做什么                                                     |
| ------------------ | -------------- | -------------------------------------------------------- |
| `C:\Users\King>`   | Windows CMD    | 输入 `psql -U postgres -h localhost`                       |
| `用户 postgres 的口令：` | psql 登录        | **只在这里输入一次密码** `123456`（输入时不显示，正常）                       |
| `postgres=#`       | 已连上 PostgreSQL | 输入 **SQL 或 psql 命令**（如 `CREATE DATABASE ...;`、`\l`、`\q`） |
| `postgres-#`       | SQL 语句未写完      | 按 **Ctrl + C** 取消；**不要**在这里再输密码                          |


**典型误操作**：出现 `postgres=#` 后又输入 `123456`，psql 会把它当成 SQL，出现 `postgres-#` 并卡住。**密码只在「口令：」处输入一次。**

**连接步骤（CMD）**

```cmd
psql -U postgres -h localhost
```

1. 提示 `用户 postgres 的口令：` → 输入 `**123456**` 回车（无回显）
2. 出现 `postgres=#` 及 `psql (16.x)` 欢迎信息 → **连接成功**
3. 退出：输入 `\q` 回车，回到 `C:\Users\King>`

**常用 psql 命令**


| 命令            | 作用               |
| ------------- | ---------------- |
| `\l`          | 列出所有数据库          |
| `\c hospital` | 切换到 `hospital` 库 |
| `\dt`         | 列出当前库下的表         |
| `\q`          | 退出 psql          |


也可从开始菜单打开 **SQL Shell (psql)**，按提示依次回车（Server / Database / Port / Username 用默认，最后一项 Password 填 `123456`）。

#### 6.1.4 创建业务库 `hospital`

**方式 A — 在 `psql` 里交互执行**

```cmd
psql -U postgres -h localhost
```

口令：`123456`。在 `postgres=#` 下**逐条**执行（注意末尾分号）：

```sql
CREATE DATABASE hospital ENCODING 'UTF8';
```

期望：`CREATE DATABASE`

```sql
\c hospital
```

期望：`You are now connected to database "hospital" as user "postgres".`，提示符变为 `hospital=#`

```sql
\q
```

**方式 B — CMD 一行命令（推荐，免进交互）**

```cmd
psql -U postgres -h localhost -c "CREATE DATABASE hospital ENCODING 'UTF8';"
```

执行时会提示输入口令，填 `**123456**`。若库已存在会报错，可忽略。

#### 6.1.5 执行建表与种子数据（P0.5 / P1）

在项目根目录 **NST** 下打开 CMD（先 `cd /d C:\Users\你的用户名\Desktop\NST`）：

```cmd
psql -U postgres -d hospital -f docs\sql\schema.sql
psql -U postgres -d hospital -f docs\sql\seed-dict.sql
```

每条命令提示口令时填 `**123456**`。详见 `[docs/sql/README.md](./sql/README.md)`。

> **Windows 中文环境**：`seed-dict.sql` 含中文，若报 `编码"GBK"...在编码"UTF8"没有相对应值`，脚本内已含 `\encoding UTF8`；仍失败时在 PowerShell 先执行 `$env:PGCLIENTENCODING="UTF8"` 再跑，或见 `[sql/README.md](./sql/README.md)` §一。

验证：

```cmd
psql -U postgres -d hospital -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
```

期望：约 **26** 张表（具体以 `schema.sql` 为准）。

#### 6.1.6 pgvector（向量扩展 · P4 前可暂缓）

> **什么时候装**：P1～P3 做挂号/病历/收费 **不必装**；**P4 做 Spring AI RAG** 前再装即可。  
> **装的是什么**：PostgreSQL 的 `**vector` 扩展**（pgvector），用于向量相似度检索，不是单独再装一个数据库。

##### 0. 安装前确认


| 检查项            | 你的环境示例                                                                     |
| -------------- | -------------------------------------------------------------------------- |
| PostgreSQL 大版本 | **16.x**（`psql --version`）                                                 |
| 安装根目录          | 常见 `C:\Program Files\PostgreSQL\16` 或 `**D:\Program Files\PostgreSQL\16`** |
| 业务库已建好         | `hospital` 库存在（§6.1.4）                                                     |
| 权限             | 复制文件到 `Program Files` 通常需 **管理员**                                          |


下文以 `**D:\Program Files\PostgreSQL\16`** 为例；装在 C 盘则把 `D:` 改成 `C:`。

##### 1. 下载 Windows 预编译包（推荐）

官方 [pgvector Releases](https://github.com/pgvector/pgvector/releases) **不提供** Windows 二进制，团队 Windows 本机用社区预编译包：


| 项               | 值                                                                                                                                    |
| --------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 来源              | [andreiramani/pgvector_pgsql_windows](https://github.com/andreiramani/pgvector_pgsql_windows)（非 Apache 官方，社区编译）                      |
| PostgreSQL 16 包 | [vector.v0.8.2-pg16.zip](https://github.com/andreiramani/pgvector_pgsql_windows/releases/download/0.8.2_16.1/vector.v0.8.2-pg16.zip) |
| 版本              | pgvector **0.8.2**，适配 **PostgreSQL 16 / Windows x64**                                                                                |


**CMD 下载到 `D:\dev`（可选）**

```cmd
mkdir D:\dev 2>nul
curl.exe -fL -o D:\dev\vector.v0.8.2-pg16.zip "https://github.com/andreiramani/pgvector_pgsql_windows/releases/download/0.8.2_16.1/vector.v0.8.2-pg16.zip"
```

下载完成后 zip 约 **158 KB**。

##### 2. 停止 PostgreSQL 服务（复制 DLL 前必做）

1. **Win + R** → 输入 `services.msc` → 回车
2. 找到名称类似 `**postgresql-x64-16`** 的服务（描述含 PostgreSQL 16）
3. 右键 → **停止**

> 不停止服务时，`lib\vector.dll` 可能被占用，复制失败或扩展加载异常。

##### 3. 解压并复制到 PostgreSQL 安装目录

zip 内结构为 `lib\`、`share\`、`include\`，需**合并**到 PG 安装根目录（不是单独再建一层文件夹）。

**方式 A — 资源管理器（最直观，我使用的）**

1. 右键 `D:\dev\vector.v0.8.2-pg16.zip` → **全部解压缩**
2. 打开解压后的文件夹，应看到 `**lib`**、`**share`**、`**include**` 三个文件夹
3. 进入 `**D:\Program Files\PostgreSQL\16**`（PostgreSQL 安装根目录，与 `bin`、`data` 同级）
4. 将解压出的 `**lib**`、`**share**`、`**include**` **分别复制进去**
5. 如果弹出「目标已包含同名文件」（我没弹出）→ 选 **「替换目标中的文件」** / **「全部是」**

复制完成后应存在（至少检查前两项）：

```text
D:\Program Files\PostgreSQL\16\lib\vector.dll
D:\Program Files\PostgreSQL\16\share\extension\vector.control
D:\Program Files\PostgreSQL\16\share\extension\vector--0.8.2.sql
```

**方式 B — CMD（管理员）**

以 **管理员身份** 打开 CMD：

```cmd
mkdir D:\dev\pgvector-extract 2>nul
tar -xf D:\dev\vector.v0.8.2-pg16.zip -C D:\dev\pgvector-extract

xcopy /E /Y D:\dev\pgvector-extract\lib "D:\Program Files\PostgreSQL\16\lib\"
xcopy /E /Y D:\dev\pgvector-extract\share "D:\Program Files\PostgreSQL\16\share\"
xcopy /E /Y D:\dev\pgvector-extract\include "D:\Program Files\PostgreSQL\16\include\"

dir "D:\Program Files\PostgreSQL\16\lib\vector.dll"
dir "D:\Program Files\PostgreSQL\16\share\extension\vector.control"
```

##### 4. 启动 PostgreSQL 服务

回到 **services.msc** → 同一服务 → 右键 **启动**。

或用管理员 CMD：

```cmd
net start postgresql-x64-16
```

> 服务名若不同，以 services.msc 里显示的为准。

##### 5. 在 `hospital` 库启用扩展

**CMD / PowerShell**（项目根目录或任意目录均可）：

```cmd
psql -U postgres -d hospital -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

提示口令：`123456`

期望输出：`CREATE EXTENSION`（若已启用过可能显示 `NOTICE: extension "vector" already exists, skipping`）。

也可在 `psql` 交互里执行：

```cmd
psql -U postgres -d hospital
```

```sql
CREATE EXTENSION IF NOT EXISTS vector;
\dx vector
\q
```

##### 6. 验证是否安装成功

```cmd
psql -U postgres -d hospital -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
```

期望（版本号可能为 0.8.2）：

```text
 extname | extversion
---------+------------
 vector  | 0.8.2
```

再测向量类型是否可用：

```cmd
psql -U postgres -d hospital -c "SELECT '[1,2,3]'::vector;"
```

无报错即扩展工作正常。

项目脚本（可选，与上面 SQL 等价）：

```cmd
cd /d C:\Users\你的用户名\Desktop\NST
psql -U postgres -d hospital -f docs\infra\init-db\01-extensions.sql
```

##### 7. 常见问题


| 现象                                      | 处理                                                         |
| --------------------------------------- | ---------------------------------------------------------- |
| 复制到 `Program Files` 被拒绝                 | 用 **管理员** 身份操作；或先停 PostgreSQL 服务                           |
| `could not open extension control file` | `vector.control` 未在 `share\extension\`；重新解压复制 **share** 目录 |
| `could not load library vector.dll`     | `vector.dll` 未在 `lib\`；或 PG 大版本与 zip 不匹配（须 **pg16** 包）     |
| `CREATE EXTENSION` 报版本不兼容               | 确认 PostgreSQL 为 **16.x**，且下载的是 **pg16** 的 zip              |
| GitHub 下载慢                              | 多试几次；或浏览器开代理后手动下载 zip                                      |


##### 8. 备选：自行编译（不推荐新手）

需安装 **Visual Studio C++ 构建工具**，在 **x64 Native Tools Command Prompt（管理员）** 中按 [pgvector 官方 Windows 说明](https://github.com/pgvector/pgvector#windows) 编译。团队 Windows 本机 **优先用 §6.1.6 预编译包**。

##### 9. 与项目阶段的关系


| 阶段      | pgvector                                                  |
| ------- | --------------------------------------------------------- |
| P1～P3   | **可不装**，不影响 `schema.sql` / 业务开发                           |
| P4 RAG  | **必装**，并在 `hospital` 库执行 `CREATE EXTENSION vector`        |
| 向量表 DDL | 见 `docs/sql/vector.sql`（P4 再写，由 Spring AI VectorStore 维护） |


#### 6.1.7 验证汇总

```cmd
psql --version
psql -U postgres -d hospital -c "SELECT version();"
psql -U postgres -d hospital -c "SELECT 1"
```

**JDBC（Java 连接）**

```text
jdbc:postgresql://127.0.0.1:5432/hospital
用户名 postgres，密码 123456
```

#### 6.1.8 可选：可视化客户端（看表 / 跑 SQL）

> **不要求安装**。项目验收与脚本执行以 `**psql` 命令行** 为准；GUI 仅便于本地浏览表结构、查看 `seed-dict.sql` 灌入的数据。

习惯用 Navicat 看 MySQL 的同学，可任选其一：


| 方式                  | 说明                                                     |
| ------------------- | ------------------------------------------------------ |
| **Navicat Premium** | 已支持 PostgreSQL，与 MySQL 连接方式类似                          |
| **pgAdmin 4**       | PostgreSQL 官方 GUI；安装 PostgreSQL 时勾选 **pgAdmin 4** 一并安装 |


**连接参数（团队默认）**


| 项         | 值                                       |
| --------- | --------------------------------------- |
| 主机 / Host | `127.0.0.1` 或 `localhost`               |
| 端口 / Port | `5432`                                  |
| 用户名       | `postgres`                              |
| 密码        | `123456`                                |
| 数据库       | `hospital`（建库后选此库；未建库前先连默认库 `postgres`） |


**Navicat Premium 建立连接**

1. 连接 → **PostgreSQL** → 新建连接
2. 填入上表参数 → **测试连接** → 确定
3. 展开 `hospital` → **表**，可查看 `schema.sql` 创建的 26 张业务表

**pgAdmin 4 建立连接**

1. 开始菜单打开 **pgAdmin 4**
2. 左侧 **Servers** 右键 → **Register** → **Server…**
3. **General** 页：Name 填 `local-hospital`（任意）
4. **Connection** 页：Host `127.0.0.1`，Port `5432`，Username `postgres`，Password `123456` → **Save**
5. 展开 **Databases** → `hospital` → **Schemas** → **public** → **Tables**

---

### 6.2 Nacos 2.2.3 standalone（Windows）

1. 下载 [Nacos 发行包](https://github.com/alibaba/nacos/releases) → `**nacos-server-2.2.3.zip`**（仅需服务端包，无需另下 console/client）。
2. 解压到 `**D:\dev\nacos`**（路径勿含中文）。
3. **单机模式**启动（CMD 示例）：

```cmd
cd /d D:\dev\nacos\bin
startup.cmd -m standalone
```

或一行启动：

```cmd
D:\dev\nacos\bin\startup.cmd -m standalone
```

> CMD 跨盘符须用 `**cd /d**`，否则当前目录可能仍在 C 盘，会报找不到 `startup.cmd`。

1. 浏览器打开：**[http://127.0.0.1:8848/nacos](http://127.0.0.1:8848/nacos)**
  - 默认账号密码：`nacos` / `nacos`（若提示登录）  
  - 「服务列表」为空属正常，Java 服务启动后会注册
2. 关闭：在同一目录执行 `.\shutdown.cmd`
3. （可选）设为开机自启：将 `startup.cmd -m standalone` 快捷方式放入「启动」文件夹，或使用 **NSSM** 注册 Windows 服务。

**Java 环境变量**

```text
NACOS_SERVER_ADDR=127.0.0.1:8848
```

> Nacos 2.x 启动可能需 **30～60 秒**；若 8848 被占用：`netstat -ano | findstr :8848`

---

### 6.3 MinIO（Windows 二进制 · AIStor Server）

> **用途**：医学影像、报告等文件的 **S3 兼容对象存储**（`hospital-pacs` / `hospital-ai` 读写）。  
> **团队目录**：程序 `D:\dev\minio\`，数据 `D:\dev\minio-data\`；**API 9001、控制台 9002**（勿占 Gateway **9000**）。

> **说明**：MinIO 官网现以 **AIStor Server** 提供 Windows 版 `minio.exe`。**启动必须带 `--license`**，否则进入 offline 模式，**所有 S3 读写会被拒绝**。

> **许可证团队约定**：**每人各自**在 min.io 申请 **Free Tier**（免费）；`minio.license` 只放本机 `D:\dev\minio\`，**禁止提交 Git、禁止组内转发 license 文件**（见 §6.3.3）。

#### 6.3.1 下载 `minio.exe`（浏览器 · 推荐）

1. 打开 [https://min.io/download](https://min.io/download)
2. 在 **AIStor** 区域找到卡片 **「AIStor Server」**（Data store for objects, tables, and files）
3. 点击该卡片下方的 **Install →**（**不要点** Client、SDK，也不要点下方 Add-ons 里的 KMS / DirectPV / Sidekick / Warp）
4. 进入 [AIStor Server 下载页](https://www.min.io/download/aistor-server?platform=windows)，选择 **Windows · amd64**
5. 按页内说明下载 `**minio.exe`**，保存到 `**D:\dev\minio\minio.exe**`

**下载页对照**


| 页面上的项                            | 是否下载                |
| -------------------------------- | ------------------- |
| **AIStor Server → Install**      | ✅ 要（本项目的对象存储服务端）    |
| AIStor Client                    | ❌ 否（`mc` 命令行客户端，可选） |
| AIStor SDK                       | ❌ 否（开发库）            |
| KMS / DirectPV / Sidekick / Warp | ❌ 否（附加组件）           |


**先建目录**

```cmd
mkdir D:\dev\minio 2>nul
mkdir D:\dev\minio-data 2>nul
```

#### 6.3.2 下载 `minio.exe`（命令行 · 可选）

PowerShell：

```powershell
New-Item -ItemType Directory -Force -Path D:\dev\minio, D:\dev\minio-data
Invoke-WebRequest -Uri "https://dl.min.io/aistor/minio/release/windows-amd64/minio.exe" -OutFile "D:\dev\minio\minio.exe"
D:\dev\minio\minio.exe --version
```

CMD（需已安装 `curl.exe`）：

```cmd
mkdir D:\dev\minio 2>nul
curl.exe -fL -o D:\dev\minio\minio.exe "https://dl.min.io/aistor/minio/release/windows-amd64/minio.exe"
D:\dev\minio\minio.exe --version
```

#### 6.3.3 获取 Free Tier 许可证（必做 · 每人一份）

AIStor **无有效 license 无法正常使用**（日志会出现 `No valid license found, running in offline mode. All S3 operations are denied`）。**启动前必须完成本节。**

**团队约定**


| 规则           | 说明                                                                |
| ------------ | ----------------------------------------------------------------- |
| **各自申请**     | 每位开发同学用 **自己的邮箱** 申请 Free Tier，保存到本机 `D:\dev\minio\minio.license` |
| **禁止提交 Git** | `minio.license` **不得**放入仓库、不得 PR、不得发到公开群；与 `.env` 同级敏感文件          |
| **禁止组内互传**   | 不要复制同学的 license；MinIO Free 协议限制再分发，且一机一份最稳妥                       |


**申请步骤**

1. 打开 [min.io/download](https://min.io/download)
2. 点击 **Request License Key** 或 **Access License Key**
3. 选择 **Free**（或 Enterprise Trial，开发试用）
4. 填写 **本人邮箱** 等信息，在邮件或页面中获取 license 内容
5. 记事本粘贴 license 全文 → **另存为** `**D:\dev\minio\minio.license`**（注意扩展名是 `.license`，不是 `.txt`）

**验证文件存在**

```cmd
dir D:\dev\minio\minio.license
```

#### 6.3.4 启动 MinIO（必须带 `--license`）

```cmd
cd /d D:\dev\minio
set MINIO_ROOT_USER=minioadmin
set MINIO_ROOT_PASSWORD=minioadmin123
minio.exe server D:\dev\minio-data --license D:\dev\minio\minio.license --address ":9001" --console-address ":9002"
```

PowerShell 等价：

```powershell
cd D:\dev\minio
$env:MINIO_ROOT_USER="minioadmin"
$env:MINIO_ROOT_PASSWORD="minioadmin123"
.\minio.exe server D:\dev\minio-data --license D:\dev\minio\minio.license --address ":9001" --console-address ":9002"
```

**首次启动**：空数据目录可能出现 `unformatted drive`，MinIO 会自动格式化，出现 `Successfully formatted pool` 即正常。

**启动成功标志**（日志中 **不应** 再出现 offline / denied license 警告）：

```text
MinIO AIStor Server
API: http://127.0.0.1:9001
WebUI: http://127.0.0.1:9002
```

窗口保持打开；关闭窗口即停止服务。

#### 6.3.5 控制台登录与创建 Bucket

1. 浏览器打开：**[http://127.0.0.1:9002](http://127.0.0.1:9002)**
2. 登录：`**minioadmin`** / `**minioadmin123**`（与上面环境变量一致）
3. 创建 Bucket（与项目约定一致）：
  - `**hospital-imaging**`（CT/影像原图，必建）  
  - `**hospital-reports**`（报告附件，可选）

**健康检查（另开终端）**

```cmd
curl http://127.0.0.1:9001/minio/health/live
```

#### 6.3.6 团队启动脚本 `start-minio.bat`

保存到 `**D:\dev\minio\start-minio.bat**`：

```bat
@echo off
cd /d D:\dev\minio
if not exist minio.license (
  echo [错误] 缺少 D:\dev\minio\minio.license
  echo 请按 DEV_ENV_SETUP.md 6.3.3 用本人邮箱申请 Free Tier，勿从 Git 或同学处复制。
  pause
  exit /b 1
)
set MINIO_ROOT_USER=minioadmin
set MINIO_ROOT_PASSWORD=minioadmin123
minio.exe server D:\dev\minio-data --license D:\dev\minio\minio.license --address ":9001" --console-address ":9002"
pause
```

双击或在 CMD 中运行：`D:\dev\minio\start-minio.bat`

#### 6.3.7 Java / 环境变量

```text
MINIO_ENDPOINT=http://127.0.0.1:9001
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
```

#### 6.3.8 常见问题


| 现象                                          | 处理                                                   |
| ------------------------------------------- | ---------------------------------------------------- |
| 下载页不知道点哪个                                   | 只点 **AIStor Server → Install**（§6.3.1 对照表）           |
| `offline mode` / `S3 operations are denied` | 未装有效 license；按 §6.3.3 **本人申请**，启动加 `--license`       |
| `unformatted drive` 后仍启动成功                  | 首次格式化数据目录，属正常（§6.3.4）                                |
| 9000 端口冲突                                   | MinIO **必须用 9001/9002**，勿与 Gateway 9000 混用           |
| 控制台打不开                                      | 确认 `minio.exe` 窗口未关；等几秒再访问 9002                      |
| `minioadmin` 登录失败                           | 检查启动前是否设置了 `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| 误把 license 提交 Git                           | 立即从历史中删除该文件；本机保留 `D:\dev\minio\minio.license` 即可     |


---

### 6.4 Redis（可选 · 团队已有 5.0.14.1）

已安装的同学确保服务运行：

```powershell
redis-cli ping
# PONG
```

未安装：见 **§5.2**；**P1～P3 可不启 Redis**。

---

### 6.5 基础设施一键检查（PowerShell）

```powershell
# PostgreSQL
psql -U postgres -d hospital -c "SELECT 1"

# Nacos（需已 startup.cmd）
curl http://127.0.0.1:8848/nacos/v1/console/health/readiness

# MinIO（需已启动 minio.exe）
curl http://127.0.0.1:9001/minio/health/live

# Redis（可选）
redis-cli ping
```

---

## 七、附录：Docker Compose（团队不使用 · 仅供参考）

> 仓库保留 `docs/infra/docker-compose.yml`，供有 Docker 的环境快速体验；**本项目团队不安装 Docker Desktop，请按 §六 操作**。

若个人机器已装 Docker，可参考该目录下 `docker compose up -d`；**与 §六 本机安装二选一，勿重复占用 5432/8848/9001 端口**。

---

## 八、Python 环境（已预装 · 仅校验与补依赖）

### 8.1 校验（必做）

```powershell
python --version
python -c "import torch; print('PyTorch', torch.__version__); print('CUDA', torch.version.cuda); print('CUDA available', torch.cuda.is_available())"
```

### 8.2 创建虚拟环境（推荐，避免污染全局）

```powershell
cd <项目根目录>\hospital-ai
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

若提示脚本禁止执行：

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

### 8.3 安装 Python 依赖（P4 前可暂缓）

待 `hospital-ai/requirements.txt` 提交后执行：

```powershell
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
pip install fastapi uvicorn python-multipart minio httpx
```

验证 FastAPI（后期）：

```powershell
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
curl http://localhost:8000/v1/health
```

> **P1～P3**：可不启动 `hospital-ai`；Java 对 CNN 接口返回 STUB 即可。

---

## 九、原生微信小程序

### 9.1 微信开发者工具（主开发环境）

1. 下载 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)。
2. 使用测试号或团队 **AppID** 登录。
3. **导入项目** → 目录选择仓库 `**hospital-patient-miniapp/`**（工程根目录，含 `app.json`）。
4. **设置 → 安全**：开发阶段勾选 **「不校验合法域名、web-view、TLS」**。

> **不再使用 uni-app / HBuilderX**：页面在工具内直接编辑 **WXML / WXSS / JS**，无需编译到 `dist/mp-weixin`。

### 9.2 推荐目录结构（初始化参考）

```text
hospital-patient-miniapp/
├── app.js / app.json / app.wxss
├── project.config.json
├── utils/
│   ├── request.js      # 封装 wx.request → Gateway
│   └── auth.js         # wx.login、token 存储
├── pages/
│   ├── login/
│   ├── register/       # 挂号
│   ├── bills/          # 待缴/缴费记录
│   └── medical-record/
└── components/
```

### 9.3 API 基址配置

在 `utils/request.js` 或 `app.js` 的 `globalData` 中配置：

```javascript
const API_BASE = 'http://localhost:9000/api/v1'
```

真机调试需内网穿透或部署测试环境 **HTTPS** 域名，并在微信公众平台配置 request 合法域名。

### 9.4 可选：Vant Weapp 组件库

若需现成 UI 组件，可在小程序工程内通过 **npm** 引入 [Vant Weapp](https://vant-contrib.gitee.io/vant-weapp/)（需微信开发者工具 **工具 → 构建 npm**）。**非必须**，也可用微信原生组件。

### 9.5 与 PC 端的差异（给小程序同学）


| 项   | PC 前端        | 原生小程序                  |
| --- | ------------ | ---------------------- |
| 框架  | Vue 3        | WXML + JS              |
| 状态  | Pinia        | globalData / 页面 data   |
| 请求  | Axios        | `wx.request` 封装        |
| UI  | Element Plus | 原生 / WeUI / Vant Weapp |


---

## 十、Java 微服务本地配置（统一环境变量）

各服务 `application.yml` 或 Nacos 配置中心建议使用下列变量（与 **§6** 本机默认值一致）：


| 变量                  | 开发默认值                   | 说明   |
| ------------------- | ----------------------- | ---- |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848`        | 注册中心 |
| `PG_HOST`           | `127.0.0.1`             |      |
| `PG_PORT`           | `5432`                  |      |
| `PG_DATABASE`       | `hospital`              |      |
| `PG_USER`           | `postgres`              |      |
| `PG_PASSWORD`       | `123456`                |      |
| `MINIO_ENDPOINT`    | `http://127.0.0.1:9001` |      |
| `MINIO_ACCESS_KEY`  | `minioadmin`            |      |
| `MINIO_SECRET_KEY`  | `minioadmin123`         |      |
| `REDIS_HOST`        | `127.0.0.1`             | 可选   |
| `REDIS_PORT`        | `6379`                  | 可选   |


**Gateway 示例（本地 IDEA 环境变量）**

```text
NACOS_SERVER_ADDR=127.0.0.1:8848
```

**JDBC URL 示例**

```text
jdbc:postgresql://127.0.0.1:5432/hospital
```

---

## 十一、IDE 与插件

### 11.1 IntelliJ IDEA（各成员自备，不统一版本）

团队一般已安装 IDEA（**Ultimate / Community 均可，不强制 2024+**）。新同学任选其一即可，**无需与队友版本一致**。

1. **Open** 项目根目录 `NST`（识别 Maven 多模块）。
2. **File → Project Structure → SDK**：选择 **JDK 17**。
3. 安装插件：**Lombok**（Settings → Plugins）。
4. 启用注解处理：**Settings → Build → Compiler → Annotation Processors → Enable**。
5. 运行 `HospitalGatewayApplication`（端口 **9000**）。

也可用 Eclipse / VS Code + Java 扩展，**不强制 IDEA**。

### 11.2 VS Code / Cursor（前端）

- 扩展：Vue - Official、ESLint（可选）。

---

## 十二、端口占用一览（避免冲突）


| 端口          | 服务                                    |
| ----------- | ------------------------------------- |
| 5173        | Vite 前端 dev                           |
| 5432        | PostgreSQL                            |
| 6379        | Redis（可选）                             |
| 8000        | hospital-ai Python（后期）                |
| 8848 / 9848 | Nacos                                 |
| 9000        | **Spring Cloud Gateway（勿被 MinIO 占用）** |
| 9001        | MinIO API                             |
| 9002        | MinIO Console                         |
| 9101        | hospital-auth                         |
| 9102        | hospital-his                          |
| 9103        | hospital-lis                          |
| 9104        | hospital-pacs                         |
| 9105        | hospital-management                   |
| 9106        | hospital-ai-bridge                    |


检查端口占用（Windows）：

```powershell
netstat -ano | findstr :9000
```

---

## 十三、按角色最低安装集


| 角色              | 必装                                     | 可选                     |
| --------------- | -------------------------------------- | ---------------------- |
| **Java 后端**     | JDK17✓、Maven、§六 PG/MinIO/Nacos；IDEA 自备 | Redis✓（可选）             |
| **PC 前端**       | Node22✓、Git                            | —                      |
| **小程序**         | **微信开发者工具**、Git                        | Node22（PC 前端或 npm 组件库） |
| **算法 / Python** | Python✓、PyTorch✓、§六 PG/MinIO           | hospital-ai P4 再装      |
| **全员**          | Git、§六 基础设施（**无 Docker**）              | Redis                  |


✓ = 团队已预装，仅校验。

---

## 十四、完整启动顺序（开发联调）

> **日常操作详细说明**（分阶段 R-min、IDEA 配置、客户端、验收）：见 **[RUNBOOK.md](./RUNBOOK.md)**。  
> **每一步为什么做**：见 **[IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) §八**。

```text
1. 启动 PostgreSQL 服务（Windows 服务或安装器自带）
2. 启动 Nacos：`D:\dev\nacos\bin\startup.cmd -m standalone`（或 `cd /d D:\dev\nacos\bin` 后 `startup.cmd -m standalone`）
3. 启动 MinIO：运行 `D:\dev\minio\start-minio.bat` 或 §6.3 命令
4. （可选）确认 Redis：redis-cli ping → PONG
5. IDEA 启动 hospital-gateway :9000
6. 启动 auth → management → his → lis → pacs → ai-bridge（按分期，见 MICROSERVICES.md §6.2）
7. npm run dev（hospital-frontend）
8. 微信开发者工具打开 patient-miniapp
9. hospital-ai :8000（P4 FastAPI，前期跳过）
```

---

## 十五、常见问题


| 现象                                         | 处理                                                                                |
| ------------------------------------------ | --------------------------------------------------------------------------------- |
| `mvn` 不是内部命令                               | 检查 `MAVEN_HOME` 与 `Path`                                                          |
| `java` 版本不是 17                             | 调整 `JAVA_HOME` 指向 JDK17                                                           |
| Nacos 启动慢或打不开                              | 等待 30～60s；检查 8848 端口；查看 `D:\dev\nacos\logs`；跨盘符启动用 `cd /d`（§6.2）                  |
| `psql` 不是内部命令                              | 将 `PostgreSQL\16\bin` 加入 Path；常见路径见 §6.1.2（C 盘或 **D 盘** `Program Files`）          |
| `postgres-#` 卡住、误输密码                       | 密码**只在**「口令：」处输一次；`postgres-#` 时 **Ctrl+C**，勿在 `postgres=#` 后再输 `123456`；见 §6.1.3 |
| `seed-dict.sql` GBK/UTF8 报错                | 脚本含 `\encoding UTF8`；或 `$env:PGCLIENTENCODING="UTF8"`；见 `sql/README.md` §一        |
| pgvector 安装 / `CREATE EXTENSION vector` 失败 | 见 §6.1.6 逐步说明；确认 pg16 包、停服务、复制 `lib`/`share`                                      |
| MinIO 与 Gateway 均要 9000                    | **MinIO 必须用 9001/9002**（§6.3.4）；Gateway 占 9000                                    |
| MinIO 下载页点哪个 / license 报错                  | §6.3.1 只下 **AIStor Server**；§6.3.3 **每人**申请 `minio.license`，**勿提交 Git**           |
| `npm install` 失败                           | 切换 npm 镜像：`npm config set registry https://registry.npmmirror.com`                |
| PyTorch 与 Python 版本不匹配                     | 保持 **Python 3.10.6**，勿升级到 3.12                                                    |
| `CUDA available False`                     | 检查 NVIDIA 驱动；确认安装的是 **CUDA 12.6** 对应的 PyTorch 2.x 轮子                              |
| 小程序请求失败                                    | 开发者工具关闭域名校验；确认 Gateway 已启动                                                        |


---

## 十六、验收自检表（完成后打勾）

- `java -version` → 17  
- `python --version` → 3.10.6  
- `python -c "import torch"` 无报错  
- `mvn -version` → **3.9.10**（或 ≥3.9.6）  
- `node -v` → v22.14.0 或 v22.x  
- （可选）`redis-server --version` → 5.0.14.1，`redis-cli ping` → PONG  
- `psql -U postgres -d hospital -c "SELECT 1"` 成功  
- Nacos 控制台 [http://127.0.0.1:8848/nacos](http://127.0.0.1:8848/nacos) 可打开  
- MinIO 控制台 [http://127.0.0.1:9002](http://127.0.0.1:9002) 可打开，bucket 已建  
- `mvn package` gateway 模块成功  
- `npm run dev` 前端可打开  
- （小程序）微信开发者工具可编译预览

---

## 十七、修订记录


| 版本     | 日期      | 说明                                                                      |
| ------ | ------- | ----------------------------------------------------------------------- |
| v1.0   | 2026-05 | 首版；JDK17 / Python3.10.6 / PyTorch 2.x + CUDA12.6 预装；Docker Compose 基础设施 |
| v1.0.1 | 2026-05 | 明确 PyTorch 2.x + CUDA 12.6（非 PyTorch 12.6）                              |
| v1.0.3 | 2026-05 | 患者端改为 **原生微信小程序**；移除 uni-app / HBuilderX                                |
| v1.0.4 | 2026-05 | **全员 Windows、不装 Docker**；§六 为本机安装标准路径                                   |
| v1.0.5 | 2026-05 | Maven 团队基线 **3.9.10**                                                   |
| v1.0.6 | 2026-05 | Nacos 团队基线 **2.2.3**（`nacos-server-2.2.3.zip`）                          |
| v1.0.7 | 2026-05 | 端口 9101～9106 对齐 his/lis/pacs；启动顺序见 `MICROSERVICES.md`                   |
| v1.0.8 | 2026-05 | §四 Maven 分步安装 + 清华下载 + 阿里云镜像模板                                          |
| v1.0.9 | 2026-05 | §4.0 命令行安装（curl/Expand-Archive）+ `install-maven.ps1`                    |
| v1.1.0 | 2026-05 | §四 重写：CMD 华为云下载 + **GUI 配环境变量**；镜像改可选；旧 Maven / 404 / setx 踩坑           |
| v1.1.1 | 2026-05 | PostgreSQL 安装密码团队默认改为 **123456**（`PG_PASSWORD` / JDBC 对齐）               |
| v1.1.2 | 2026-05 | §6.1 补充可选 GUI（Navicat / pgAdmin）连接说明；**不要求安装**                          |
| v1.1.3 | 2026-05 | §6.1 重写：Path 配置、`psql` 提示符与口令易错点、建库/跑脚本分步、D 盘路径示例                       |
| v1.1.4 | 2026-05 | `seed-dict.sql` 增加 `\encoding UTF8`；§6.1.5 / sql README 补充 Windows 编码说明 |
| v1.1.5 | 2026-05 | §6.1.6 pgvector Windows 分步安装（下载、停服务、复制、启用、验证）                           |
| v1.1.6 | 2026-05 | §6.2 / §6.3 Nacos、MinIO 默认路径统一为 **`D:\dev\`**；补充 `cd /d` 启动说明           |
| v1.1.7 | 2026-05 | §6.3 重写：AIStor Server 下载步骤、license、启动脚本、常见问题                            |
| v1.1.8 | 2026-05 | §6.3.3 license **必做**、**每人各自申请**、**禁止提交 Git**；启动必须 `--license`          |


---

*可选参考（团队不用）：`docs/infra/docker-compose.yml`*