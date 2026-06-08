# hospital-frontend（PC 医护端）

Vue 3 + Element Plus + Pinia + Axios · 经 Gateway `http://127.0.0.1:9000/api/v1`

## 启动

```bash
npm install
npm run dev
```

需本地已启：Nacos、Gateway、对应后端服务（见 [RUNBOOK.md](../docs/RUNBOOK.md)）。

## 开发账号（密码 123456）

| 用户名 | 角色 | 路由 |
|--------|------|------|
| doctor01 | 门诊医生 | `/doctor/workspace` |
| lab01 | 检验科 | `/lis/queue` |
| check01 | 检查检验科 | `/pacs/queue`（检查）、`/pacs/inspection-queue`（检验） |
| pharmacy01 | 药师 | `/pharmacy/pending` |
| registrar01 | 收费员 | `/registrar/*` |
| admin | 管理员 | `/admin/*`（可进所有受保护路由） |

## Mock 与联调

默认 **`VITE_USE_MOCK=true`**（见 `.env.development`），无需后端即可演示全角色界面。

- 契约详见 [`docs/API_INTERFACE_SPEC.md`](../docs/API_INTERFACE_SPEC.md)
- 联调真库：改 `VITE_USE_MOCK=false` 并启动 Gateway + 微服务

处置科 `/disposal/queue`：Mock 账号 `disposal01` / `123456`；`admin` 可访问各角色菜单。

## Mock

`.env.development` 中 `VITE_USE_MOCK=true` 可演示 PENDING 接口（窗口挂号/收费、AI 草稿）。见 [src/mock/README.md](./src/mock/README.md)。

## 实现顺序（2026-06）

| 批次 | 内容 | 状态 |
|------|------|------|
| **0** | 路由全角色、StaffShell、Mock 工具 | ✅ |
| **1** | 检验/检查/处置队列、管理员字典只读 | ✅ |
| **2** | 收费员窗口挂号/收费（Mock）、医生开检查 + AI 诊断条 | ✅ |
| **3** | 医生：确诊/finish、结果聚合、处方 AI 草稿编辑 UI | 待做 |
| **4** | AI 助理 SSE（lml 联调）、草稿逐步编辑弹窗 | 待做 |
| **5** | 管理员排班 + Timefold（P5） | 占位 |

对照 [FRONTEND_API_MAP.md](../docs/FRONTEND_API_MAP.md) · [TEAM_COLLABORATION.md](../docs/TEAM_COLLABORATION.md) §九。
