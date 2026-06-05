# 智慧云脑诊疗平台 — 设计决策记录（ADR）

> **文档性质**：已拍板的技术与实现选择；编码与评审以本文为准。  
> **文档索引**：[README.md](./README.md)  
> **版本**：v1.5 | 2026-06
> **状态图例**：**已定稿** | **待定（P4+）**

---

## 一、决策总览

| ID | 主题 | 状态 | 摘要 |
|----|------|------|------|
| ADR-001 | 患者微信登录与 Token 签发 | **已定稿** | **方案 C**：his 微信+落库；**auth 统一签发** 医护/患者 Token |
| ADR-002 | 医学影像上传入口 | **已定稿** | **pacs** 接收上传；his 只读结果 |
| ADR-003 | 开立检验/检查后的协同 | **已定稿** | his 写申请单行 + **Feign 通知** lis/pacs |
| ADR-004 | 医技 API 路径演进 | **已定稿** | P2 起新接口用 `/lis/**`、`/pacs/**` |
| ADR-005 | lis/pacs 读患者摘要 | **已定稿** | **共享库只读** + Feign 仅用于通知/可选校验 |
| ADR-006 | 处置业务归属 | **已定稿** | 开立与结果录入均在 **his**（处置科 PC 菜单） |
| ADR-007 | 影像/AI 结果推送 | **已定稿** | 首期 **HTTP 轮询** |
| ADR-008 | Redis | **已定稿** | **P1 不依赖**；后期可选 |
| ADR-009 | 微信支付 | **已定稿** | 开发期 **模拟支付**；生产再接微信回调 |
| ADR-010 | AI 会话存储 | **待定（P4）** | P4 前 bridge **STUB**；存储方案届时二选一 |
| ADR-011 | CNN 算法交付 | **已定稿** | FastAPI 契约先通；模型权重与效果 **单独里程碑** |
| ADR-012 | P1 字典数据 | **已定稿** | **P1 即启 management** 或提供 `seed-dict.sql` |
| ADR-013 | 建表脚本 | **已定稿** | **P0.5 优先** 产出 `docs/sql/schema.sql` |
| ADR-014 | 项目命名 | **已定稿** | 仓库 **NST** = Nexus Smart Treatment |
| ADR-015 | AI 辅助开检查/检验/处置 | **已定稿** | **方案 A** `diagnosis/suggest` 分支 + **方案 B** ai-draft 三步（与处方对称） |

---

## 二、ADR-001 患者微信登录与 Token 签发（已定稿 · 方案 C）

### 2.1 定稿结论

| 职责 | 服务 |
|------|------|
| 微信 `code` 换 `openid`、创建/更新 `patient` / `patient_wechat` | **hospital-his** |
| **签发** 患者 JWT、医护 JWT、刷新 Token | **hospital-auth**（**唯一签发方**） |
| JWT 解析、Gateway 校验、`userType` 约定 | **hospital-common**（或 auth 暴露校验接口） |

**auth 不写入** `patient`、`patient_wechat` 表。

### 2.2 对外流程（小程序）

```text
小程序  wx.login() → code
    → POST /api/v1/patient/auth/wechat   （Gateway → his）
    → his：调微信接口 + 落库 patient / patient_wechat
    → his：Feign → auth  POST /internal/token/patient
    ← auth：accessToken（+ 可选 refreshToken）
    ← his：将 Token 与 patientId、病历号等一并返回小程序
```

### 2.3 内部接口（不经 Gateway）

| 调用方 | 被调方 | 方法与路径 | 说明 |
|--------|--------|------------|------|
| hospital-his | hospital-auth | `POST /internal/token/patient` | 请求体含 `patientId` 等；**仅内网**，服务间鉴权（如内部 Header / mTLS 后期可选） |
| hospital-auth | — | — | 校验 `sys_user` 后签发 **STAFF** Token（现有 `POST /auth/staff/login`） |

### 2.4 Token 载荷（与 `API.md` §2.1 一致）

- 患者：`type=PATIENT`，`sub=patientId`
- 医护：`type=STAFF`，`sub=userId`，`roles[]`
- **issuer / 签名密钥仅配置在 auth**（common 可提供 DTO 与常量，避免 his 本地签发）

### 2.5 网关白名单

- `POST /api/v1/auth/staff/login` — 医护登录（auth）
- `POST /api/v1/patient/auth/wechat` — 患者登录入口（**his**，见 `API.md` §4.0）
- ~~`POST /api/v1/auth/patient/wechat/login`~~ — **废弃**（勿在 auth 上暴露患者微信登录）

### 2.6 故障与依赖

- **auth 不可用**：患者无法完成登录（新会话）；已持有 Token 在过期前仍可由 Gateway 校验（若 Gateway 本地验签或缓存公钥）。
- **his 不可用**：患者无法登录、无法办理挂号；医护 Token 若已签发不受影响。

### 2.7 答辩口径（一句话）

**身份与档案在 HIS，令牌在认证中心 auth 统一签发；患者表仍由 his 独占写入。**

---

## 三、其它已定稿决策（详述）

### ADR-002 医学影像上传入口

- **定稿**：`wx.uploadFile` → Gateway → **`/api/v1/pacs/**`**（pacs 鉴权、写 MinIO、建 `imaging_study`、异步调 `hospital-ai`）。
- his 仅提供检查单关联查询、报告展示（读 pacs 写入的结果）。

### ADR-003 开立检验/检查后的协同

- his 在事务内：创建 `inspection_request` / `check_request`（开立态）+ 关联 `bill`（按业务）。
- 同事务或事务提交后：**Feign 通知** lis/pacs（激活队列），**禁止** lis/pacs 侧改「开立」字段。
- 执行与结果状态 **仅 lis/pacs 可写**。

### ADR-004 医技 API 路径演进

- **P2 起**：检验新接口统一 **`/api/v1/lis/**`**；检查/影像 **`/api/v1/pacs/**`**。
- 旧文档中 `/doctor/inspections`、`/doctor/checks` 等可保留一段时间，Gateway **双路由** 至同一服务，前端逐步迁移。

### ADR-005 lis/pacs 读患者/挂号摘要

- **定稿**：允许 JDBC **只读** `patient`、`register` 等（共享库）；**禁止 UPDATE** 他服务主写表。
- Feign 用于：通知、可选的业务校验（如「挂号是否属于该患者」），非默认读路径。

### ADR-006 处置业务归属

- **定稿**：不增设第四微服务。
- `disposal_request`：**his 开立**；处置科在 PC 端用 **处置医生角色** 录入结果（仍在 **his** 进程，菜单属医技/处置模块）。
- 状态机与检查/检验相同（见 `BUSINESS_FLOW.md` §八）。

### ADR-007 影像/AI 结果通知

- **定稿**：首期 **前端轮询**（如 `GET /patient/imaging-studies/{id}` 或医生端等价接口）。
- SSE/WebSocket 列为 P4+ 增强，不阻塞 P3。

### ADR-008 Redis

- **定稿**：**P1～P3 不依赖 Redis**；网关限流、Token 黑名单若做，放在 P4 或之后。

### ADR-009 微信支付

- **开发/答辩**：`POST /patient/payments` 可走 **模拟成功** 或测试开关，直接推进 `payment_record` 与单据状态。
- **生产**：`callback/wechat/pay` 路由至 **his**；需 HTTPS 与商户配置（`DEV_ENV_SETUP` 说明）。

### ADR-010 AI 会话存储（P4）

- P1～P3：`hospital-ai-bridge` 返回 **STUB**（50301 或固定文案）。
- P4 再定：业务表 `ai_chat_session` **或** Spring AI 内置存储（二选一）。

### ADR-011 CNN 算法交付

- Java/Python **接口契约** 按 `API.md` §八 先实现通。
- **模型文件、推理耗时、演示数据集** 单独立项（算法同学），不阻塞门诊主链验收。

### ADR-012 P1 字典数据

- **定稿**：**P1 联调启动 `hospital-management`**（R-min 含 management），**或** 提供 `docs/sql/seed-dict.sql`（科室、号别、少量医生/排班）。
- 避免 his 挂号无科室、无号别可挂。

### ADR-013 建表脚本

- **定稿**：**P0.5 必须交付** `docs/sql/schema.sql`（由 `DATABASE_DESIGN.md` 导出，PostgreSQL 方言）。
- 可选：`seed-dict.sql`、P4 `vector.sql`。

### ADR-014 项目命名（已定稿）

| 层级 | 名称 | 说明 |
|------|------|------|
| **中文产品名** | 智慧云脑诊疗平台 | 答辩、界面标题、需求文档 |
| **英文仓库/代号** | **NST** | **N**exus **S**mart **T**reatment（枢纽智能诊疗） |
| **GitHub 仓库名** | `NST` | clone 后默认目录 `NST/` |
| **培训背景** | NEU Software Training | 东软公司实训项目 |
| **Maven artifactId** | `nst` | 小写，与仓库名对应 |

- **定稿**：GitHub 仓库、文档、clone 路径统一 **NST**；README 首段注明全称 Nexus Smart Treatment。

---

## 四、ADR-015 AI 辅助开检查/检验/处置（已定稿）

### 4.1 定稿结论

| 环节 | 方案 | 接口 |
|------|------|------|
| **是否开单**（分支判断） | **方案 A** | `POST /api/v1/ai/diagnosis/suggest` → `needCheck` / `needInspection` / `needDisposal` |
| **开什么单**（草稿→确认） | **方案 B** | 与处方对称：`POST …/ai-draft` → `PUT …/ai-draft/{id}` → `POST …/ai-draft/{id}/confirm` |
| 适用类型 | 检查、检验、处置 | 前缀见 `API.md` §5.3～5.5 |
| 处方 | 已有 | `API.md` §5.6 ai-draft 三步 |

**原则**（与 `BUSINESS_FLOW.md` 补-24、补-25 一致）：

- AI 只产出 **建议 / 草稿**；**医生可改、须确认后提交** 方为「已开立」。
- **禁止** ai-bridge 或 LLM **直接写** `check_request` / `inspection_request` / `disposal_request` 为已开立。
- 确认提交后由 **hospital-his** 落库并 Feign 通知 lis/pacs（ADR-003）；处置仅在 his（ADR-006）。

### 4.2 调用链（定稿）

```text
PC 医生
  → Gateway :9000
  → hospital-his
       ├─ POST /doctor/check-requests/ai-draft 等
       │     → Feign hospital-ai-bridge（Spring AI 结构化 JSON）
       │     → his 写 ai_*_draft
       ├─ PUT  …/ai-draft/{draftId}   （医生编辑，仅 his）
       └─ POST …/ai-draft/{draftId}/confirm → 正式申请单 + bill（fromAi=true）

分支入口（可选、先于草稿）：
  → POST /api/v1/ai/diagnosis/suggest（Gateway → ai-bridge）
  → 前端据 needCheck/needInspection/needDisposal 展示「AI 生成××草稿」按钮
```

### 4.3 分工（实现期）

| 角色 | 职责 |
|------|------|
| **lml** | `hospital-ai-bridge`：`diagnosis/suggest`、生成草稿 JSON 的 Spring AI Prompt |
| **zcl** | `API.md` 契约、his 草稿 CRUD + confirm、医生 PC 交互 |
| **lzr** | 缴费后 lis/pacs 队列（不改开单） |
| **wsh** | CNN 影像报告（**开单之后** 的检查环节，非本 ADR） |

### 4.4 数据表（P4 前可 STUB）

与 `ai_prescription_draft` 对称，his 侧新增草稿表（实现时二选一）：

- 分表：`ai_check_draft` / `ai_inspection_draft` / `ai_disposal_draft`，或
- 统一 `ai_clinical_draft`（`draft_type` = CHECK | INSPECTION | DISPOSAL）

`draft_content` 存 §5.3.2 的 `items[]` + `aiReason` JSONB。

---

## 五、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05 | 首版；ADR-002～013 已定稿；ADR-001 讨论中 |
| v1.1 | 2026-05 | **ADR-001 定稿为方案 C**（his 微信+落库，auth 统一签发 Token） |
| v1.2 | 2026-05 | **ADR-014** 项目命名：CloudBrain-Hospital + 本地简称 NST（东软培训） |
| v1.3 | 2026-05 | **ADR-014 修订**：GitHub 仓库定名 NTS（Neural Treatment System） |
| v1.4 | 2026-05 | **ADR-014 修订**：仓库定名 **NST**（Nexus Smart Treatment） |
| v1.5 | 2026-06 | **ADR-015** AI 辅助开单：diagnosis/suggest + ai-draft 三步 |
