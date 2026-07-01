# 智慧云脑诊疗平台 — 设计决策记录（ADR）

> **文档性质**：已拍板的技术与实现选择；编码与评审以本文为准。  
> **文档索引**：[README.md](./README.md)  
> **版本**：v2.5 | 2026-07-01
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
| ADR-006 | 处置业务归属 | **已修订** | 开立与缴费状态在 **his**；执行与结果在 **`hospital-disposal`**（见 ADR-017） |
| ADR-007 | 影像/AI 结果推送 | **已定稿** | 首期 **HTTP 轮询** |
| ADR-008 | Redis | **已定稿** | **P1 不依赖**；后期可选 |
| ADR-009 | 微信支付 | **已定稿** | 开发期 **模拟支付**；生产再接微信回调 |
| ADR-010 | AI 会话存储 | **待定（P4）** | P4 前 bridge **STUB**；存储方案届时二选一 |
| ADR-011 | CNN 算法交付 | **已定稿** | FastAPI 契约先通；模型权重与效果 **单独里程碑** |
| ADR-012 | P1 字典数据 | **已定稿** | **P1 即启 management** 或提供 `seed-dict.sql` |
| ADR-013 | 建表脚本 | **已定稿** | **P0.5 优先** 产出 `docs/sql/schema.sql` |
| ADR-014 | 项目命名 | **已定稿** | 仓库 **NST** = Nexus Smart Treatment |
| ADR-015 | AI 辅助开检查/检验/处置 | **已定稿** | **方案 A** `diagnosis/suggest` 分支 + **方案 B** ai-draft 三步（与处方对称） |
| ADR-016 | 就诊人/家属业务模型 | **已定稿** | **方案 A**：JWT=操作者；`visitPatientId`=当前就诊人；本人不进 link |
| ADR-017 | 处置微服务拆分 | **已定稿** | 镜像 LIS/PACS：`hospital-disposal`（:9105）负责队列/执行/结果 |
| ADR-018 | HIS 领域设计模式重构 | **实施中** | ①～④ **代码已落地**，待验收；详见 [REFACTORING v2.5](./REFACTORING_DESIGN_PATTERNS.md) |
| ADR-019 | HIS 三拆（patient / clinical / pharmacy） | **已定稿** | 自 `hospital-his` 拆出 **patient + pharmacy**；缴费 **Feign 方案 A**；见 §七 |

---

## 二、ADR-001 患者微信登录与 Token 签发（已定稿 · 方案 C）

### 2.1 定稿结论

| 职责 | 服务 |
|------|------|
| 微信 `code` 换 `openid`、创建/更新 `patient` / `patient_wechat` | **hospital-patient**（ADR-019） |
| **签发** 患者 JWT、医护 JWT、刷新 Token | **hospital-auth**（**唯一签发方**） |
| JWT 解析、Gateway 校验、`userType` 约定 | **hospital-common**（或 auth 暴露校验接口） |

**auth 不写入** `patient`、`patient_wechat` 表。

### 2.2 对外流程（小程序）

```text
小程序  wx.login() → code
    → POST /api/v1/patient/auth/wechat   （Gateway → patient，ADR-019）
    → patient：调微信接口 + 落库 patient / patient_wechat
    → patient：Feign → auth  POST /internal/token/patient
    ← auth：accessToken（+ 可选 refreshToken）
    ← patient：将 Token 与 patientId、病历号等一并返回小程序
```

### 2.3 内部接口（不经 Gateway）

| 调用方 | 被调方 | 方法与路径 | 说明 |
|--------|--------|------------|------|
| hospital-patient | hospital-auth | `POST /internal/token/patient` | 请求体含 `patientId` 等；**仅内网**，服务间鉴权（如内部 Header / mTLS 后期可选） |
| hospital-auth | — | — | 校验 `sys_user` 后签发 **STAFF** Token（现有 `POST /auth/staff/login`） |

### 2.4 Token 载荷（与 `API.md` §2.1 一致）

- 患者：`type=PATIENT`，`sub=patientId`
- 医护：`type=STAFF`，`sub=userId`，`roles[]`
- **issuer / 签名密钥仅配置在 auth**（common 可提供 DTO 与常量，避免 his 本地签发）

### 2.5 网关白名单

- `POST /api/v1/auth/staff/login` — 医护登录（auth）
- `POST /api/v1/patient/auth/wechat` — 患者登录入口（**patient**，见 `API.md` §4.0）
- ~~`POST /api/v1/auth/patient/wechat/login`~~ — **废弃**（勿在 auth 上暴露患者微信登录）

### 2.6 故障与依赖

- **auth 不可用**：患者无法完成登录（新会话）；已持有 Token 在过期前仍可由 Gateway 校验（若 Gateway 本地验签或缓存公钥）。
- **patient 不可用**：患者无法登录、无法办理挂号；医护 Token 若已签发不受影响。

### 2.7 答辩口径（一句话）

**身份与档案在 patient（原 his 患者域），令牌在认证中心 auth 统一签发；患者表由 patient 独占写入。**

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

### ADR-006 处置业务归属（已修订 · 见 ADR-017）

- **原决策（v1.6 前）**：不增设第四微服务，开立与结果均在 **his**。
- **现决策（ADR-017）**：处置 **执行侧** 拆为独立进程 **`hospital-disposal`**，与 LIS/PACS 对称；**his 仍负责** 医生开立、`bill` 关联、缴费/退费驱动 `disposal_request.status`（10/20/50）。
- 处置科 PC 菜单路由 **`/api/v1/disposal/**`**；状态机与检查/检验相同（见 `BUSINESS_FLOW.md` §八）。

### ADR-017 处置微服务拆分

- **定稿**：新增 **`hospital-disposal`**（端口 **9105**），Gateway 前缀 **`/api/v1/disposal/**`**。
- **职责划分**（共享表 `disposal_request`）：

| 服务 | 写权限 |
|------|--------|
| **hospital-his** | 开立 INSERT（status=10）；缴费 → 20；退费 → 50 |
| **hospital-disposal** | 执行 → 30；录入 `result_text` → 40 |

- **患者报告**：`GET /patient/reports` 仍走 **his**（只读 `disposal_request`，`status >= 40`）。
- **不做**：处置 **非** 第四课件子系统；仍为门诊医嘱的一种，仅运行形态与 LIS/PACS 对齐以便故障隔离。

### ADR-007 影像/AI 结果通知

- **定稿**：首期 **前端轮询**（如 `GET /patient/imaging-studies/{id}` 或医生端等价接口）。
- SSE/WebSocket 列为 P4+ 增强，不阻塞 P3。

### ADR-008 Redis

- **定稿**：**P1～P3 不依赖 Redis**；网关限流、Token 黑名单若做，放在 P4 或之后。

### ADR-009 微信支付

- **开发/答辩**：`POST /patient/payments` 可走 **模拟成功** 或测试开关，直接推进 `payment_record` 与单据状态。
- **生产**：`callback/wechat/pay` 路由至 **patient**（ADR-019）；需 HTTPS 与商户配置（`DEV_ENV_SETUP` 说明）。

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
- **现状（2026-07）**：脚本已对齐 **DATABASE_DESIGN v1.16**（含 `clinical_sync_task`）；本地重建见 `docs/sql/README.md` §四。
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
- 确认提交后由 **hospital-his** 落库并 Feign 通知 lis/pacs（ADR-003）；**处置执行** 归 **hospital-disposal**（ADR-017）。

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

## 五、ADR-016 就诊人/家属业务模型（已定稿 · 方案 A）

### 5.1 定稿结论（v1.8 修订 · QQ 式病人账户）

| 概念 | 约定 |
|------|------|
| **登录鉴权** | JWT **`patientId` = 当前病人账户**（`patient` 表）；**非**微信账号 |
| **登录入口** | `POST /patient/auth/login`（完整本人档案：姓名、身份证、性别、出生日期、手机号、地址） |
| **切换账户** | `POST /patient/auth/switch-account` → **换发目标病人 JWT**（须家属 link 授权） |
| **本机多账户** | 小程序 `account-store` 缓存多个 Token，类似 QQ 切号 |
| **微信** | 仅 `POST /patient/auth/wechat/bind` 绑定 openid，用于支付 |
| **家属 link** | 仍用于切换授权与无手机患儿；添加后可切换登录该就诊账户 |
| **业务数据** | `register` / `bill` / 病历等挂在 **当前 JWT 病人** `patient_id` |

### 5.1.1 历史方案 A（操作者 + visitPatientId）

v1.6～v1.7 曾采用「JWT=操作者、Query visitPatientId=就诊人」；**v1.8 起小程序改为 JWT 随账户切换**，后端 `visitPatientId` 仍兼容但客户端默认省略。

### 5.2 典型场景

- 妻子代张三挂号：妻子 JWT=李四，`visitPatientId=张三`；`register.patient_id=张三`
- 张三用自己微信登录：JWT=张三，默认 `visitPatientId=张三`，可见妻子代挂的号（同一 `patient` 行；**身份证合并**见 §4.0.2 / `PatientIdentityMergeService`）

### 5.3 明确拒绝

- **非 B1**：切换就诊人时 **不** 修改 `patient_wechat`、**不** re-issue JWT 为就诊人 ID
- **非 C**：操作者与就诊人始终分离建模，业务权限通过 link 或本人判定校验

### 5.4 实现要点

- his：`PatientFamilyService.resolveVisitPatientId` / `canAccessVisitPatient`
- 患者端列表/待缴/支付/退费/病历：操作者 JWT + `visitPatientId` 过滤
- 小程序：`patient-context.js` 存 `activeMemberPatientId`，请求传 Query `patientId`（与 `visitPatientId` 等价）
- 身份证合并：`PatientIdentityMergeService`（登录可选 `idCard` 或档案补全触发）

---

## 六、ADR-018 HIS 领域设计模式重构（实施中 · ①～④ 已落地）

### 6.1 定稿结论

| 项 | 决策 |
|----|------|
| **步骤 ①** | `VisitTransitions` + `VisitLifecycleCoordinator` | 就诊 visit_state **最先** |
| **步骤 ②** | `MedTechOrderTransitions` + `PrescriptionTransitions` + Coordinator | 医嘱 SM1/SM2 |
| **步骤 ③** | `MedicalOrderHandler` + `MedicalOrderHandlerRegistry` | Handler（开单 + 缴费/退费一体） |
| **步骤 ④** | `AbstractMedTechExecuteTemplate` + `AbstractMedTechOrderCoordinator` | 单模板 · LIS/PACS/Disposal 三子类 |
| **处方库存** | 开立预扣 `stock_qty`；退费/驳回/退药回增；不足拒开 | 与 SM2 同事务；见 REFACTORING §4.3.1 |
| **步骤 ⑧** | 拆 patient / pharmacy / clinical | **ADR-019 已编码落地**（2026-07）；验收脚本待重跑 |
| **契约** | **不改** Gateway / `API.md` 字段 | |
| **代码状态** | ①～④ **已落地**（2026-06-04） | 验收脚本待重跑 |
| **延后** | 单测扩充（Coordinator/Handler/Execute） | 不阻塞当前代码合并 |
| **实施** | ①→②→③→④ 每步独立验收 | 见 [REFACTORING §〇](./REFACTORING_DESIGN_PATTERNS.md#〇实施顺序king-定稿) |

### 6.2 与现有文档关系

- 就诊状态图：**已有** → `BUSINESS_FLOW.md` §8.1（State 模式将其代码化）  
- 医嘱状态图：**已有** → §8.2～8.5（保持枚举 + 局部校验，**不**强行 State 类）  
- 微服务边界：**ADR-019 已落地** → `MICROSERVICES.md` §2.3～§2.3b（patient :9108 · clinical :9102 · pharmacy :9109）

---

## 七、ADR-019 HIS 三拆（patient / clinical / pharmacy）（已定稿）

> **前置**：ADR-018 步骤 ①～④ 代码已落地；**对外 HTTP 路径与 `API.md` 字段不变**（仅 Gateway 后端 `lb://` 目标变化）。  
> **关联**：[REFACTORING §九](./REFACTORING_DESIGN_PATTERNS.md#九阶段③--微服务拆分模式重构完成之后) · [MICROSERVICES §2.3～§2.3b](./MICROSERVICES.md)

### 7.1 定稿结论（King · 2026-06-04）

| 项 | 决策 |
|----|------|
| **拆分形态** | 自现有 `hospital-his` 拆为 **3 个 Java 进程** |
| **`hospital-patient`** | 患者小程序 + **registrar 窗口** + 账单/支付/退费 + 定时关单 |
| **`hospital-his`（临床）** | 门诊医生：叫号/finish、病历、医嘱开立（Handler） |
| **`hospital-pharmacy`** | 药师：待发药、发药、退药、驳回 |
| **registrar** | **并进 patient**（`/registrar/**`） |
| **缴费驱动医嘱 status** | **方案 A**：patient 付完款 **Feign → clinical 内部 API** → Handler |
| **AUTO_DAY_CLOSE** | **patient** 定时任务（`RegisterLifecycleService`） |
| **数据库** | **仍共库 `hospital`**；按表 **写归属** 约束，不物理拆库 |
| **lis / pacs / disposal** | **不并入** 本次拆分；边界与 ADR-017 不变 |
| **答辩** | **全流程演示为主**；小概率单模块演示（见 §7.6） |

### 7.2 三服务边界与写归属

#### `hospital-patient`（:9108）

| Gateway 前缀 | 职责 |
|--------------|------|
| `/api/v1/patient/**` | 微信登录/绑定、档案、家属、线上挂号、待缴、支付 |
| `/api/v1/registrar/**` | 窗口挂号、窗口收费、窗口退费、退号 |
| `/api/v1/callback/wechat/**` | 支付回调（生产） |

**主写表**：`patient`、`patient_wechat`、`patient_family_link`、`register`（创建/占号/待支付/退号）、`bill`、`payment_record`、`payment_bill`、`refund_record`。

**协调器**：`VisitLifecycleCoordinator` — **PAY_REGISTRATION**、**CANCEL_PENDING**、**EXPIRE_PENDING**、**CANCEL_REGISTERED**、**AUTO_DAY_CLOSE**。

**服务**：`PaymentService`、`RefundService`、`RegisterLifecycleService`（含日结定时任务）。

#### `hospital-his`（临床 · :9102，沿用端口）

| Gateway 前缀 | 职责 |
|--------------|------|
| `/api/v1/doctor/**` | 队列、叫号、finish、病历、开检验/检查/处置/处方 |

**主写表**：`medical_record`、`medical_record_disease`；医嘱 **开立**（insert + status=10）；`disposal_request` **开立侧**（10/20/50 经 Handler）；处方开立 **预扣库存**（`PrescriptionMedicalOrderHandler`）。

**协调器**：`VisitLifecycleCoordinator` — **CALL**、**FINISH**；`OrderStatusCoordinator` — Handler 开单/内部 settle 触发。

**保留**：`MedicalOrderHandler` + `MedicalOrderHandlerRegistry` 全族（ADR-018 步骤 ③）。

#### `hospital-pharmacy`（:9109）

| Gateway 前缀 | 职责 |
|--------------|------|
| `/api/v1/pharmacy/**` | 待发药、发药、退药、药师驳回 |

**主写表（SM2 写侧）**：处方 status **20→30**（发药）、**30→40**（退药）、**20→15**（驳回）；驳回/退药路径 **回增** `drug_info.stock_qty`（与现 `PharmacyService` 一致）。

**不做**：医生开处方、开立预扣（归 **clinical**）。

### 7.3 跨服务协作（Feign · 方案 A）

#### patient → clinical（缴费 / 退费 / 可退校验）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/internal/orders/on-bill-paid` | body: `{ bizType, bizId }` → `Registry.handler(bizType).onBillPaid(bizId)` |
| POST | `/internal/orders/on-refund` | body: `{ bizType, bizId }` → `Handler.onRefund(bizId)` |
| POST | `/internal/orders/assert-refundable` | body: `{ bizType, bizId }` → `Handler.assertBillRefundable(bizId)` |

**调用时机**：`PaymentService` 在 `bill.markPaid` 与 **`clinical_sync_task` 入队** 同一事务内完成；commit 后 `ClinicalSyncService` 投递 Feign。`RefundService` 在写 `refund_record` 之后对医嘱类 bizType 入队 `ON_REFUND`（同上）。

**Outbox 表**：`clinical_sync_task`（**hospital-patient** 主写；DDL 见 `docs/sql/schema.sql` §F、`DATABASE_DESIGN.md` §8.4.1）。仅用于 patient→clinical 支付/退费同步；其它 Feign 不建任务表。

**幂等**：`on-bill-paid` 对已是 status=20 的医嘱应 **安全跳过或返回 409**；避免重复 Feign 双写。

**鉴权**：`X-Internal-Service: hospital-patient`（或项目统一内部 Header）；**不经 Gateway 对外暴露**。

#### pharmacy → patient（药师驳回退费）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/internal/refunds/prescription-pharmacy-reject` | body: `{ prescriptionId, reason, pharmacistId }`；等价现 `RefundService.refundPrescriptionBillForPharmacyReject` |

**顺序**：**先 Feign 退费成功 → 再** `OrderStatusCoordinator.pharmacyReject` + 回库（跨服务无大事务；失败需可重试/补偿）。

#### patient → auth（不变 · ADR-001）

`POST /internal/token/patient` — 调用方由 his 改为 **patient**。

#### lis / pacs / disposal → clinical / patient（不变 · ADR-005）

- 共享库 **只读** `patient`、`register` 等仍允许；
- Feign 读摘要 **可选**；**禁止 UPDATE** 他服务主写表。

### 7.4 缴费链路（答辩必讲）

```text
POST /patient/payments  （Gateway → patient）
  → PaymentService.mockPay @Transactional
    → billRepository.markPaid
    → settlePaidBillLocally:
         REGISTER / MEDICAL_BOOK → patient 本地 Coordinator（不入队）
         INSPECTION/CHECK/DISPOSAL/PRESCRIPTION
                      → clinicalSyncTaskRepository.enqueue (ON_BILL_PAID)
  → commit（bill + task 同一事务）
  → afterCommit：ClinicalSyncService.processTaskIds
                      → Feign clinical POST /internal/orders/on-bill-paid
                        → Handler.onBillPaid → OrderStatusCoordinator → SM1/SM2
  → 若 Feign 失败：task → FAILED，ClinicalSyncScheduler 退避重试（默认 30s）
```

### 7.5 迁移顺序与验收

| 顺序 | 模块 | 说明 |
|------|------|------|
| 1 | **hospital-pharmacy** | 最小闭环；练 Feign → patient 驳回退费 |
| 2 | **hospital-patient** | 搬 patient/registrar/Payment/Refund/定时关单 |
| 3 | **瘦身 hospital-his** | 剩 doctor + Handler + 内部 API |
| 4 | Gateway + `start-project.ps1` | 更新路由与启动脚本 |

**每步完成后重跑**：`r-pharmacy` · `r-min` · `r-reversal` ·（P4）`r-full`。

**编码前置**：ADR-018 ①～④ **验收脚本 ✅**。

### 7.6 答辩演示

| 模式 | 说明 |
|------|------|
| **全流程（主）** | 对外 URL 不变；PPT 标注 **patient ──Feign on-bill-paid──► clinical** |
| **单模块（小概率）** | 药房：gateway + auth + **patient** + **pharmacy** + PG（驳回依赖退费）；医生：gateway + auth + **clinical** + management |

### 7.7 与课件「HIS 一子系统」的关系

- **运行形态**：HIS 逻辑拆为 **patient + clinical + pharmacy** 三 jar；**仍共库、仍一条门诊闭环**。
- **答辩表述**：「课件 HIS 对应 **三个协作进程**，与 LIS/PACS/Disposal 执行侧拆分对称；Gateway 对外仍一套 API。」

### 7.8 明确不做

- 物理拆库、Seata、消息队列（答辩/demo 非必须）
- 将 lis/pacs/disposal/management 并入本次拆分
- 修改 `API.md` 对外路径或字段（仅内部 Feign 新增）

---

## 八、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05 | 首版；ADR-002～013 已定稿；ADR-001 讨论中 |
| v1.1 | 2026-05 | **ADR-001 定稿为方案 C**（his 微信+落库，auth 统一签发 Token） |
| v1.2 | 2026-05 | **ADR-014** 项目命名：CloudBrain-Hospital + 本地简称 NST（东软培训） |
| v1.3 | 2026-05 | **ADR-014 修订**：GitHub 仓库定名 NTS（Neural Treatment System） |
| v1.4 | 2026-05 | **ADR-014 修订**：仓库定名 **NST**（Nexus Smart Treatment） |
| v1.5 | 2026-06 | **ADR-015** AI 辅助开单：diagnosis/suggest + ai-draft 三步 |
| v1.6 | 2026-06 | **ADR-016** 就诊人/家属：**方案 A**（操作者 JWT + visitPatientId） |
| v1.8 | 2026-06 | **ADR-016 修订**：病人账户登录 + QQ 式 `switch-account`；微信仅 bind 支付 |
| v1.9 | 2026-06-04 | **ADR-018** 实施顺序：① visit → ② SM1/SM2 → ③ Strategy → ④ Execute Template |
| v2.0 | 2026-06-30 | **ADR-018 修订**：③ Handler + Registry；④ 单模板三子类；叙述 2 层 / 实现 3 表 |
| v2.3 | 2026-06-04 | **ADR-019 定稿**：his 三拆 patient/clinical/pharmacy；缴费 Feign 方案 A；ADR-001/009 调用方同步 |
| v2.5 | 2026-07-01 | **ADR-019 文档同步**：去除「实施前」；步骤⑧标已编码；patient `RegisterRepository` 临床死代码清理 |
| v2.4 | 2026-07-01 | **ADR-019 §7.4 更新**：patient→clinical 落地 **`clinical_sync_task` Outbox**；DDL 并入 `schema.sql`（DATABASE v1.16） |
| v2.1 | 2026-06-30 | **ADR-018 补充**：处方 SM2 与 `stock_qty` 联动（开立预扣、退费/退药/驳回回增、不足拒开） |
