# 智慧云脑诊疗平台 — 实现进度（活文档）

> **更新频率**：建议 **每周** 或里程碑完成时更新。  
> **状态**：⬜ 未开始 · 🟨 进行中 · ✅ 已完成 · ⏸ 阻塞  
> **版本**：2026-06-04  
> **数据模型对齐**：文档 + 后端 + 前端 Mock + 小程序 Mock 已同步 **DATABASE_DESIGN v1.14**（业务单号即表 `id`；药品 `drugFormat`/`drugDosage`/`drugType`）

---

## 一、当前阶段

| 项 | 值 |
|----|-----|
| **目标阶段** | P3（R-pacs）— 核心已完成 |
| **本迭代 DoD** | 见 [IMPLEMENTATION_PLAN.md §五](./IMPLEMENTATION_PLAN.md#五p3--r-pacs检查--处方--发药) |
| **当前步骤** | ✅ 药房发药闭环 → Python AI / 排班 CRUD |
| **联调清单** | [RUNBOOK.md](./RUNBOOK.md) §十二 · [TEAM_COLLABORATION.md](./TEAM_COLLABORATION.md) |

---

## 二、基础设施

| 任务 | 状态 | 负责人 | 备注 |
|------|------|--------|------|
| PostgreSQL + 库 `hospital` | ✅ | | 联调已连通 |
| `schema.sql` 已执行（**v1.14**） | 🟨 | | 本地若仍用旧表须 DROP SCHEMA 重建 |
| `seed-dict.sql` 已执行 | ✅ | | 排班含当日数据 |
| Nacos 2.2.3 standalone | ✅ | | 8848 |
| MinIO（P3 前可跳过） | ✅ | wsh | `C:\dev\minio`，社区版 `start-minio-community.bat` |
| Gateway 路由配置 | ✅ | | 已合入 `application.yml` |
| Gateway JWT 过滤器 | ✅ | | `JwtAuthGlobalFilter` |

---

## 三、后端服务（代码）

| 模块 | 文档 | 状态 | 负责人 | 备注 |
|------|------|------|--------|------|
| hospital-common | — | ✅ | | Result、JWT 常量、UserType、ErrorCode |
| hospital-gateway | TECH §五 | ✅ | | 路由 + JWT 白名单过滤器 |
| hospital-auth · staff login | API §3.1 | ✅ | | `doctor01/123456` 可登录 |
| hospital-auth · internal/token/patient | API §3.2, ADR-001 | ✅ | | 需 `X-Internal-Service: hospital-his` |
| hospital-auth · token/refresh | API §3.3 | ✅ | | |
| hospital-auth · auth/me | API §3.1 | ✅ | | 验收 A2 |
| hospital-his · 微信登录 | API §4.0 | ✅ | | Feign → auth；dev mock code |
| hospital-his · 挂号/支付 | API §4.2–4.3 | ✅ | | 模拟支付 `POST /patient/payments` |
| hospital-his · 医生队列/病历 | API §5.1–5.2 | ✅ | | `/doctor/queues`, `/doctor/call/{id}` |
| hospital-his · 医生医嘱汇总 | API §5.3 | ✅ | | `GET /doctor/registers/{id}/orders` |
| hospital-his · 开立检验 | API §5.4 | ✅ | | `POST /doctor/inspection-requests` |
| hospital-his · 开立检查 | API §5.3 | ✅ | | `POST /doctor/check-requests` |
| hospital-his · 开立处方 | API §5.5 | ✅ | | `POST /doctor/prescriptions` |
| hospital-his · 药房发药 | API §5.6 | ✅ | | `GET /pharmacy/pending`, `POST .../dispense`, `return-drug` |
| hospital-his · 退号/退费 | API §5.9 | ✅ | | `/registrar/refunds`, `/registrar/registers/{id}/cancel` |
| hospital-his · 窗口挂号/收费 | API §八 | ✅ | | `POST /registrar/registers`（待支付）+ `POST /registrar/charges` |
| hospital-lis · 队列/结果 | API §5.7 | ✅ | | `GET /lis/queue`, `POST /lis/requests/{id}/result` |
| hospital-disposal · 队列/结果 | API §5.7.4 | ✅ | | 三段式报告 + `r-disposal-acceptance.ps1` |
| hospital-management · 字典只读 | API §9.1 | ✅ | | `GET /admin/departments` 等 |
| hospital-management · 科室/员工/排班 CRUD | API §9.2–9.3 | ✅ | | `POST/PUT/DELETE /admin/**` + auth 内部开户 |
| hospital-management · 排班请假 §8.5/§9.5 | API §8.5、§9.5 | ✅ | | `scheduling_leave_request` 表 + 验收脚本 |
| hospital-pacs · 队列/执行/结果 | API §6 | ✅ | lzr | `GET /pacs/queue`, `POST execute/result`；R-pacs 7/7 |
| hospital-pacs · 三段式报告 | API §6.1 | ✅ | lzr | `result-detail` / `ai-report` STUB / 双字段 `result`；`PacsReportStubSupport` + `PacsAiReportCache` |
| hospital-pacs · 影像任务/CNN | API §6 · §8 | 🟨 | lzr+wsh | taskType 已通；62001 头部 / 62002 肺部 / 62006 肿瘤（三模型权重已部署） |
| hospital-ai-bridge · STUB | API §7 | ✅ | | `/ai/health`, triage/assistant 占位 |
| hospital-lis | MICRO §2.4 | ✅ | | :9103 |
| hospital-disposal | MICRO §2.5a | ✅ | | :9105 |
| hospital-pacs | MICRO §2.5 | ✅ | | :9104 |
| hospital-management | MICRO §2.6 | ✅ | | :9107 字典只读 + 科室/员工/排班 CRUD |
| hospital-ai-bridge | MICRO §2.7 | ✅ | | :9106 STUB |
| hospital-ai (Python) | API §8 | 🟨 | wsh | 头部 `best.pth` + 肺部 `lung_artifact_best.pth` 已部署；见 `LUNG_INTEGRATION_TEAM_CHANGELOG.md` |

---

## 四、前端

| 模块 | 文档 | 状态 | 负责人 | 备注 |
|------|------|------|--------|------|
| PC · 登录页 | API.md §二 | ✅ | | `/login` |
| PC · 医生队列/病历 | API.md §五 | ✅ | | 开单对话框 + 医嘱面板 + 完整病历字段 |
| PC · 药师发药 | §2.6 | ✅ | | `/pharmacy/pending` 待发药 + 发药/退药 |
| PC · 收费员退费 | §2.3 | ✅ | | `/registrar/refund` 按病历号查询 + 退费 |
| PC · PACS 检查队列 | API §6 | ✅ | zty | `TechQueuePanel` 三段式 + 录入弹窗 **重新采图**（跳转影像 AI 工作台）+ `mergeCheckReportAfterLlm` 保留三视图 |
| PC · PACS 影像任务 | §2.5 | 🟨 | zty | `/pacs/imaging` 关 Mock 显示开发中空态；后端 `imaging-studies` ⬜ |
| PC · PACS 影像 AI 工作台 | §2.5 | ✅ | zty | `/pacs/imaging-ai` 最小可运行；CNN 由 wsh 后续 |
| PC · admin | API §9 | ✅ | | 排班页与 Mock 统一；请假联调；AI STUB 50301 |
| 小程序 · 登录/挂号 | §一 | ✅ | | `hospital-patient-miniapp/` |
| 小程序 · 支付 | §一 | ✅ | | 待缴明细 + 演示级微信支付 UI + 缴费详情 |
| 小程序 · 报告/医嘱 | §一 | ✅ | | 报告分组/角标/空态 + 医嘱上下文 + 处方详情 |

---

## 五、里程碑验收

| 组合 | 状态 | 验收日期 | 验收人 |
|------|------|----------|--------|
| R-min（P1） | ✅ | 2026-05-31 | King | `scripts/r-min-acceptance.ps1` 10/10 PASS |
| R-lis（P2） | ✅ | 2026-05-31 | King | `scripts/r-lis-acceptance.ps1` 9/9 PASS |
| R-pacs（P3） | ✅ | 2026-05-31 | King | `scripts/r-pacs-acceptance.ps1` 7/7 PASS |
| R-pharmacy（P3） | ✅ | 2026-05-31 | King | `scripts/r-pharmacy-acceptance.ps1` 4/4 PASS |
| R-reversal（P3） | ✅ | 2026-05-31 | King | `scripts/r-reversal-acceptance.ps1` 4/4 PASS |
| R-mgmt（P1） | ✅ | 2026-06-11 | | `scripts/r-mgmt-acceptance.ps1` 12/12 PASS |
| R-full（P4） | ⬜ | | |

---

## 六、阻塞与风险

| 日期 | 描述 | 影响 | 处理 |
|------|------|------|------|
| | | | |

---

## 七、变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-03 | wsh：taskType 第二步、MinIO 社区版、LIDC 数据计划；hospital-ai 肺部权重热插拔 |
| 2026-06-04 | API 文档合并为唯一 [API.md](./API.md) v2.0（路径定稿 + 实现状态） |
| 2026-06-04 | UI Mock 完整版 + 接口契约（现并入 API.md） |
| 2026-06-04 | 全库文档对齐 DATABASE **v1.14**（API 端口/字段、HIS 分包、进度表述） |
| 2026-06-04 | ADR-015：AI 开单 suggest + ai-draft；`TEAM_COLLABORATION` §九 六人分工 |
| 2026-05-31 | 文档精简：合并 INTEGRATION/RATIONALE/TECH 至 RUNBOOK/IMPLEMENTATION/MICROSERVICES |
| 2026-05-31 | 退号/退费/退药：患者退号、窗口退费、药师退药+退费；验收 4/4 PASS |
| 2026-05-31 | 药房发药闭环：开处方→缴费→待发药→发药；验收 4/4 PASS |
| 2026-05-31 | 补齐 management/pacs/ai-bridge 三模块 + HIS 检查开单 |
| 2026-05-31 | 患者微信小程序工程 `hospital-patient-miniapp`（登录/挂号/缴费/档案/病历） |
| 2026-05-31 | R-lis 检验闭环验收通过（开单→缴费→LIS 录入→医生查看） |
| 2026-05-31 | R-min 联调验收通过（Gateway 9000，A～D 全场景） |
| 2026-05-26 | 初始化进度表 |
