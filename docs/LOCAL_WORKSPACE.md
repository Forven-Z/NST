# 本机工作目录说明（方案 A）

> **唯一工作目录**：`C:\Neuedu\NST-work`（Git 仓库，push / PR 用）  
> **已停用**：`C:\Neuedu\NST-main`（无 Git，勿再改代码）  
> **训练工程（肺部）**：`C:\Neuedu\6.3\BrainCT-Lung`（不在 Git 仓库内，本机保留）

## 日常流程

1. 用 Cursor / VS Code 打开 **`C:\Neuedu\NST-work`**
2. 在功能分支上开发（当前：`feature/ai-task-type`）
3. **基础设施**（每次联调前）：
   - MinIO：`C:\dev\start-minio-community.bat` 或 `scripts\start-minio-community.bat`
   - Nacos：`C:\dev\start-nacos.bat`（若未运行）
   - PostgreSQL：Windows 服务 `postgresql-x64-16`
4. 启动 Java + hospital-ai：`scripts\start-r-pacs-ai.bat`
5. 前端：`cd hospital-frontend && npm run dev`
6. 数据库：本机 PostgreSQL 库 **`hospital`**（密码 **`postgres`**，非文档里的 123456）
7. 提交：`git add` → `git commit` → `git push` → GitHub PR

## 本机基础设施路径（2026-06 已安装）

| 组件 | 路径 / 端口 |
|------|-------------|
| MinIO 社区版 | `C:\dev\minio\minio-community.exe`，数据 `C:\dev\minio-data`，API **9001** |
| Nacos | `C:\dev\nacos`，**8848** |
| PostgreSQL | `postgres` / `postgres`，库 `hospital` |
| hospital-ai venv | `NST-work\hospital-ai\.venv` |

## GPU 验证

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-hospital-ai-gpu.ps1
```

期望：`torch 2.6.0+cu124`、`cuda_available True`；健康检查 `device` 含 `cuda`。

## 首次切换到 NST-work 后请执行

```powershell
cd C:\Neuedu\NST-work

# GPU 版 PyTorch（若未装过）
powershell -ExecutionPolicy Bypass -File scripts/setup-hospital-ai.ps1

# 确认权重存在
dir hospital-ai\model\weights\best.pth

# 编译 Java（若 jar 不存在或刚 pull main）
cd hospital-backend
mvn package -DskipTests -pl hospital-gateway,hospital-auth,hospital-management,hospital-his,hospital-pacs -am
```

## 肺部数据（并行进行）

- LIDC 下载：`6.3/BrainCT-Lung/Datasets/raw_dicom/lidc/`
- 下完后：`docs/LUNG_ANNOTATION_NEXT.md`、`6.3/BrainCT-Lung/docs/胸部CT数据获取.md`

## 远程仓库

- `origin` → https://github.com/Forven-Z/NST.git
- 推送前确认：`git branch --show-current`
