# 智慧云脑诊疗平台 — 实现进度（活文档）

> **更新频率**：里程碑完成时更新。  
> **状态**：⬜ 未开始 · 🟨 进行中 · ✅ 已完成 · ⏸ 阻塞  
> **版本**：2026-06-04 · **定稿交付**  
> **数据模型**：文档 + 后端 + 前端 + 小程序 已同步 **DATABASE_DESIGN v1.16**（含 `clinical_sync_task`）

---

## 一、当前阶段

| 项 | 值 |
|----|-----|
| **目标阶段** | **P4（R-full）— 已全部落地** |
| **门诊主链** | P1～P3 ✅（R-min / R-lis / R-pacs / 药房 / 逆向 / 管理） |
| **AI 能力** | CNN 三态 ✅ · LLM triage/诊断/草稿/报告 ✅ · RAG ✅（需 Key + pgvector） |
| **一键启动** | [RUNBOOK.md §零](./RUNBOOK.md) · `start-project.ps1` + `start-hospital-ai.ps1` |
| **联调验收** | [RUNBOOK.md §十二](./RUNBOOK.md) 手工 checklist |

**交付范围外（不阻塞答辩）**：真微信支付回调 · admin 药品/医技等字典**全量**写 CRUD · P5 Timefold / LLM 排班 UI · Redis · 队列 `triageLevel` 扩展字段。

---

## 二、基础设施

| 任务 | 状态 | 备注 |
|------|------|------|
| PostgreSQL + 库 `hospital` | ✅ | |
| `schema.sql` **v1.16** | ✅ | 含 `clinical_sync_task` |
| `seed-dict.sql` | ✅ | |
| Nacos 8848 | ✅ | |
| MinIO 9001 | ✅ | `start-project` local 自动启 |
| Gateway 路由 + JWT | ✅ | |
| 架构图 | ✅ | `docs/images/tech-architecture.png`（`render-tech-architecture.py`） |

---

## 三、后端服务

| 模块 | 端口 | 状态 | 要点 |
|------|------|------|------|
| hospital-common | — | ✅ | 共享 jar |
| hospital-gateway | 9000 | ✅ | 唯一对外 HTTP |
| hospital-auth | 9101 | ✅ | 医护/患者 Token |
| hospital-his（临床） | 9102 | ✅ | 医生队列/病历/开单 |
| hospital-patient | 9108 | ✅ | 小程序/挂号/支付/registrar/窗口收费 |
| hospital-pharmacy | 9109 | ✅ | 发药/退药/驳回 |
| hospital-lis | 9103 | ✅ | 检验队列/结果 |
| hospital-pacs | 9104 | ✅ | 检查/影像/CNN 任务/LLM 报告 |
| hospital-disposal | 9105 | ✅ | 处置执行/三段式报告 |
| hospital-management | 9107 | ✅ | 字典只读 · 科室/员工/排班 CRUD · 排班 AI 规则引擎 |
| hospital-ai-bridge | 9106 | ✅ | triage/诊断/草稿 · LIS/PACS LLM 报告 · RAG |
| hospital-ai（Python） | 8000 | ✅ | CNN 头部/肺部/肿瘤；内网不经 Gateway |

---

## 四、前端

| 模块 | 状态 | 要点 |
|------|------|------|
| PC · 登录/医生/药师/收费/LIS/PACS/处置/管理 | ✅ | 关 Mock 联调 |
| PC · 影像 AI 工作台 | ✅ | `/pacs/imaging-ai` + CNN |
| 小程序 · 登录/挂号/支付/报告/病历 | ✅ | `hospital-patient-miniapp/` |

---

## 五、里程碑验收

| 组合 | 状态 | 验收方式 |
|------|------|----------|
| R-min（P1） | ✅ | RUNBOOK §12.2 |
| R-lis（P2） | ✅ | RUNBOOK §12.3 |
| R-pacs / 药房 / 逆向（P3） | ✅ | RUNBOOK §12.4 |
| R-mgmt | ✅ | 管理端字典/排班/请假 |
| R-full（P4） | ✅ | CNN + LLM/RAG 演示（§12.5） |

---

## 五·一、HIS 设计模式重构（ADR-018 / ADR-019）

| 阶段 | 代码 | 验收 |
|------|------|------|
| ① VisitTransitions | ✅ | ✅ |
| ② SM1 + SM2 | ✅ | ✅ |
| ③ MedicalOrderHandler | ✅ | ✅ |
| ④ MedTechExecute Template | ✅ | ✅ |
| ⑧ 拆 patient / pharmacy / clinical + Outbox | ✅ | ✅ |

详见 [REFACTORING_DESIGN_PATTERNS.md](./REFACTORING_DESIGN_PATTERNS.md)。

---

## 六、阻塞与风险

| 日期 | 描述 | 影响 | 处理 |
|------|------|------|------|
| — | 无 | — | — |

---

## 七、变更记录

| 日期 | 说明 |
|------|------|
| 2026-06-04 | **定稿交付**：全模块 ✅；文档全库同步；移除自动化验收脚本，改 RUNBOOK 手工 checklist |
| 2026-06-04 | 架构图重绘（ADR-019 · 11 Java + CNN）；移除 Redis |
| 2026-06-04 | `GET /registrar/regist-levels` · 契约精简（移除 assistant/stream 等） |
| 2026-07-01 | ADR-019 收尾 · DATABASE v1.16 · `clinical_sync_task` |
| 2026-05-31 | R-min～R-reversal 联调通过 |
