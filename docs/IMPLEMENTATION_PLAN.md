# 智慧云脑诊疗平台 — 实施计划与任务拆解

> **用途**：成员共享、按阶段分工、对齐 Definition of Done。  
> **文档索引**：[README.md](./README.md)  
> **版本**：v2.1 | 2026-06  
> **联调验收**：见 [RUNBOOK.md](./RUNBOOK.md) §十二  
> **协作约定**：见 [TEAM_COLLABORATION.md](./TEAM_COLLABORATION.md)
---

## 一、阶段总览

| 阶段 | 演示组合 | 可演示能力 | 文档依据 |
|------|----------|------------|----------|
| **P0.5** | 基础设施 | PG + Nacos + 建表 + seed | `sql/`、`DEV_ENV_SETUP` |
| **P1** | **R-min** | 登录、挂号、接诊、病历 | 本文 §二 |
| **P2** | **R-lis** | + 检验闭环、收费完善 | 本文 §三 |
| **P3** | **R-pacs** | + 检查、处方、发药 | 本文 §四 |
| **P4** | **R-full** | + AI / CNN | `DESIGN_DECISIONS` ADR-010/011 |

**R-min 进程**：gateway + auth + **his** + **management**（或仅 seed 字典，见 ADR-012）。

---

## 二、P0.5 — 基础设施（全员前置）

| # | 任务 | 负责建议 | 产出 | 完成标准 |
|---|------|----------|------|----------|
| 0.1 | 本机 PG / Nacos 可连 | 全员 | `DEV_ENV_SETUP` 自检表打勾 | `psql`、`8848/nacos` 可访问 |
| 0.2 | 执行 `schema.sql` + `seed-dict.sql` | 后端/DB | 库 `hospital` 有表有 seed | `SELECT count(*) FROM department` ≥ 5 |
| 0.3 | Gateway 路由对齐 | 后端 | `gateway-routes.example.yml` 合入 gateway | `/api/v1/auth/**` 可达 auth |
| 0.4 | 小程序工程初始化 | 小程序 | `app.json` + `utils/request.js` | 能发请求到 `:9000` |
| 0.5 | PC 前端登录页骨架 | 前端 | `views/doctor/login` 或公共 login | 能调 `POST /auth/staff/login` |

---

## 三、P1 — R-min（门诊最小链）

### 3.1 依赖顺序（必须按序）

```text
common (Result, JWT DTO)
  → auth (staff login + internal/token/patient)
  → his (controller.patient 微信/挂号 + controller.doctor 队列病历)
  → management 只读字典 API（或直接用 seed 数据）
  → gateway 路由 + JWT 过滤器
  → 小程序 / PC 联调
```

### 3.2 后端任务

| # | 服务 | 任务 | API / 表 | 负责人 |
|---|------|------|----------|--------|
| 1.1 | common | `Result<T>`、JWT 常量、`userType` | — | |
| 1.2 | auth | 员工登录 `POST /auth/staff/login` | `sys_user`, `employee` | |
| 1.3 | auth | 内部签发 `POST /internal/token/patient` | — | |
| 1.4 | auth | Token 刷新 `POST /auth/token/refresh` | — | |
| 1.5 | his | 微信登录 `POST /patient/auth/wechat` + Feign auth | `patient`, `patient_wechat` | |
| 1.6 | his | 档案 `GET/PUT /patient/profile` | `patient` | |
| 1.7 | his | 排班列表 `GET /patient/schedules` | 读 `scheduling` | |
| 1.8 | his | 创建挂号 `POST /patient/registers` + `bill` | `register`, `bill` | |
| 1.9 | his | 模拟支付 `POST /patient/payments`（ADR-009） | `payment_*`, `register.visit_state=1` | |
| 1.10 | his | 医生队列 `GET /doctor/queues` | `register` | |
| 1.11 | his | 叫号 `POST /doctor/call/{registerId}` | `visit_state=2` | |
| 1.12 | his | 病历 `GET/PUT /doctor/medical-records/...` | `medical_record` | |
| 1.13 | management | 字典只读（可选）`GET /admin/departments` 等 | A 组表 | |
| 1.14 | gateway | 路由 + 白名单 + JWT 校验 | `gateway-routes.example.yml` | |

### 3.3 前端任务

| # | 端 | 页面/功能 | 主要 API |
|---|-----|-----------|----------|
| 1.F1 | 小程序 | 微信登录 | `POST /patient/auth/wechat` |
| 1.F2 | 小程序 | 选科室/排班、挂号 | `GET /schedules`, `POST /registers` |
| 1.F3 | 小程序 | 模拟支付 | `POST /payments` |
| 1.F4 | PC 医生 | 登录 | `POST /auth/staff/login` |
| 1.F5 | PC 医生 | 队列、叫号、病历 | `/doctor/**` |

详见 [API.md](./API.md) 附录 A。

### 3.4 P1 Definition of Done（DoD）

- [ ] **R-min** 进程均可启动，经 **Gateway :9000** 访问
- [ ] 小程序：微信登录（或 dev mock code）→ 挂号 → 模拟支付 → `register.visit_state=1`
- [ ] PC：`doctor01` 登录 → 看到队列 → 叫号 → 保存病历
- [ ] 患者 Token、医护 Token **均由 auth 签发**（方案 C）
- [ ] 进度更新至 [PROGRESS.md](./PROGRESS.md)

---

## 四、P2 — R-lis（检验 + 收费完善）

| # | 服务 | 任务 | 备注 |
|---|------|------|------|
| 2.1 | his | 开立检验 + `bill` + Feign 通知 lis | ADR-003 |
| 2.2 | lis | `GET /lis/queue`, 执行、录入结果 | `inspection_request` 写 |
| 2.3 | his | 窗口收费/退费（`controller.registrar`；Gateway `/registrar/**`） | ADR-009 可仍模拟 |
| 2.4 | 前端 | 检验科菜单 + 医生开检验单 | `/lis/**` 新路径 |

**DoD**：医生开检验 → 患者缴费 → 检验科录入结果 → 医生可见 `status=40`。

---

## 五、P3 — R-pacs（检查 + 处方 + 发药）

| # | 服务 | 任务 | 备注 |
|---|------|------|------|
| 3.1 | his | 开立检查、处方、处置 | |
| 3.2 | pacs | 检查队列、结果、`/pacs/imaging/upload` | ADR-002 |
| 3.3 | his | 药房发药 | `prescription.status=30` |
| 3.4 | MinIO | 影像 bucket | P3 检查上传 |

**DoD**：完整门诊链：挂号→看诊→开单→缴费→医技/发药（CNN 可 STUB）。

---

## 六、P4 — R-full（AI）

| # | 任务 | 文档 |
|---|------|------|
| 4.1 | ai-bridge STUB → 真 SSE | `API` §七 |
| 4.2 | hospital-ai FastAPI + pacs 异步 | `API` §八 |
| 4.3 | 影像轮询 → 可选 SSE | ADR-007 |

---

## 七、协作约定

详见 [TEAM_COLLABORATION.md](./TEAM_COLLABORATION.md)（分支、契约变更、Mock、模块认领）。

---

## 八、附录：开发动机（Why）

> 原独立文档 `DEVELOPMENT_RATIONALE.md` 已并入本节。

### 8.1 整体图景

```text
小程序 / PC → Gateway (:9000) → auth / his / lis / pacs / … → PostgreSQL + Nacos
```

**动机**：课件要求 HIS/LIS/PACS 分系统；团队需 **能分工、能独立启动、对外仍是一个 API**。

### 8.2 基础设施：为什么需要

| 组件 | 为什么 | 不开会怎样 |
|------|--------|------------|
| PostgreSQL | 业务数据 | 任何写库接口失败 |
| Nacos | Gateway `lb://` 服务发现 | Gateway **503** |
| Gateway | 统一入口 :9000 | 客户端要记住多个端口 |
| MinIO | 影像大文件 | P1 无影响；P3 上传失败 |

**R-min 联调必须开 Nacos**；仅直连某服务端口调试时可临时不开。

### 8.3 P1 后端：为什么必须这个顺序

```text
common → auth → his → [management] → gateway → 前端联调
```

| 顺序 | 原因 |
|------|------|
| common 最先 | `Result`、JWT 常量共用 |
| auth 在 his 前 | ADR-001：Token **只能 auth 签** |
| management 可后做 | seed 已灌字典，his 可先只读库 |

### 8.4 P1 任务动机摘要

| 任务 | 不做会怎样 |
|------|------------|
| 1.2 员工登录 | 医护端无法登录 |
| 1.3 internal 患者 Token | 方案 C 不成立 |
| 1.5 微信登录 | 小程序无身份 |
| 1.8 挂号 + bill | 无挂号、无费用 |
| 1.9 模拟支付 | 医生队列永远空 |
| 1.11 叫号 | 无法进入接诊中 |
| 1.14 Gateway JWT | 只能直连 910x，违背设计 |

### 8.5 P2 / P3 / P4 阶段动机

| 阶段 | 为什么单独一阶段 |
|------|------------------|
| P2 R-lis | LIS 独立进程；检验结果由 **lis** 写入 |
| P3 R-pacs | 检查 + MinIO + 发药 |
| P4 R-full | Python CNN / LLM；**停 AI 门诊仍可用** |

### 8.6 开发期可「偷懒」场景

| 可暂时不做 | 验收前必须补 |
|------------|--------------|
| 不开 Gateway，直连 9101 | 经 9000 联调 |
| 不开 MinIO / Redis | P3/P4  respectively |
| 不做 management API | 至少跑 `seed-dict.sql` |

---

## 九、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05 | 首版 P0.5～P4 任务与 P1 DoD |
| v2.0 | 2026-05 | 合并 DEVELOPMENT_RATIONALE；协作约定链 TEAM_COLLABORATION |
| v2.1 | 2026-06 | §3.1 HIS 包命名与 `MICROSERVICES.md` §2.3 扁平 `controller.*` 对齐 |