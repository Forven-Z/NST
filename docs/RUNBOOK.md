# 智慧云脑诊疗平台 — 启动、联调与验收手册

> **版本**：v2.1 | 2026-06  
> **用途**：日常 **开什么、怎么开**；**R-min～R-full 联调验收**（原 INTEGRATION_CHECKLIST 已并入本文 §十二）。  
> **环境安装**（首次装软件）：见 [DEV_ENV_SETUP.md](./DEV_ENV_SETUP.md)  
> **实现进度**：见 [PROGRESS.md](./PROGRESS.md)  
> **任务与动机**：见 [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)
---

## 一、先搞清楚：三层东西

| 层 | 跑什么 | 何时需要 |
| --- | --- | --- |
| **基础设施** | PostgreSQL、Nacos、MinIO（+ 可选 Redis） | 几乎每次联调 Java 都要 |
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

> 团队默认目录 **`D:\dev\`**；以下命令在 **CMD** 中执行（PowerShell 亦可，注意语法差异）。

### 步骤 0：快速自检（30 秒）

```cmd
psql -U postgres -d hospital -c "SELECT 1"
curl -s http://127.0.0.1:8848/nacos/v1/console/health/readiness
curl -s http://127.0.0.1:9001/minio/health/live
```

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

**必须先有本机 `D:\dev\minio\minio.license`**（每人各自申请，见 DEV_ENV_SETUP §6.3.3）。

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
3. 每个可运行模块：主类 `*Application`，**Environment variables**（可配 Run Configuration 模板）：

```text
NACOS_SERVER_ADDR=127.0.0.1:8848
PG_HOST=127.0.0.1
PG_PORT=5432
PG_DATABASE=hospital
PG_USER=postgres
PG_PASSWORD=123456
```

需要 MinIO 的服务（pacs 等）再加：

```text
MINIO_ENDPOINT=http://127.0.0.1:9001
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
```

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
- 测试账号（seed）：`doctor01` / `123456` → `POST /auth/staff/login`

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
| Nacos 服务列表 | 控制台 → 服务管理 | 已启的 Java 服务已注册 |
| 端口占用 | `netstat -ano ^| findstr :9000` | gateway 在监听 |

---

## 八、怎么停

| 组件 | 停法 |
| --- | --- |
| Java 服务 | IDEA Stop，或关闭 jar 窗口 |
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

- [ ] PostgreSQL 运行，`hospital` 库已执行 **`schema.sql`（v1.14）** + `seed-dict.sql`（旧库须 DROP SCHEMA 重建，见 [sql/README.md §四](./sql/README.md)）
- [ ] Nacos standalone 运行（8848）
- [ ] 各服务 `NACOS_SERVER_ADDR=127.0.0.1:8848`、JDBC 指向 `hospital`
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
| `GET /lis/queue` → `POST execute` | `status=30`（执行中） |
| `GET result-detail`（发布前） | 含 `criticalItems[]`（血常规 WBC 异常时非空） |
| 患者退费（execute 后） | **失败**（400，status=30 不可退） |
| `POST result` 发布 | `status=40` |
| 医生查看结果 | §1.7 全字段（`instrumentData`、`reportTime` 等） |
| 患者 `GET /patient/reports?type=lab` | 有 `summary`，**无** `instrumentData` |

**手动联调（第三阶段）**

1. **lab01** 登录 PC 检验科 → 队列选血常规 →「开始执行」→ 录入页应见仪器 STUB 与 `criticalItems` 对应异常项 → 点「发布」应弹出 **危急值确认** 对话框 → 确认后发布成功。
2. **execute 后退费负例**：患者端或小程序对同一检验账单发起退费 → 应返回错误（与 `RefundService` 一致）。
3. **doctor01** 医生工作台 → 本次就诊医嘱 → 检验「查看结果」→ 三段式只读（仪器 / AI / 医师）+ 报告时间。

脚本：`scripts/r-lis-acceptance.ps1`（自动化覆盖 E3a～E5）

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