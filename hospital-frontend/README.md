# hospital-frontend

**医生端**与**管理端** PC Web 应用（不包含患者端）。

## 技术栈（定稿）

- Vue 3 + `<script setup>`
- Element Plus
- Pinia
- Axios（业务 API）；AI 流式使用 **fetch + SSE**
- Vite
- 语言：**JavaScript**（第一期）；有余力再迁 TypeScript

## 目录

```
src/views/
├── doctor/    # 医生：7:3 布局（左病历 70%，右 AI 助理 30%）
└── admin/     # 管理：排班、基础数据等
```

患者端已迁移至仓库根目录 **`hospital-patient-miniapp/`**（**原生微信小程序**，微信开发者工具开发）。

## 开发

```bash
npm install
npm run dev
```

## 文档

`docs/PROJECT_REQUIREMENTS.md`
