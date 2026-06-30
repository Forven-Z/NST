# 智慧云脑诊疗平台 — 设计文档索引

> **项目仓库**：**NST**（**N**exus **S**mart **T**reatment，枢纽智能诊疗）  
> **培训背景**：东软公司 NEU Software Training 实训项目  
> **中文产品名**：智慧云脑诊疗平台  
> **文档体系版本**：v2.2 | 2026-06-04  
> **数据模型**：[`DATABASE_DESIGN.md`](./DATABASE_DESIGN.md) **v1.14**（表结构定稿；`docs/sql/schema.sql` 已对齐）  
> **实施策略**：自启动 **微服务**（**9×Java** + 1×Python FastAPI）；课件三系统 **HIS / LIS / PACS** 各一 jar；**处置执行** 另拆 **`hospital-disposal`**（ADR-017）。

---

## 一、文档清单（精简后 16 份正文 + sql/infra）

| 层级 | 文档 | 权威范围 |
|------|------|----------|
| **L1 必读** | [TEAM_COLLABORATION.md](./TEAM_COLLABORATION.md) | 协作方式、Mock、契约变更、模块认领 |
| **L1 必读** | [RUNBOOK.md](./RUNBOOK.md) | **启动 + 联调验收**（含原 INTEGRATION_CHECKLIST） |
| **L2 契约** | [API.md](./API.md) | **唯一** HTTP 契约（路径定稿、报文、实现状态、页面速查附录） |
| **L2 契约** | [DATABASE_DESIGN.md](./DATABASE_DESIGN.md) | 表结构、状态枚举（**v1.14 定稿**） |
| **L2 契约** | [MICROSERVICES.md](./MICROSERVICES.md) | 微服务边界、路由、**架构图 §八**、M1～M10 |
| **L3 计划** | [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) | 分期任务、DoD、**开发动机附录 §八** |
| **L3 参考** | [PROJECT_REQUIREMENTS.md](./PROJECT_REQUIREMENTS.md) | 需求、角色、答辩材料 |
| **L3 参考** | [BUSINESS_FLOW.md](./BUSINESS_FLOW.md) | 业务流程与状态图 |
| **L3 参考** | [DESIGN_DECISIONS.md](./DESIGN_DECISIONS.md) | ADR 已定稿 |
| **L3 参考** | [REFACTORING_DESIGN_PATTERNS.md](./REFACTORING_DESIGN_PATTERNS.md) | **HIS 设计模式重构**（ADR-018 · ①～④ 已落地） |
| **L4 按需** | [DEV_ENV_SETUP.md](./DEV_ENV_SETUP.md) | Windows 首次装环境 |
| **活文档** | [PROGRESS.md](./PROGRESS.md) | 实现进度（每周更新） |
| **SQL** | [sql/README.md](./sql/README.md) | 建表与 seed |
| **L4 AI** | [IMAGING_DATA_ACCESS.md](./IMAGING_DATA_ACCESS.md) | 影像 MinIO + 按 patient_id 查结果（跨模块） |
| **L4 AI** | [AI_CNN_INTEGRATION.md](./AI_CNN_INTEGRATION.md) | **CNN 唯一文档**：CT 伪影、pacs API、taskType、权重 |
| **L4 AI** | [RAG_GUIDE.md](./RAG_GUIDE.md) | **LLM/RAG 唯一文档**：pgvector、知识库、启动与验收 |
| **L4 演示** | [DEMO_MEDICAL_RECORD_SAMPLES.md](./DEMO_MEDICAL_RECORD_SAMPLES.md) | RAG/开单演示病历样例 |

> **已合并删除**（勿再引用，留痕见 [archive/README.md](./archive/README.md)）：  
> `LUNG_*`、`AI_TASK_TYPE_MINIMAL` → **AI_CNN_INTEGRATION**；`RAG_RUN/VECTOR/KNOWLEDGE_*` → **RAG_GUIDE**；  
> `FRONTEND_API_MAP`、`API_INTERFACE_SPEC` → **API.md**；`LOCAL_WORKSPACE` → **RUNBOOK §零**；  
> `INTEGRATION_CHECKLIST` → RUNBOOK §十二；`DEVELOPMENT_RATIONALE` → IMPLEMENTATION_PLAN §八；`TECH_ARCHITECTURE` → MICROSERVICES §八。

**冲突处理顺序**（高 → 低）：

1. `DESIGN_DECISIONS.md`  
2. `MICROSERVICES.md`  
3. `DATABASE_DESIGN.md`  
4. `API.md`  
5. `BUSINESS_FLOW.md`  
6. `PROJECT_REQUIREMENTS.md`  
7. `DEV_ENV_SETUP.md`

---

## 二、推荐阅读顺序

| 角色 | 顺序 |
|------|------|
| **全员首次** | 本页 → **TEAM_COLLABORATION** → **RUNBOOK** → DEV_ENV_SETUP（新同学装环境） |
| **日常开发** | TEAM_COLLABORATION → **RUNBOOK §零**（开机启动）→ PROGRESS |
| **后端** | TEAM_COLLABORATION → MICROSERVICES → IMPLEMENTATION_PLAN → DATABASE §1.4 → API §〇 → sql/ |
| **前端** | TEAM_COLLABORATION → **API.md**（§〇 路径 · 附录 A）→ RUNBOOK §十二 |
| **答辩（30 min）** | MICROSERVICES §八 → PROJECT_REQUIREMENTS → BUSINESS_FLOW §八 → PROGRESS → AI_CNN §九 + RAG_GUIDE §一 |
| **答辩（技术深度）** | REFACTORING_DESIGN_PATTERNS → DESIGN_DECISIONS ADR-018 → AI_CNN + IMAGING_DATA_ACCESS |

---

## 三、术语对照（避免混读）

| 术语 | 含义 |
|------|------|
| **HIS / LIS / PACS** | 课件三子系统 = **`hospital-his` / `hospital-lis` / `hospital-pacs` 三个 Java 进程** |
| **处置执行** | **`hospital-disposal`**（:9105），镜像 LIS/PACS；开立仍在 **his** |
| **逻辑模块** | his 内部的 **Java 包**（patient、pharmacy…），**不是**独立微服务 |
| **一体化** | 一个库 `hospital`、一个 Gateway :9000；与多 jar 部署不矛盾 |
| **hospital-common** | 共享 jar，**不单独启动** |

---

## 四、服务与端口

| 服务 | 端口 |
|------|------|
| hospital-gateway | **9000**（对外唯一 HTTP） |
| hospital-auth | 9101 |
| hospital-his | 9102 |
| hospital-lis | 9103 |
| hospital-pacs | 9104 |
| hospital-management | 9107 |
| hospital-ai-bridge | 9106 |
| hospital-disposal | 9105 |
| hospital-ai (Python) | 8000 |

基础设施：PostgreSQL `5432`，Nacos `8848`，MinIO API `9001`。

---

## 五、分期与演示组合

| 阶段 | 组合 | 说明 |
|------|------|------|
| P1 | **R-min** | 挂号、接诊、病历 |
| P2 | **R-lis** | + 检验 |
| P3 | **R-pacs** | + 检查、发药、退号退费退药 |
| P4 | **R-full** | + CNN + LLM/RAG |

详见 `MICROSERVICES.md` §九、`IMPLEMENTATION_PLAN.md` §一。

**代码实现进度**：一律以 [PROGRESS.md](./PROGRESS.md) 为准。

---

## 六、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0～v1.6 | 2026-05 | 见历史条目 |
| v1.7 | 2026-05 | **文档精简**：合并 INTEGRATION / RATIONALE / TECH；分层阅读 L1～L4 |
| v2.1 | 2026-06 | 全库对齐 **DATABASE v1.14**；`API.md` v1.4 端口/字段修正 |
| v2.2 | 2026-06-04 | CNN/RAG 各合并为 1 份；删除过程文档；新增 [archive/](./archive/) 留痕索引 |
