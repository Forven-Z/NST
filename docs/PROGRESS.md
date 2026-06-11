# 智慧云脑诊疗平台 — 实现进度（活文档）

> **更新频率**：建议 **每周** 或里程碑完成时更新。  
> **状态**：⬜ 未开始 · 🟨 进行中 · ✅ 已完成 · ⏸ 阻塞  
> **版本**：2026-06-11  
> **数据模型对齐**：文档 + 后端 + 前端 Mock + 小程序 Mock 已同步 **DATABASE_DESIGN v1.14**（业务单号即表 `id`；药品 `drugFormat`/`drugDosage`/`drugType`）

---

## 一、当前阶段

| 项 | 值 |
|----|-----|
| **目标阶段** | P3（R-pacs）— 核心已完成；**PACS 对齐 LIS 三段式** ✅ |
| **本迭代 DoD** | [PACS-LIS 对齐规格](./superpowers/specs/2026-06-11-pacs-lis-alignment-design.md) · [实施计划](./superpowers/plans/2026-06-11-pacs-lis-alignment.md) |
| **当前步骤** | 影像任务 `imaging-studies` & CNN 由 **wsh** 后续实现；下一阶段 Disposal 三段式对齐 |
| **联调清单** | [RUNBOOK.md](./RUNBOOK.md) §十二 · [TEAM_COLLABORATION.md](./TEAM_COLLABORATION.md) |

---

## 二、基础设施

| 任务 | 状态 | 负责人 | 备注 |
|------|------|--------|------|
| PostgreSQL + 库 `hospital` | ✅ | | 联调已连通 |
| `schema.sql` 已执行（**v1.14**） | 🟨 | | 本地若仍用旧表须 DROP SCHEMA 重建 |
| `seed-dict.sql` 已执行 | ✅ | | 排班含当日数据 |
| Nacos 2.2.3 standalone | ✅ | | 8848 |
| MinIO（P3 前可跳过） | ⬜ | | |
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
| hospital-his · 医生读检验结果 | API §5.4 · 改进方案 §5.1 | ✅ | | `GET /doctor/inspection-requests/{id}/result` §1.7 全字段 |
| hospital-his · 开立检查 | API §5.3 | ✅ | | `POST /doctor/check-requests` |
| hospital-his · 医生读检查结果 | API §5.4 · §1.7 | ✅ | | `GET /doctor/check-requests/{id}/result` §1.7 全字段 |
| hospital-his · 开立处方 | API §5.5 | ✅ | | `POST /doctor/prescriptions` |
| hospital-his · 药房发药 | API §5.6 | ✅ | | `GET /pharmacy/pending`, `POST .../dispense`, `return-drug` |
| hospital-his · 退号/退费 | API §5.9 | ✅ | | `/registrar/refunds`, `/registrar/registers/{id}/cancel` |
| hospital-his · 窗口挂号/收费 | API §八 | ✅ | | `POST /registrar/registers`（待支付）+ `POST /registrar/charges` |
| hospital-lis · 队列/结果/报告 | API §6 | ✅ | | 第二阶段三段式 + **第三阶段** `criticalItems`、危急值发布前弹窗、退费边界联调 |
| hospital-disposal · 队列/结果 | API §5.7.4 | ✅ | | `GET /disposal/queue`, `POST /disposal/requests/{id}/result` |
| hospital-management · 字典只读 | API §6 | ✅ | | `GET /admin/departments` 等 |
| hospital-pacs · 队列/执行/结果 | API §6 | ✅ | lzr | `GET /pacs/queue`, `POST execute/result`；R-pacs 7/7 |
| hospital-pacs · 三段式报告 | API §6.1 | ✅ | lzr | `result-detail` / `ai-report` STUB / 双字段 `result`；`PacsReportStubSupport` + `PacsAiReportCache` |
| hospital-pacs · 影像任务/CNN | API §6 · §8 | ⬜ | lzr+wsh | `imaging-studies`、MinIO upload、`hospital-ai` 回调 |
| hospital-ai-bridge · STUB | API §7 | ✅ | | `/ai/health`, triage/assistant 占位 |
| hospital-lis | MICRO §2.4 | ✅ | | :9103 |
| hospital-disposal | MICRO §2.5a | ✅ | | :9105 |
| hospital-pacs | MICRO §2.5 | ✅ | | :9104 |
| hospital-management | MICRO §2.6 | ✅ | | :9107 字典只读 |
| hospital-ai-bridge | MICRO §2.7 | ✅ | | :9106 STUB |
| hospital-ai (Python) | API §8 | ⬜ | | P4 |

---

## 四、前端

| 模块 | 文档 | 状态 | 负责人 | 备注 |
|------|------|------|--------|------|
| PC · 登录页 | API.md §二 | ✅ | | `/login` |
| PC · 医生队列/病历 | API.md §五 | ✅ | | 开单对话框 + 医嘱面板 + 检验结果三段式只读（`RegisterOrdersPanel`） |
| PC · LIS 检验科队列 | API.md §6 | ✅ | | `TechQueuePanel` 三段式 + 危急值发布前确认弹窗 |
| PC · 药师发药 | §2.6 | ✅ | | `/pharmacy/pending` 待发药 + 发药/退药 |
| PC · 收费员退费 | §2.3 | ✅ | | `/registrar/refund` 按病历号查询 + 退费 |
| PC · PACS 检查队列 | API §6 | ✅ | zty | `TechQueuePanel` 三段式 + `fetchPacsResultDetail`；对齐 LIS |
| PC · PACS 影像任务 | §2.5 | 🟨 | zty | `/pacs/imaging` 关 Mock 显示开发中空态；后端 `imaging-studies` ⬜ |
| PC · PACS 影像 AI 工作台 | §2.5 | ✅ | zty | `/pacs/imaging-ai` 最小可运行；CNN 由 wsh 后续 |
| 小程序 · 登录/挂号 | §一 | ✅ | | `hospital-patient-miniapp/` |
| 小程序 · 支付 | §一 | ✅ | | 待缴列表 + 模拟支付 |
| 小程序 · 报告/医嘱 | §一 | ✅ | | 报告 Tab + `pages/orders` 医嘱进度 |

---

## 五、里程碑验收

| 组合 | 状态 | 验收日期 | 验收人 |
|------|------|----------|--------|
| R-min（P1） | ✅ | 2026-05-31 | King | `scripts/r-min-acceptance.ps1` 10/10 PASS |
| R-lis（P2） | ✅ | 2026-06-11 | King | `scripts/r-lis-acceptance.ps1`（含 execute、criticalItems、退费负例、E5 患者报告） |
| R-pacs（P3） | ✅ | 2026-05-31 | King | `scripts/r-pacs-acceptance.ps1` 7/7 PASS |
| R-pharmacy（P3） | ✅ | 2026-05-31 | King | `scripts/r-pharmacy-acceptance.ps1` 4/4 PASS |
| R-reversal（P3） | ✅ | 2026-05-31 | King | `scripts/r-reversal-acceptance.ps1` 4/4 PASS |
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
| 2026-06-11 | **PACS 对齐 LIS 实现完成**：后端 `result-detail`/`ai-report` STUB/双字段 `result`；HIS 医生读结果 §1.7；前端 `QueueView` 三段式 + `ImagingAiView`/`ImagingView` 最小修复；[API.md](./API.md) v2.5 |
| 2026-06-11 | **PACS 对齐 LIS** 设计定稿：[规格](./superpowers/specs/2026-06-11-pacs-lis-alignment-design.md) · [计划](./superpowers/plans/2026-06-11-pacs-lis-alignment.md)；不含 MinIO/CNN |
| 2026-06-11 | LIS 第三阶段（改进方案 §五 5.1～5.4 ✅）：医生读结果 §1.7、`criticalItems`、危急值弹窗、退费边界、`r-lis` E3～E5 扩展 |
| 2026-06-11 | LIS 第二阶段：三段式报告 UI、`POST ai-report` STUB、仪器 STUB、`POST result` 合成、队列分诊 STUB |
| 2026-06-10 | 实现 `GET /doctor/registers/{registerId}/orders`（医嘱汇总，ADR-005 只读聚合） |
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
