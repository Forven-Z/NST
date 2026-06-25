# 智慧云脑诊疗平台 — 启动、联调与验收手册

> **版本**：v2.12 | 2026-06-04  
> **用途**：日常 **开什么、怎么开**；**R-min～R-full 联调验收**（原 INTEGRATION_CHECKLIST 已并入本文 §十二）。  
> **环境安装**（首次装软件）：见 [DEV_ENV_SETUP.md](./DEV_ENV_SETUP.md)  
> **实现进度**：见 [PROGRESS.md](./PROGRESS.md)  
> **任务与动机**：见 [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)

---

## 零、每次开机启动（速查）

> **一键启动（推荐）**：`.\scripts\start-project.ps1`  
> **一键停止**：`.\scripts\stop-project.ps1`  
> 数据环境由 `-EnvProfile local`（**默认**，本机 PostgreSQL / MinIO）或 `-EnvProfile cloud`（阿里云 ECS）决定；**Java / Nacos / 前端始终在本机**。

### A. 一键启动（日常 · 默认本机库）

**前提**：本机 PostgreSQL 5432 已启（Windows 服务 `postgresql-x64-16`）。**MinIO** 由脚本自动处理：`local` 本机未运行时自动启 `:9001`；`cloud` 探测 ECS 远程 MinIO（见 §4.3 / §4.5）。

在仓库根目录 **PowerShell** 执行 **一条命令**：

```powershell
cd <你的仓库路径>\NST
.\scripts\start-project.ps1              # 默认 -EnvProfile local
# 云端库：.\scripts\start-project.ps1 -EnvProfile cloud
```

脚本会自动：加载 `env-{profile}.ps1` → **MinIO**（local 自动启 / cloud 健康检查）→ 启 Nacos（若未运行）→ `mvn package` → 启动 **全部 Java 微服务**（auth/his/lis/pacs/disposal/management/ai-bridge/gateway）→ 新开窗口运行 **PC 前端** `npm run dev`。

| 访问 | 地址 |
|------|------|
| PC 前端 | http://localhost:5173 |
| API（Gateway） | http://127.0.0.1:9000/api/v1 |
| 测试登录 | `doctor01` / `123456`（完整账号见 [sql/README.md §三](./sql/README.md#三测试账号seed-写入后)） |

常用参数：

```powershell
.\scripts\start-project.ps1 -EnvProfile cloud    # 阿里云 ECS 库 + 远程 MinIO 探测
.\scripts\start-project.ps1 -SkipBuild           # 跳过 Maven 编译（已打包过）
.\scripts\start-project.ps1 -SkipFrontend        # 只启后端
.\scripts\start-project.ps1 -SkipMinio             # 不测影像时跳过 MinIO
.\scripts\start-project.ps1 -Restart             # 先 stop-project 再启动
.\scripts\start-project.ps1 -MinioHome D:\dev\minio -MinioData D:\dev\minio-data  # 自定义本机 MinIO 路径
```

停止：`.\scripts\stop-project.ps1`（不关 Nacos；需关 Nacos 见 §八；会一并停止 his 副本 **9202**）

### A.1 答辩：hospital-his 双实例（Gateway 负载均衡）

> **用途**：Nacos 中 `hospital-his` 显示 **2 个实例**，Gateway `lb://hospital-his` 轮询分流。  
> **前提**：必须先 `start-project.ps1`（主实例 **9102**、Nacos、Gateway 已就绪）。

```powershell
# 1. 正常一键启动
.\scripts\start-project.ps1 -EnvProfile cloud   # 或 local

# 2. 追加第二个 his 实例（固定端口 9202，服务名仍为 hospital-his）
.\scripts\start-his-replica.ps1 -EnvProfile cloud

# 3. Nacos 控制台 → 服务管理 → hospital-his → 实例数应为 2（9102 + 9202）

# 4. 演示结束，仅停副本（主实例 9102 继续运行）
.\scripts\stop-his-replica.ps1
```

**如何验证负载均衡**

| 步骤 | 说明 |
|------|------|
| 经 Gateway 访问 | 必须用 **`http://127.0.0.1:9000/api/v1/...`**，不要直连 9102/9202 |
| 看日志 | 主实例 `logs/project/hospital-his.log`，副本 `logs/project/hospital-his-replica-9202.log` |
| 多次请求 | 带 Token 反复请求如 `GET /registrar/departments`，两日志应交替出现访问 |

**注意**：`hospital-ai-bridge` 等模块若配置了 `HOSPITAL_HIS_BASE_URL=http://127.0.0.1:9102` 为 Feign **直连**，不经过 Gateway 负载均衡；答辩演示 LB 时用 **前端或 curl 打 9000** 即可。

### B. 精简启动（仅 R-min · 小程序 / 最小联调）

```powershell
.\scripts\start-r-min.ps1 -EnvProfile cloud
# 前端需另开窗口：cd hospital-frontend && npm run dev
```

### C. 仅切换环境变量（不自动启服务）

```powershell
. .\scripts\env-cloud.ps1   # 云端展示
. .\scripts\env-local.ps1   # 本地开发
```

IDEA 启动 Java 时：先 `. .\scripts\env-cloud.ps1`，或把变量粘到 Run Configuration。

### D. 环境脚本说明

#### 启动 / 环境

| 文件 | 作用 | 不会启动 |
|------|------|----------|
| [scripts/start-project.ps1](../scripts/start-project.ps1) | **一键启动**：`env-{profile}.ps1` → **MinIO**（local 自动启 9001 / cloud 远程健康检查）→ Nacos（8848，若未运行）→ **8 个 Java 微服务**（9101～9107、9000）→ PC 前端（5173） | PostgreSQL、**Python hospital-ai（8000）**、小程序 |
| [scripts/stop-project.ps1](../scripts/stop-project.ps1) | 停止 Java（9000、9101～9107、**9202**）+ 前端（5173） | **不关** Nacos（8848）、MinIO（9001）、Python（8000）、PG |
| [scripts/start-his-replica.ps1](../scripts/start-his-replica.ps1) | **答辩用**：在 9102 已启后，再起 his 副本 **9202**（Nacos 双实例 + Gateway LB） | 不启 Nacos/Gateway/其他微服务 |
| [scripts/stop-his-replica.ps1](../scripts/stop-his-replica.ps1) | 仅停 his 副本 **9202** | 不影响主实例 9102 |
| [scripts/env-cloud.ps1](../scripts/env-cloud.ps1) | `DB_HOST` / `MINIO_*` → ECS；`DB_PASSWORD` 为**云 PG 密码**（≠ 本机 `123456`） | 不启任何进程 |
| [scripts/env-local.ps1](../scripts/env-local.ps1) | `DB_HOST` / `MINIO_*` → `127.0.0.1` | 不启任何进程 |
| [scripts/start-r-min.ps1](../scripts/start-r-min.ps1) | **R-min 精简**：auth + his + ai-bridge + gateway（无 lis/pacs/前端） | 同上 |

**`start-project.ps1` 默认 `-EnvProfile local`**（本机 PostgreSQL）。云端答辩时用：

```powershell
.\scripts\start-project.ps1 -EnvProfile cloud
```

**基础设施谁负责**

| 组件 | cloud 模式 | local 模式 |
|------|------------|------------|
| PostgreSQL | ECS 上 `docker-compose`（§4.5） | 本机 Windows 服务 `postgresql-x64-16` |
| MinIO | ECS 上 `docker-compose`；`start-project` **探测** `$MINIO_ENDPOINT` | `start-project` **自动启**本机 `:9001`（或 `-SkipMinio` 跳过） |
| Nacos | `start-project` / `start-r-min` 本机启 | 同上 |

#### Python 影像 AI（P4 · 单独启）

| 文件 | 作用 |
|------|------|
| [scripts/setup-hospital-ai.ps1](../scripts/setup-hospital-ai.ps1) | 首次：创建 `hospital-ai/.venv` 并安装 GPU PyTorch |
| [scripts/start-hospital-ai.ps1](../scripts/start-hospital-ai.ps1) | **仅启 Python CNN** `:8000`（uvicorn，日志 `logs/hospital-ai/`） |
| [scripts/stop-hospital-ai.ps1](../scripts/stop-hospital-ai.ps1) | **仅停** `:8000`（不影响 Java / 前端） |
| [scripts/start-r-pacs-ai.bat](../scripts/start-r-pacs-ai.bat) | 旧版：硬编码路径，启 Java + Python；**推荐** `start-project` + `start-hospital-ai` |

**CNN 演示推荐顺序**：

```powershell
.\scripts\start-project.ps1              # Java 全栈（含 pacs :9104、ai-bridge :9106）
.\scripts\start-hospital-ai.ps1        # 另启 Python :8000
# 停止 CNN：.\scripts\stop-hospital-ai.ps1
```

环境变量 `HOSPITAL_AI_BASE_URL=http://127.0.0.1:8000` 已在 `env-*.ps1` 中配置；**pacs** 通过该地址异步调 CNN。

#### 验收 / 运维辅助

| 文件 | 作用 |
|------|------|
| `scripts/r-*-acceptance.ps1` | 各模块自动化验收（经 Gateway 9000） |
| [scripts/seed-demo-check.ps1](../scripts/seed-demo-check.ps1) | 向云/本机 PG 灌影像演示数据（`check_request` #62001） |
| `docs/sql/seed-demo-patients.sql` | 演示患者 `MR202606040100` + 今日内科挂号（小程序联调） |
| [DEMO_MEDICAL_RECORD_SAMPLES.md](./DEMO_MEDICAL_RECORD_SAMPLES.md) | **医生站病历/AI 诊断**：三则可直接复制的专业文案 |
| `docs/sql/seed-dict.sql` §药品/医技 | **20 种药品 + 34 项医技**（对齐 RAG DRUG/TECH 指南，可 AI 开单） |
| [scripts/miniapp-smoke.ps1](../scripts/miniapp-smoke.ps1) | 患者小程序 API 冒烟（6 项） |
| [scripts/stop-r-min.ps1](../scripts/stop-r-min.ps1) | 停止 `start-r-min` 拉起的进程 |

**运维改云 IP**：只改 `scripts/env-cloud.ps1` 里的 `$script:HospitalCloudHost`。

---

## 一、先搞清楚：三层东西

| 层 | 跑什么 | 何时需要 |
| --- | --- | --- |
| **基础设施** | PostgreSQL、Nacos、MinIO（+ 可选 Redis） | 几乎每次联调 Java 都要；**云端展示**时 PG/MinIO 在 ECS，本机只启 Nacos |
| **Java 微服务** | gateway、auth、his、lis、pacs… | 按阶段 **R-min / R-lis / R-pacs** 递增启动 |
| **客户端** | PC 前端 `npm run dev`、微信开发者工具 | 做页面时需要 |

**对外唯一 HTTP 入口**：Gateway **`http://127.0.0.1:9000`**（前端、小程序、Postman 都只连它，不要直连 9101～9106）。

---

## 二、一次性准备（新同学首次）

按 [DEV_ENV_SETUP.md](./DEV_ENV_SETUP.md) 完成：

| 项 | 验证命令 |
| --- | --- |
| JDK 17 | `java -version` |
| Maven 3.9.x | `mvn -version` |
| PostgreSQL 16 | `psql -U postgres -d hospital -c "SELECT 1"` |
| 建表 + 种子数据 | 已跑 `docs/sql/schema.sql`、`seed-dict.sql` |
| Nacos 2.2.3 | 解压于 `D:\dev\nacos` |
| MinIO | `D:\dev\minio\minio.exe` + **本人** `minio.license`（勿提交 Git） |

克隆仓库：

```cmd
cd /d C:\Users\你的用户名\Desktop
git clone https://github.com/<你的用户名>/NST.git
cd NST
```

编译后端（验证 Maven）：

```cmd
cd hospital-backend
mvn -q -DskipTests package
```

---

## 三、每次开发：推荐启动顺序

> **日常请直接看 §零**。本节为分步说明。团队默认目录 **`D:\dev\`**。

### 步骤 0：快速自检（30 秒）

**本地模式**（`-EnvProfile local`）：

```powershell
psql -U postgres -d hospital -c "SELECT 1"
curl -s http://127.0.0.1:8848/nacos/v1/console/health/readiness
```

**云端模式**（`-EnvProfile cloud`）：

```powershell
psql -h 123.57.206.134 -U postgres -d hospital -c "SELECT 1"
curl -s http://127.0.0.1:8848/nacos/v1/console/health/readiness
```

影像联调再加：`curl -s http://123.57.206.134:9001/minio/health/live`（云 MinIO）或本机 `127.0.0.1:9001`（本地 MinIO）。

任一项失败 → 先启动对应基础设施（§四）。

### 步骤 1～3：基础设施

见 **§四**（PostgreSQL 服务、Nacos、MinIO）。

### 步骤 4：Java 微服务（按当前阶段）

见 **§五**（R-min 起）；在 **IDEA** 中运行各 `*Application`，或 `java -jar`（服务实现后）。

### 步骤 5：客户端（按需）

| 客户端 | 命令 / 操作 | 访问地址 |
| --- | --- | --- |
| PC 前端 | `cd hospital-frontend` → `npm run dev` | 见终端（通常 `http://localhost:5173`） |
| 患者小程序 | 微信开发者工具导入 `hospital-patient-miniapp/` | 工具内预览 |
| 仅测 API | Postman / curl | `http://127.0.0.1:9000/api/v1/...` |

---

## 四、基础设施怎么开

### 4.1 PostgreSQL

- **方式**：Windows 服务 **`postgresql-x64-16`**（services.msc 设为自动或手动启动）
- **库名**：`hospital`
- **账号**：`postgres` / `123456`

无需每次开 CMD；服务在跑即可。

### 4.2 Nacos（注册中心 · 8848）

```cmd
cd /d D:\dev\nacos\bin
startup.cmd -m standalone
```

或：

```cmd
D:\dev\nacos\bin\startup.cmd -m standalone
```

- **控制台**：http://127.0.0.1:8848/nacos（`nacos` / `nacos`；未开鉴权时可能免登录）
- **停止**：同目录 `shutdown.cmd`
- **等待**：启动后 **30～60 秒** 再开 Java 服务

### 4.3 MinIO（对象存储 · API 9001 / 控制台 9002）

**推荐**：`.\scripts\start-project.ps1` 会在 **local** 模式下自动启动本机 MinIO（`:9001` 未监听时）；**cloud** 模式仅探测 `env-cloud.ps1` 中的远程 MinIO。不测影像时可加 `-SkipMinio`。

**手动启动**（与脚本等价，见 DEV_ENV_SETUP §6.3）：

**必须先有本机 `D:\dev\minio\minio.license`**（AIStor 版；每人各自申请）。若使用社区版 `minio-community.exe` 则无需 license。

```cmd
D:\dev\minio\start-minio.bat
```

或手动：

```cmd
cd /d D:\dev\minio
set MINIO_ROOT_USER=minioadmin
set MINIO_ROOT_PASSWORD=minioadmin123
minio.exe server D:\dev\minio-data --license D:\dev\minio\minio.license --address ":9001" --console-address ":9002"
```

- **控制台**：http://127.0.0.1:9002（`minioadmin` / `minioadmin123`）
- **P1～P2**：可不启 MinIO；**P3 影像 / P4 AI** 需要
- **停止**：关闭 MinIO 的 CMD 窗口

### 4.4 Redis（可选）

P1～P3 可不启。需要时确保 `redis-cli ping` → `PONG`。

### 4.5 阿里云 ECS（云端 PostgreSQL + MinIO）

> **云端展示**时使用；组员本机 **不必** 安装 PostgreSQL / MinIO。  
> **运维**（ECS Workbench）日常确认：

```bash
cd /opt/hospital
docker-compose ps
```

两者应为 **Up (healthy)**。ECS 重启后若容器未起来：

```bash
cd /opt/hospital
docker-compose up -d postgres minio
```

| 组件 | 地址 | 账号 |
|------|------|------|
| PostgreSQL | `123.57.206.134:5432` / 库 `hospital` | `postgres` / 见 `env-cloud.ps1` 中 `DB_PASSWORD` |
| MinIO API | `http://123.57.206.134:9001` | `minioadmin` / `minioadmin123` |
| MinIO 控制台 | `http://123.57.206.134:9002` | 同上 |
| 影像桶名 | `imaging`（见 [IMAGING_DATA_ACCESS.md](./IMAGING_DATA_ACCESS.md)） | — |

本机 Java 通过 `scripts/env-cloud.ps1` 连接上述地址；**Nacos 仍用本机** `127.0.0.1:8848`。

> **云 PG 密码 ≠ 本机 PG 密码**：本机 `env-local.ps1` 仍为 `123456`；云端在 `env-cloud.ps1` 单独配置（勿与业务登录 `doctor01/123456` 混淆）。

**首次 / 重建云库**（ECS Workbench，密码见 `/opt/hospital/.env` 中 `POSTGRES_PASSWORD`）：

```bash
cd /opt/hospital
mkdir -p sql
# 本机 scp：schema.sql、seed-dict.sql → /opt/hospital/sql/
docker exec -i hospital-postgres psql -U postgres -d hospital < sql/schema.sql
docker exec -i hospital-postgres psql -U postgres -d hospital < sql/seed-dict.sql
# 可选演示：seed-demo-patients.sql（测试患者+挂号）、seed-demo-check.sql（影像 #62001）
# docker exec -i hospital-postgres psql -U postgres -d hospital < sql/seed-demo-patients.sql
# docker exec -i hospital-postgres psql -U postgres -d hospital < sql/seed-demo-check.sql
```

**导入后验收**（登录数据在 `sys_user` + `employee`，**无** `staff_account` 表）：

```bash
docker exec -it hospital-postgres psql -U postgres -d hospital -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
docker exec -it hospital-postgres psql -U postgres -d hospital -c "SELECT id, username, user_type FROM sys_user ORDER BY id;"
docker exec -it hospital-postgres psql -U postgres -d hospital -c "SELECT u.username, e.real_name, e.role_type FROM sys_user u JOIN employee e ON e.id = u.employee_id ORDER BY u.id;"
```

若出现 `readme_to_recover` 等异常库，说明公网 5432 曾被扫：停 PG → 删数据卷 → 改强密码 → 按上重建（详见运维记录或团队口头说明）。

---

## 五、Java 微服务：跑什么、什么端口

### 5.1 端口一览

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| **hospital-gateway** | **9000** | **唯一对外 HTTP** |
| hospital-auth | 9101 | 登录、JWT 签发 |
| hospital-his | 9102 | 患者/门诊/收费/药房 |
| hospital-lis | 9103 | 检验 |
| hospital-pacs | 9104 | 检查/影像 |
| hospital-disposal | 9105 | 门诊处置执行 |
| hospital-management | 9107 | 管理/字典/排班 |
| hospital-ai-bridge | 9106 | Spring AI（P4） |
| hospital-ai（Python） | 8000 | CNN 推理（P4，不经 Gateway） |

`hospital-common` 是依赖 jar，**不单独启动**。

### 5.2 按阶段启动组合（权威）

与 [MICROSERVICES.md](./MICROSERVICES.md) §6.2、本文 **§十二** 一致：

| 组合 | 需启动的 Java 进程 | 基础设施 | 能演示什么 |
| --- | --- | --- | --- |
| **R-min**（P1） | gateway + auth + **his** + management（或仅 seed，见 ADR-012） | PG + Nacos | 医护/患者登录、挂号、接诊、病历 |
| **R-lis**（P2） | R-min + **lis** | 同上 | + 检验开单与结果 |
| **R-disposal**（P2～P3） | R-min + **disposal** | 同上 | + 处置开单与结果 |
| **R-pacs**（P3） | R-min + **pacs** | + **MinIO** | + 检查、影像上传 |
| **R-full**（P4） | R-pacs + **ai-bridge** + **hospital-ai** | + pgvector（可选） | + AI 问诊、CNN |

**推荐启动顺序**（Java 部分）：

```text
auth (9101) → management (9107) → his (9102) → [lis] → [pacs] → [disposal] → [ai-bridge] → gateway (9000) 最后
```

Gateway 放最后，避免前端连上时后端路由未就绪。

### 5.3 IDEA 本地运行（推荐）

1. **Open** 仓库根目录 `NST`，识别 Maven 多模块  
2. **Project SDK**：JDK **17**  
3. **Environment variables**：复制 [scripts/env-cloud.ps1](../scripts/env-cloud.ps1) 或 [env-local.ps1](../scripts/env-local.ps1) 中的变量到 Run Configuration 模板（**勿用 `PG_HOST`**，代码读取 **`DB_HOST`**）：

```text
DB_HOST=127.0.0.1          # 云端展示改为 123.57.206.134
DB_PORT=5432
DB_NAME=hospital
DB_USER=postgres
DB_PASSWORD=...            # local：123456；cloud：见 env-cloud.ps1
NACOS_SERVER_ADDR=127.0.0.1:8848
```

pacs 等还需：`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET=imaging`。

4. 按 §5.2 组合逐个 **Run**；Nacos 控制台「服务列表」应出现对应服务名。

### 5.4 命令行 jar 启动（可选，服务打包后）

```cmd
cd /d C:\Users\你的用户名\Desktop\NST\hospital-backend\hospital-gateway\target
set NACOS_SERVER_ADDR=127.0.0.1:8848
java -jar hospital-gateway-*.jar
```

其他模块同理，**先 cd 到对应 `target` 目录**。

### 5.5 当前代码状态

实现进度与模块就绪情况以 **[PROGRESS.md](./PROGRESS.md)** 为准（勿依赖本节静态表格）。

自动化验收：`scripts/r-min-acceptance.ps1`、`r-lis-acceptance.ps1`、`r-pacs-acceptance.ps1`、`r-pharmacy-acceptance.ps1`、`r-reversal-acceptance.ps1`。
---

## 六、客户端怎么开

### 6.1 PC 前端（医生 / 管理）

```cmd
cd /d C:\Users\你的用户名\Desktop\NST\hospital-frontend
npm install
npm run dev
```

- 浏览器打开 Vite 提示的地址（通常 **http://localhost:5173**）
- API 应指向 Gateway：`http://127.0.0.1:9000/api/v1`（见前端 env 配置）
- 测试账号（seed）：见 [sql/README.md §三](./sql/README.md#三测试账号seed-写入后)（如 `doctor01` / `123456`）→ `POST /auth/staff/login`

### 6.2 患者微信小程序

1. 启动 R-min：`.\scripts\start-r-min.ps1`（Nacos + auth + his + gateway）
2. 验收：`.\scripts\miniapp-smoke.ps1`（6 项患者 API 经 Gateway）
3. 小程序目录复制联调配置：`copy hospital-patient-miniapp\config.local.example.js hospital-patient-miniapp\config.local.js`（`USE_MOCK: false`）
4. 打开 **微信开发者工具**，导入 **`hospital-patient-miniapp/`**
5. **详情 → 本地设置** → 勾选「不校验合法域名」
6. 登录页填写本人档案 → 微信授权并登录

号源为空时执行：`psql -U postgres -d hospital -f docs\sql\patch-scheduling-refresh.sql`

详见 [hospital-patient-miniapp/README.md](../hospital-patient-miniapp/README.md)、[DEV_ENV_SETUP.md §九](./DEV_ENV_SETUP.md)

### 6.3 只用 Postman / curl 测后端

Base URL：**`http://127.0.0.1:9000`**

示例（R-min 实现后）：

```cmd
curl -X POST http://127.0.0.1:9000/api/v1/auth/staff/login -H "Content-Type: application/json" -d "{\"username\":\"doctor01\",\"password\":\"123456\"}"
```

完整场景见 **§十二**；或运行 `scripts/r-min-acceptance.ps1`。
---

## 七、怎么确认「都起来了」

| 检查项 | 命令或 URL | 期望 |
| --- | --- | --- |
| PostgreSQL | `psql -U postgres -d hospital -c "SELECT 1"` | 返回 1 |
| Nacos | http://127.0.0.1:8848/nacos | 控制台可开 |
| MinIO | http://127.0.0.1:9002 | 控制台可登录 |
| Gateway | http://127.0.0.1:9000 | 有响应（非连接拒绝） |
| Nacos 服务列表 | 控制台 → 服务管理 | 已启的 Java 服务已注册；答辩 LB 时 **hospital-his 可为 2 实例**（9102+9202） |
| 端口占用 | `netstat -ano ^| findstr :9000` | gateway 在监听 |

---

## 八、怎么停

| 组件 | 停法 |
| --- | --- |
| Java 服务 | IDEA Stop，或 `.\scripts\stop-project.ps1`（含 9202 副本） |
| his 副本（仅 9202） | `.\scripts\stop-his-replica.ps1` |
| MinIO | 关闭 MinIO CMD 窗口 |
| Nacos | `D:\dev\nacos\bin\shutdown.cmd` |
| PostgreSQL | 一般 **不要停**（Windows 服务保持运行） |
| 前端 | 终端 `Ctrl+C` |

---

## 九、按角色：最少要开什么

| 角色 | 每次至少启动 |
| --- | --- |
| **Java 后端** | PG + Nacos + 当前阶段 Java 组合（§5.2） |
| **PC 前端** | 同上 + `npm run dev` |
| **小程序** | 同上 + 微信开发者工具 |
| **只做 SQL/文档** | 仅 PostgreSQL |
| **P4 算法** | R-full + `hospital-ai`（Python，端口 8000） |

---

## 十、常见问题

| 现象 | 处理 |
| --- | --- |
| 9000 被占用 | MinIO 误用 9000 → 改 **9001/9002**；`netstat -ano ^| findstr :9000` 查进程 |
| Gateway 503 | Nacos 未起 / 下游服务未注册 / 路由未配 |
| 401 全部接口 | Token 未带；或 auth 未启 |
| MinIO offline | 缺 `minio.license` 或未加 `--license` |
| Nacos startup 找不到 | 用 `cd /d D:\dev\nacos\bin` |
| 前端连不上后端 | 确认 Gateway 9000 已启；检查 API 基址 |

更多环境类问题 → [DEV_ENV_SETUP.md §十五](./DEV_ENV_SETUP.md)。

---

## 十二、联调与验收清单

> 答辩/demo 前自检。自动化脚本见 `scripts/r-*-acceptance.ps1`。

### 12.1 环境准备（每次联调前）

- [ ] **环境 profile**：云端展示 `. .\scripts\env-cloud.ps1` 或 `start-r-min.ps1 -EnvProfile cloud`；本地 `. .\scripts\env-local.ps1`
- [ ] PostgreSQL：`hospital` 库已执行 **`schema.sql`（v1.14）** + `seed-dict.sql`（云库运维已导入；本地见 [sql/README.md §四](./sql/README.md)）
- [ ] Nacos standalone 运行（8848，**本机**）
- [ ] 各服务 `NACOS_SERVER_ADDR=127.0.0.1:8848`、`DB_HOST` 与 profile 一致
- [ ] Gateway **9000** 未被 MinIO 占用（MinIO 用 **9001**）

### 12.2 R-min — P1 门诊最小链

**启动进程**：auth(9101) → management(9107，可跳过) → his(9102) → gateway(9000)

| 场景 | 步骤 | 预期 |
|------|------|------|
| **A 医护登录** | `POST /auth/staff/login`（doctor01/123456） | `code=200`，含 `accessToken` |
| | `GET /auth/me` | 返回员工信息 |
| **B 患者登录** | `POST /patient/auth/wechat`（dev mock code） | Token 由 **auth** 签发（方案 C） |
| **C 挂号+支付** | `GET /patient/schedules` → `POST /patient/registers` → `POST /patient/payments` | `register.visit_state=1` |
| **D 接诊+病历** | `GET /doctor/queues` → `POST /doctor/call/{id}` → `PUT /doctor/medical-records/{id}` | `visit_state=2`，病历已存 |

**通过标准**：A～D 全部经 Gateway 9000；`scripts/r-min-acceptance.ps1` PASS。

### 12.3 R-lis — P2 检验

额外启动：hospital-lis :9103

| 步骤 | 预期 |
|------|------|
| 医生开检验 → 患者缴费 | `inspection_request.status=20` |
| `GET /lis/queue` → 录入结果 | `status=40` |
| 医生查看结果 | 可见 |

脚本：`scripts/r-lis-acceptance.ps1`

### 12.4 R-pacs / 药房 / 逆向 — P3

额外启动：hospital-pacs :9104；MinIO :9001（影像）

| 场景 | 脚本 |
|------|------|
| 检查闭环 | `scripts/r-pacs-acceptance.ps1` |
| 处方发药 | `scripts/r-pharmacy-acceptance.ps1` |
| 退号/退费/退药 | `scripts/r-reversal-acceptance.ps1` |

### 12.5 R-full — P4（可选）

- [ ] ai-bridge `/api/v1/ai/**` 可 STUB 或真 SSE
- [ ] pacs → hospital-ai :8000 异步；停 AI 后门诊仍可用
- [ ] 影像任务失败写 `imaging_study.FAILED`，不拖垮 his

### 12.6 联调常见问题

| 现象 | 排查 |
|------|------|
| 401 全接口 | Token 未带 / Gateway 未配白名单 |
| 503 路由不到 | Nacos 未注册 / Gateway routes 未配 |
| 挂号无排班 | 未跑 seed 或 `publish_status≠1` |
| 登录无 patient | his 未 Feign auth 或 auth internal 未启 |
| 支付后 visit_state 不变 | bill/register 联动事务未提交 |
| 挂号一直「已挂号」 | 当日 21:00 后 his 定时任务自动关单；重启 his 会补偿执行 |
| 待支付占号未释放 | 10 分钟未付自动取消；需 **hospital-his** 运行中 |

---

## 十三、相关文档

| 文档 | 何时看 |
| --- | --- |
| [DEV_ENV_SETUP.md](./DEV_ENV_SETUP.md) | 装软件、配 Path、MinIO license |
| [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) | 分期任务、开发动机（附录） |
| [TEAM_COLLABORATION.md](./TEAM_COLLABORATION.md) | 分工、Mock、契约变更 |
| [API.md](./API.md) | 接口路径、报文、页面速查（附录 A） |
| [API.md](./API.md) | 接口路径与报文 |
| [MICROSERVICES.md](./MICROSERVICES.md) | 服务边界、架构图、M1～M10 |

---

## 十四、修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-05 | 首版：基础设施 + 分阶段 Java 组合 + 客户端 |
| v2.0 | 2026-05 | 合并原 INTEGRATION_CHECKLIST；§5.5 改链 PROGRESS |
| v2.1 | 2026-06 | §12.1 标注 schema **v1.14** 重建说明 |
| v2.2 | 2026-06-15 | §零 每次开机速查；`env-cloud.ps1` / `env-local.ps1`；§4.5 阿里云 ECS；修正 `DB_HOST` |
| v2.3 | 2026-06-15 | `start-project.ps1` / `stop-project.ps1` 一键启停全栈 + 前端 |
| v2.4 | 2026-06-15 | §4.5 云库重建与验收 SQL（`sys_user`）；云 PG 密码与 `env-cloud.ps1` 对齐 |
| v2.5 | 2026-06-15 | §零 D 补充脚本边界（不含 Python/PG/MinIO）；`start-project` 默认 `cloud` |
| v2.6 | 2026-06-15 | 新增 `start-hospital-ai.ps1` / `stop-hospital-ai.ps1`（单独启停 CNN :8000） |
| v2.7 | 2026-06-15 | `start-project` 默认 `-EnvProfile local`（本机库；云端显式 `-EnvProfile cloud`） |
| v2.8 | 2026-06-15 | §A.1 `start-his-replica.ps1` / `stop-his-replica.ps1`（his 双实例 LB 答辩演示）；`stop-project` 含 9202 |
| v2.9 | 2026-06-15 | `start-project.ps1`：local 自动启 MinIO / cloud 远程健康检查；`-SkipMinio`、`-MinioHome` |
| v2.10 | 2026-06-04 | 测试账号链至 `sql/README.md` §三；§4.5 补充 `seed-demo-patients.sql` / 云库重跑 seed 说明 |
| v2.12 | 2026-06-04 | 挂号生命周期：未叫号可退号；待支付 10 分钟超时；当日 21:00 自动关单（`remark=AUTO_DAY_CLOSE`） |