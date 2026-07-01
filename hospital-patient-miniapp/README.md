# hospital-patient-miniapp

患者端 **原生微信小程序**，对接云脑 HIS 门诊闭环（Mock / Gateway 联调）。

## 界面结构（已定稿）

| Tab | 内容 |
|-----|------|
| **首页** | 「您好，{账号名}」+ **就诊人切换** · **当前就医行程卡** · **门诊 / 住院 / 报告 / 其它** 四 Tab（门诊 5 格：挂号/待缴费用/挂号记录/分诊/病历；报告 4 格纯查结果） · 就医提示 |
| **报告** | 检验/检查/处置报告（首页报告分区 + 底部 Tab；日期分组 · 含影像角标 · 下拉刷新） |
| **个人中心** | 档案 · 就诊人/挂号/待缴/缴费记录/报告/病历 · 退出 |

## 支付说明

- 当前为 **演示级微信支付**：调起确认弹窗 + 后端 `POST /patient/payments` 模拟入账，**不产生真实扣款**。
- 已缴记录可点进 **缴费详情**；待缴账单可展开 **明细行**（处方药品/医技项目等）。

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
psql -U postgres -d hospital -f docs\sql\patch-pharmacy-reject.sql
```

（新库已跑过 `schema.sql` 且含 `patient_family_link`、prescription 驳回字段时，对应 patch 可跳过。）

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

依次启动 **Nacos :8848** → **auth :9101** → **his :9102** → **patient :9108** → **pharmacy :9109** → **gateway :9000**。脚本会等待 login 接口可用（避免 Gateway 已起但 patient 未注册 Nacos 导致 **503**）。日志在 `logs/r-min/`。

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

### 5. 开发者工具卡顿 / `SdkReport` 报错

控制台里反复出现：

```text
POST https://report-online.sh.wxgateway.com/SdkReport net::ERR_CONNECTION_CLOSED
```

这是**微信开发者工具 / 基础库内部的统计上报**，不是本小程序业务接口，一般**不影响** `127.0.0.1:9000` 联调。常见原因：Windows 网络/代理、防火墙或工具版本与基础库（如 3.16.x）组合导致上报失败。

**建议（按顺序试）：**

1. **可忽略**：只要 Network 里 `/api/v1/patient/**` 正常，业务不受此错误影响。
2. **详情 → 本地设置**：勾选「不校验合法域名…」；关闭「增强编译」「热重载」试是否更流畅。
3. **详情 → 本地设置 → 调试基础库**：与 `project.config.json` 的 `libVersion` 对齐（如 3.5.7），或换稳定版，避免工具 1.06 + 基础库 3.16 组合异常。
4. **确认后端已启**：未启动 Gateway 时首页会串行等待多个接口，体感像「整站很卡」——先跑 `.\scripts\start-r-min.ps1 -Restart`。
5. 本仓库已在 `project.private.config.json` 关闭 `useApiHook` / `useApiHostProcess`，减轻工具 Hook 开销（需**重新编译**小程序后生效）。

## Mock 演示（无后端）

1. 删除或重命名 `config.local.js`（或设 `USE_MOCK: true`）
2. 微信开发者工具导入本目录
3. Tab 图标异常时：`node scripts/gen-tab-icons.js`

## 患者端能力（与 PC Mock 闭环一致）

```
登录 → 切换就诊人 → 挂号 → 待缴/演示微信支付 → 候诊进度
     → 行程卡引导 → 报告/病历/处方详情
```

### 联调演示数据提示

| 能力 | 条件 |
|------|------|
| **电子病历** | 医生端对该次挂号 **确诊并提交**（`medical_record.status=2`） |
| **检查三视图** | PACS 为检查单写入 `imaging_study.report_json.reportSnapshots` 并重采 PNG；详见 `docs/IMAGING_DATA_ACCESS.md` |
| **报告空态** | 有进行中检验/检查时 `pendingCount>0`，列表显示「报告尚未出具」 |

验收脚本扩展：`scripts/miniapp-smoke.ps1`（挂号/账单/报告/病历/缴费详情）。

详见 `docs/API.md`（附录 A）、`docs/RUNBOOK.md` §6.2、`scripts/start-r-min.ps1`。
