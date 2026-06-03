# 智慧云脑诊疗平台（NST · Nexus Smart Treatment）

门诊信息化 + AI 增强；**微服务**架构（HIS / LIS / PACS + 平台与 AI）。

```bash
git clone https://github.com/Forven-Z/NST.git
cd NST
```

Git 协作：**人人可推 `main`，但须保持可运行**；大改动先 `feature/*`，完善后自行合并。详见 [docs/TEAM_COLLABORATION.md](./docs/TEAM_COLLABORATION.md) §六。

## 仓库结构

| 目录 | 说明 |
|------|------|
| [docs/](./docs/) | **设计文档（必读 [docs/README.md](./docs/README.md)）** |
| [hospital-backend/](./hospital-backend/) | Java 17 · Spring Boot 3.4 · 微服务 |
| [hospital-frontend/](./hospital-frontend/) | Vue 3 · 医生/管理 PC |
| [hospital-patient-miniapp/](./hospital-patient-miniapp/) | 患者微信小程序（原生） |
| [hospital-ai/](./hospital-ai/) | Python FastAPI · CNN（待建） |

## 快速入口

- **团队协作**：[docs/TEAM_COLLABORATION.md](./docs/TEAM_COLLABORATION.md)
- **启动 + 联调验收**：[docs/RUNBOOK.md](./docs/RUNBOOK.md)（含 §十二 验收清单）
- **文档索引（分层阅读）**：[docs/README.md](./docs/README.md)
- **实施计划 + 开发动机**：[docs/IMPLEMENTATION_PLAN.md](./docs/IMPLEMENTATION_PLAN.md)
- **进度跟踪**：[docs/PROGRESS.md](./docs/PROGRESS.md)
- **前端↔API**：[docs/FRONTEND_API_MAP.md](./docs/FRONTEND_API_MAP.md)
- **微服务 + 架构图**：[docs/MICROSERVICES.md](./docs/MICROSERVICES.md)
- **环境搭建**：[docs/DEV_ENV_SETUP.md](./docs/DEV_ENV_SETUP.md)
- **API 契约**：[docs/API.md](./docs/API.md)（Base URL：`http://localhost:9000`）
- **建库脚本**：[docs/sql/README.md](./docs/sql/README.md)

## 当前阶段

**P3 核心已完成**（门诊 + 检验 + 检查 + 发药 + 退号退费退药）；详见 [docs/PROGRESS.md](./docs/PROGRESS.md)。
