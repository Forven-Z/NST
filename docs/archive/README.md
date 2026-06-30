# 文档归档说明

本目录存放**已合并或停用**的过程性文档摘要，完整历史以 **Git 提交记录** 为准。

## 2026-06-04 文档精简

以下正文已合并入主文档，**勿再引用原文件名**：

| 原文件 | 合并至 |
|--------|--------|
| `LUNG_MODEL_INTEGRATION.md` | [AI_CNN_INTEGRATION.md](../AI_CNN_INTEGRATION.md) §十～§十一 |
| `LUNG_INTEGRATION_TEAM_CHANGELOG.md` | [AI_CNN_INTEGRATION.md](../AI_CNN_INTEGRATION.md) §十三、§十五 |
| `AI_TASK_TYPE_MINIMAL.md` | [AI_CNN_INTEGRATION.md](../AI_CNN_INTEGRATION.md) §十二 |
| `LUNG_CT_DATA_PLAN.md` | [AI_CNN_INTEGRATION.md](../AI_CNN_INTEGRATION.md) §十四 |
| `LUNG_ANNOTATION_NEXT.md` | 训练侧本地工程，不纳入 Git 主文档 |
| `RAG_RUN_GUIDE.md` | [RAG_GUIDE.md](../RAG_GUIDE.md) |
| `RAG_VECTOR_DATABASE.md` | [RAG_GUIDE.md](../RAG_GUIDE.md) §二 |
| `RAG_KNOWLEDGE_BASE.md` | [RAG_GUIDE.md](../RAG_GUIDE.md) §三 |
| `FRONTEND_API_MAP.md` | [API.md](../API.md) |
| `LOCAL_WORKSPACE.md` | [RUNBOOK.md](../RUNBOOK.md) §零、[DEV_ENV_SETUP.md](../DEV_ENV_SETUP.md) |

**留痕要点**（CNN 肺部合并 main，2026-06）：

- 分支 `feature/ai-task-type` 已合并；演示单 #62001 头部 / #62002 肺部 / #62006 肿瘤 STUB
- 权重：`shared/model-weights/best.pth`、`lung_artifact_best.pth`；安装脚本 `scripts/install-model-weights.ps1`
- CNN 仅输出掩码与 NIfTI 预览；文字报告由 LLM（ai-bridge）负责

查看删除前原文：`git log --all -- docs/LUNG_INTEGRATION_TEAM_CHANGELOG.md`
