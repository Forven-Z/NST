# hospital-frontend（PC 医护端）

Vue 3 + Element Plus + Pinia + Axios · 经 Gateway `http://127.0.0.1:9000/api/v1`

## 启动

```bash
npm install
npm run dev
```

需本地已启：Nacos、Gateway、对应后端服务（见 [RUNBOOK.md](../docs/RUNBOOK.md)）。

## 开发账号（密码 123456）

完整列表见 [`docs/sql/README.md` §三](../docs/sql/README.md#三测试账号seed-写入后)。

| 用户名 | 角色 | 路由 |
|--------|------|------|
| doctor01～doctor06 | 门诊医生（内科/外科） | `/doctor/workspace` |
| check01～check03 | 检查科（放射/CT） | `/pacs/queue` |
| lab01 / lab02 | 检验科 | `/lis/queue` |
| pharmacy01 | 药师 | `/pharmacy/pending` |
| registrar01 | 收费员 | `/registrar/*` |
| disposal01 | 处置科 | `/disposal/queue` |
| admin | 管理员 | `/admin/*`（可进所有受保护路由） |

## Mock 与联调

默认 **`VITE_USE_MOCK=true`**（见 `.env.development`），无需后端即可演示全角色界面。

- 契约详见 [`docs/API.md`](../docs/API.md)、[`docs/FRONTEND_API_MAP.md`](../docs/FRONTEND_API_MAP.md)
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
| **3** | 医生：确诊提交、diseaseIds、处方 AI 草稿三步、resultText 结果展示 | ✅ |
