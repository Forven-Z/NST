# hospital-patient-miniapp

患者端 **原生微信小程序**，对接云脑 HIS 门诊闭环（Mock / Gateway 联调）。

## 界面结构（已定稿）

| Tab | 内容 |
|-----|------|
| **首页** | 「您好，{账号名}」+ **就诊人切换** · **当前就医行程卡** · **门诊 / 住院 / 检查 / 其它** 四 Tab · 就医提示 |
| **报告** | 检验/检查报告列表（按当前就诊人 · Tab 栏入口） |
| **个人中心** | 档案 · 就诊人/挂号/待缴/缴费记录/报告/病历 · 退出 |

## 运行模式

| 模式 | 配置 | 说明 |
|------|------|------|
| **Mock 演示** | 无 `config.local.js` | 离线；默认账户手机 `13800138001` + 身份证 `110101199001011234` |
| **联调** | `config.local.js` → `USE_MOCK: false` | Gateway `127.0.0.1:9000` |

## 账户模型（QQ 式）

- **登录**：手机号 + 身份证 + 验证码（Mock 可 `000000`）→ JWT = 病人账户
- **切换**：首页/个人中心切换 → `POST /patient/auth/switch-account` 换 JWT
- **添加账户**：个人中心或登录页「添加其他病人账户」
- **微信**：仅在支付前绑定（`/patient/auth/wechat/bind`），不是登录入口

## 快速联调（推荐）

### 1. 准备数据库

```powershell
cd C:\Users\King\Desktop\NST
$env:PGPASSWORD='123456'
psql -U postgres -d hospital -f docs\sql\patch-family-link.sql
psql -U postgres -d hospital -f docs\sql\patch-family-link-guardian.sql
psql -U postgres -d hospital -f docs\sql\seed-dict.sql
psql -U postgres -d hospital -f docs\sql\patch-scheduling-refresh.sql
```

（新库已跑过 `schema.sql` 且含 `patient_family_link` 时，前两步可跳过。）

### 2. 启动 R-min 后端

```powershell
.\scripts\start-r-min.ps1
# 若曾手动停过部分进程、出现 503，请完整重启：
.\scripts\start-r-min.ps1 -Restart
```

停止：

```powershell
.\scripts\stop-r-min.ps1
```

依次启动 **Nacos :8848** → **auth :9101** → **his :9102** → **gateway :9000**。脚本会等待 login 接口可用（避免 Gateway 已起但 HIS 未注册 Nacos 导致 **503**）。日志在 `logs/r-min/`。

验收：

```powershell
.\scripts\miniapp-smoke.ps1
```

### 3. 小程序切联调

```powershell
cd hospital-patient-miniapp
copy config.local.example.js config.local.js
```

确认 `config.local.js`：

```javascript
module.exports = {
  API_BASE: 'http://127.0.0.1:9000/api/v1',
  USE_MOCK: false,
}
```

### 4. 微信开发者工具

1. 导入 **`hospital-patient-miniapp/`**
2. **详情 → 本地设置** → 勾选 **不校验合法域名、web-view、TLS**
3. 编译运行 → **登录页** 填写本人档案 → 登录
4. 验证：个人档案、就诊人、挂号、待缴

登录页底部会显示当前为 Mock 或联调模式。

## Mock 演示（无后端）

1. 删除或重命名 `config.local.js`（或设 `USE_MOCK: true`）
2. 微信开发者工具导入本目录
3. Tab 图标异常时：`node scripts/gen-tab-icons.js`

## 患者端能力（与 PC Mock 闭环一致）

```
登录 → 切换就诊人 → 挂号 → 待缴/支付 → 排队候诊
     → 行程卡引导 → 报告/病历
```

详见 `docs/API.md`（附录 A）、`docs/RUNBOOK.md` §6.2、`scripts/start-r-min.ps1`。
