# 本机工作目录说明（方案 A）

> **唯一工作目录**：`C:\Neuedu\NST-work`（Git 仓库，push / PR 用）  
> **已停用**：`C:\Neuedu\NST-main`、`C:\Neuedu\1\NST-main`（无 Git，勿再改代码）

## 日常流程

1. 用 Cursor / VS Code 打开 **`C:\Neuedu\NST-work`**
2. 在功能分支上开发（当前：`feature/ai-task-type`）
3. 启动：`scripts\start-r-pacs-ai.bat`（`ROOT` 已指向本目录）
4. 前端：`cd hospital-frontend && npm run dev`
5. 数据库：本机 PostgreSQL 库 **`hospital`**（与文件夹无关）
6. 提交：`git add` → `git commit` → `git push` → GitHub PR

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

## 远程仓库

- `origin` → https://github.com/Forven-Z/NST.git
- 推送前确认：`git branch --show-current`
