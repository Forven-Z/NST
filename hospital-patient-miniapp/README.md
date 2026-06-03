# hospital-patient-miniapp

患者端 **原生微信小程序**（WXML + WXSS + JavaScript）。

## 技术栈（定稿）

- **微信官方原生框架**（非 uni-app）
- **微信开发者工具**：创建、编辑、预览、上传
- **`wx.request`**：调用 `hospital-gateway` REST API（`Result<T>`）
- **`wx.login`**：微信 code 换 JWT
- **`wx.uploadFile`**：医学影像上传（P4）
- UI：微信原生组件；可选 **WeUI** / **Vant Weapp**
- 语言：**JavaScript**（第一期）

## 功能范围（规划）

- 微信登录
- 线上挂号、待缴/缴费记录
- AI 智能问诊 / 导诊（P4，对接 `hospital-ai-bridge`）
- 电子病历查看
- 医学影像上传（→ MinIO → 异步 CNN，P4）

## 开发与运行

1. 安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)。
2. **导入项目** → 选择本目录 `hospital-patient-miniapp/`（含 `app.json` 的工程根）。
3. 开发阶段：**设置 → 安全** → 勾选「不校验合法域名」。
4. API 基址：`http://localhost:9000/api/v1`（见 `utils/request.js` 或 `app.js` globalData）。
5. 详见 **`docs/DEV_ENV_SETUP.md` §九**。

## 工程初始化（待做）

当前目录为占位说明；P0.5 在微信开发者工具中 **新建小程序** 或导入模板，代码保存在本目录，并提交：

- `app.js` / `app.json` / `app.wxss`
- `project.config.json`
- `pages/`、`utils/request.js` 等

## 后端依赖

- `hospital-patient`：挂号、费用、病历
- `hospital-auth`：`POST /api/v1/auth/patient/wechat/login`
- `hospital-ai-bridge`：AI 问诊（P4；原生侧可用轮询或分块读取，非 SSE 必需）

详见 `docs/PROJECT_REQUIREMENTS.md` §0.1、`docs/API.md` §四。
