# 智慧云脑诊疗平台（NST · Nexus Smart Treatment）

门诊信息化 + AI 增强；**微服务**架构（HIS / LIS / PACS + 平台与 AI）。

```bash
git clone https://github.com/<你的用户名>/NST.git
cd NST
```

### Git 速查

`clone` → 在 `feature/...` 上改 → `push` 分支 → GitHub **PR Merge** → `git pull origin main`

详见 [docs/TEAM_COLLABORATION.md](./docs/TEAM_COLLABORATION.md) §六。

## 仓库结构

| 目录 | 说明 |
|------|------|
| [docs/](./docs/) | **设计文档（必读 [docs/README.md](./docs/README.md)）** |
| [hospital-backend/](./hospital-backend/) | Java 17 · Spring Boot 3.2.4 · Spring Cloud 2023.0 · 微服务 |
| [hospital-frontend/](./hospital-frontend/) | Vue 3 · 医生/管理 PC |
| [hospital-patient-miniapp/](./hospital-patient-miniapp/) | 患者微信小程序（原生） |
| [hospital-ai/](./hospital-ai/) | Python FastAPI · CT 金属伪影 CNN（见 [docs/AI_CNN_INTEGRATION.md](./docs/AI_CNN_INTEGRATION.md)） |

## 快速入口

- **每次开机一键启动**：[docs/RUNBOOK.md §零](./docs/RUNBOOK.md) → `.\scripts\start-project.ps1`
- **环境脚本**：`scripts/start-project.ps1` · `stop-project.ps1` · `env-cloud.ps1` · `env-local.ps1` · `start-hospital-ai.ps1` · `install-model-weights.ps1`
- **团队协作**：[docs/TEAM_COLLABORATION.md](./docs/TEAM_COLLABORATION.md)
- **启动 + 联调验收**：[docs/RUNBOOK.md](./docs/RUNBOOK.md)（含 §十二 验收清单）
- **文档索引（分层阅读）**：[docs/README.md](./docs/README.md)
- **实施计划 + 开发动机**：[docs/IMPLEMENTATION_PLAN.md](./docs/IMPLEMENTATION_PLAN.md)
- **进度跟踪**：[docs/PROGRESS.md](./docs/PROGRESS.md)
- **API 契约**：[docs/API.md](./docs/API.md)（唯一；Base URL：`http://localhost:9000`，含页面速查附录 A）
- **微服务 + 架构图**：[docs/MICROSERVICES.md](./docs/MICROSERVICES.md)
- **环境搭建**：[docs/DEV_ENV_SETUP.md](./docs/DEV_ENV_SETUP.md)
- **CNN 集成**：[docs/AI_CNN_INTEGRATION.md](./docs/AI_CNN_INTEGRATION.md)
- **LLM/RAG**：[docs/RAG_GUIDE.md](./docs/RAG_GUIDE.md)
- **建库脚本**：[docs/sql/README.md](./docs/sql/README.md)
- **影像数据怎么按患者查**：[docs/IMAGING_DATA_ACCESS.md](./docs/IMAGING_DATA_ACCESS.md)

## 当前阶段

**定稿交付（P0.5～P4）**：门诊主链 + CNN 三态 + LLM/RAG 已全部落地。详见 [docs/PROGRESS.md](./docs/PROGRESS.md)。

**数据模型 v1.16**：表结构、API、后端、前端、小程序已对齐。  
**联调**：`.\scripts\start-project.ps1`（默认本机库）；答辩展示 `-EnvProfile cloud`。CNN：`install-model-weights.ps1` → `start-hospital-ai.ps1`。详见 [RUNBOOK §零](./docs/RUNBOOK.md)。
