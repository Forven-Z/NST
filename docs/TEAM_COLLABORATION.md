# 智慧云脑诊疗平台 — 团队协作开发说明

> **用途**：约定「前端 + 库表先行、后端分模块并行」的协作方式，减少接口漂移与联调返工。  
> **适用对象**：全员（前端负责人、各后端模块负责人、联调/答辩）  
> **配套**：[FRONTEND_API_MAP.md](./FRONTEND_API_MAP.md) · [API.md](./API.md) · [DATABASE_DESIGN.md](./DATABASE_DESIGN.md) · [MICROSERVICES.md](./MICROSERVICES.md) · [PROGRESS.md](./PROGRESS.md)

---

## 一、协作总原则

### 1.1 分两阶段，契约居中

```text
阶段 A（前端 + 数据负责人）
  定稿/维护：页面、路由、交互、Mock、schema.sql、DATABASE_DESIGN、API.md、FRONTEND_API_MAP
  产出：可独立演示的前端（Mock 或已联调接口）

阶段 B（后端模块负责人，可并行）
  按 MICROSERVICES 边界实现各 jar
  严格遵循 API.md 路径与报文，不改契约除非走变更流程
  每模块自带验收脚本 + 更新 PROGRESS.md
```

**核心规则**：**先文档、后代码；先契约、后实现。** 不允许「代码里临时起路径、文档事后补」。

### 1.2 单一事实来源（冲突时按此顺序）

与 [README.md](./README.md) 一致：

1. `DESIGN_DECISIONS.md`（ADR 已定稿）
2. `MICROSERVICES.md`（服务边界、端口、表写归属）
3. `DATABASE_DESIGN.md`（表、字段、状态枚举）
4. `API.md`（HTTP 路径、Request/Response）
5. `BUSINESS_FLOW.md`（业务流程与状态迁移）
6. `FRONTEND_API_MAP.md`（页面 ↔ API 对照）
7. `PROJECT_REQUIREMENTS.md`（需求范围）

---

## 二、角色与职责


| 角色                                 | 主要职责                                       | 主要产出                                                                                        |
| ---------------------------------- | ------------------------------------------ | ------------------------------------------------------------------------------------------- |
| **前端 + 库表负责人**                     | PC / 小程序全页面；库表与 API 契约维护；Mock 层；联调清单       | `hospital-frontend/`、`hospital-patient-miniapp/`、`docs/sql/`、`API.md`、`FRONTEND_API_MAP.md` |
| **hospital-his 负责人**               | 门诊、患者端、挂号收费、药房、医嘱开立、registrar              | `hospital-his` 代码、`scripts/r-*-acceptance.ps1`（his 相关）                                      |
| **hospital-lis 负责人**               | 检验队列、执行、结果录入                               | `hospital-lis`、LIS 验收脚本                                                                     |
| **hospital-pacs 负责人**              | 检查队列、执行、结果、影像上传                            | `hospital-pacs`、PACS 验收脚本                                                                   |
| **hospital-auth 负责人**              | 登录、JWT、内部 token、角色                         | `hospital-auth`                                                                             |
| **hospital-management 负责人**        | 字典 CRUD、排班、报表                              | `hospital-management`                                                                       |
| **hospital-gateway 负责人**           | 路由、JWT 白名单、跨域                              | `hospital-gateway`                                                                          |
| **hospital-ai-bridge / Python AI** | SSE、推理任务（P4+）                              | `hospital-ai-bridge`、`hospital-ai-imaging/`                                                 |
| **全员**                             | 每周更新 [PROGRESS.md](./PROGRESS.md)；阻塞项写入 §六 | 活文档                                                                                         |


模块与端口详见 [MICROSERVICES.md](./MICROSERVICES.md) §二、§三。

---

## 三、阶段 A：前端 + 库表（先行）

### 3.1 前端工程范围


| 工程     | 路径                          | 说明                                          |
| ------ | --------------------------- | ------------------------------------------- |
| PC 医护端 | `hospital-frontend/`        | Vue 3 + Element Plus；按 **6 角色** 分路由与 Layout |
| 患者小程序  | `hospital-patient-miniapp/` | 挂号、待缴、支付、档案、病历、退号等                          |


**PC 角色与路由前缀（约定）**


| 角色    | `employee.role_type` | 路由前缀            | 用例图对应         |
| ----- | -------------------- | --------------- | ------------- |
| 门诊医生  | `OUTPATIENT_DOCTOR`  | `/doctor/`**    | 叫号、病历、开单、确诊   |
| 检验医生  | `LAB_DOCTOR` 等       | `/lis/**`       | 检验队列、执行、录入结果  |
| 检查医生  | `CHECK_DOCTOR` 等     | `/pacs/**`      | 检查队列、执行、录入结果  |
| 处置医生  | `DISPOSAL_DOCTOR` 等  | `/disposal/**`  | 处置队列、执行、录入结果  |
| 药师    | `PHARMACIST`         | `/pharmacy/**`  | 发药、退药         |
| 挂号收费员 | `REGISTRAR`          | `/registrar/**` | 挂号、收费、退费、费用查询 |
| 管理员   | `ADMIN`              | `/admin/**`     | 字典、排班、报表      |


登录后按 `roles` 跳转，见 `hospital-frontend/src/router/index.js` 现有模式。

### 3.2 页面完成标准（不要求后端已就绪）

每个页面需满足：

- 在 `FRONTEND_API_MAP.md` 有一行对照（Method + Path + 服务 + 阶段）  
- 有对应 `src/api/*.js` 封装（路径与 `API.md` 一致）  
- 后端未实现时：**Mock 可演示**（见 §3.4）  
- 列表/表单字段与 `API.md` Response 示例一致（含 `status` 数字与文案）  
- 空态、加载态、错误提示（`Result.code !== 200`）

### 3.3 库表完成标准

- 变更先改 `DATABASE_DESIGN.md`，再改 `docs/sql/schema.sql`  
- 状态枚举与 `BUSINESS_FLOW.md` §八一致（挂号、检查、检验、处方、处置）  
- 新表在 `MICROSERVICES.md` §表写归属中登记  
- 本地执行 `schema.sql` + `seed-dict.sql` 无报错  
- **冻结**：阶段 A 结束前组内评审一次，之后改表走 §五变更流程

### 3.4 Mock 约定（前端并行必备）

**环境变量**（建议）：

```env
# hospital-frontend/.env.development
VITE_API_BASE=http://127.0.0.1:9000/api/v1
VITE_USE_MOCK=false
```

**规则**：

1. Mock 数据放在 `hospital-frontend/src/mock/`，按模块分文件（如 `doctor.js`、`lis.js`）。
2. Mock 返回结构必须与 `API.md` 中 `Result<T>` 一致：`{ code, message, data, success }`。
3. 在 `src/api/request.js` 或各 API 文件中：当 `VITE_USE_MOCK=true` 且接口标记为 `PENDING` 时走 Mock。
4. 在 `FRONTEND_API_MAP.md` 用 `**PENDING`** 标记后端未实现的 API；实现后改为 `**DONE**` 并关 Mock。

**禁止**：Mock 字段与 `API.md` 不一致（例如 Mock 用 `id` 而契约用 `registerId`）。

---

## 四、阶段 B：后端分模块（并行）

### 4.1 认领与边界

每人只改 **自己模块** 的 `src/main/java` 与 `pom.xml`（及该模块验收脚本）。跨模块调用：

- 优先 **Feign + API 契约**（见 `MICROSERVICES.md` §Feign）  
- 禁止 his 直接写 lis 专属表（除非文档明确只读）

**表写归属**：实现前查 `DATABASE_DESIGN.md` §1.4 / `MICROSERVICES.md` 表矩阵。

### 4.2 后端完成标准（每个 API）

- 路径、Method、权限与 `API.md` 一致（Gateway 前缀 `/api/v1`）  
- 返回统一 `com.hospital.common.Result<T>`  
- 业务异常使用 `BusinessException` + `ErrorCode`  
- 状态变更符合 `BUSINESS_FLOW.md`（例如：仅 `已缴费(20)` 后可执行）  
- 在 `JwtAuthFilter`（服务内）与 `JwtAuthGlobalFilter`（gateway）补充路由鉴权（若为新前缀）  
- 提供或更新 `scripts/r-xxx-acceptance.ps1` 至少覆盖 happy path  
- 更新 `PROGRESS.md` 对应行

### 4.3 开发账号（联调）

见 `docs/sql/README.md` / `seed-dict.sql`：


| 账号          | 密码     | 角色   |
| ----------- | ------ | ---- |
| doctor01    | 123456 | 门诊医生 |
| lab01       | 123456 | 检验   |
| check01     | 123456 | 检查   |
| pharmacy01  | 123456 | 药师   |
| registrar01 | 123456 | 收费员  |
| admin       | 123456 | 管理员  |


对外统一经 **Gateway :9000**。

---

## 五、契约变更流程（必走）

当前后端未实现的 API（如处置、窗口挂号、确诊提交等），前端可先做 UI + Mock。**一旦要改契约**：

```text
1. 提 Issue / 站会说明原因
2. 先改 API.md + FRONTEND_API_MAP.md（+ DATABASE_DESIGN 若涉及表）
3. 前端负责人 Review 通过后，后端再改代码
4. PROGRESS.md 变更记录写一行
```

**禁止**：仅改后端代码、不更新 `API.md`。

---

## 六、Git 与分支约定

> **仓库**：[https://github.com/Forven-Z/NST](https://github.com/Forven-Z/NST) · 默认分支 `**main`**

### 6.1 规则

- 在 `**feature/*`、`fix/*`、`docs/***` 上开发并 push；**合并进 `main` 须走 GitHub Pull Request**（不能 `push origin main`）。
- Merge 前确认 `**main` 仍可运行**（相关服务能启、对应 `scripts/r-*-acceptance.ps1` 能过；改表则同步 `schema.sql` / `API.md`）。

### 6.2 分支命名


| 类型  | 示例                     |
| --- | ---------------------- |
| 功能  | `feature/his-disposal` |
| 修复  | `fix/gateway-jwt`      |
| 文档  | `docs/team-git`        |


### 6.3 克隆与认证

```bash
git clone https://github.com/Forven-Z/NST.git
cd NST
```

需 push 时：仓库 **Settings → Collaborators** 添加账号；`git push` 密码用 GitHub **Personal Access Token**（`repo` 权限），不是登录密码。

### 6.4 拉取（pull）

```bash
cd ~/Desktop/NST
git checkout main
git pull origin main
```

在开发分支上、且他人已 Merge 进 `main` 时（本地分支提交后未修改代码）：

```bash
git checkout feature/xxx
git pull origin main
```

把最新的 `main` 合进当前你在写的 feature 分支。若 pull 下来有新的 `docs/sql/*.sql`，按 [sql/README.md](./sql/README.md) 更新本地库。

如果本地有未提交的更改（有本地修改）：

```bash
git checkout feature/xxx   # 若已在 feature/xxx 上无需运行本行命令
git add .
git commit -m "feat(his): xxx"          # 可以先 commit，哪怕半成品
git pull origin main
# 有冲突 → 改文件 → git add . → git commit（merge commit）
git push origin feature/xxx
```

### 6.5 推送与合并

```bash
# 1. 建分支并开发
git checkout main && git pull origin main
git checkout -b feature/his-disposal
git add . && git commit -m "feat(his): 窗口挂号"
git push -u origin feature/his-disposal

# 2. GitHub：Pull requests → New → base: main ← compare: feature/his-disposal → Merge

# 3. 本地同步
git checkout main && git pull origin main
```

PR 合并前在 **Files changed** 看一眼 diff；**Merge commit** 即可。

### 6.6 提交规范

commit 前缀建议：`feat:` / `fix:` / `docs:` + 模块名。改契约/改表先走 §五。

### 6.7 常见问题


| 现象             | 处理                                          |
| -------------- | ------------------------------------------- |
| 不能 push `main` | 正常；push 工作分支并开 PR                           |
| push 分支被拒绝     | 先 `git pull origin main` 再 push             |
| PR 冲突          | 在工作分支上 `pull origin main`，解决冲突后 commit、push |
| 认证失败           | 用 PAT；或清除 Windows 凭据里旧的 github.com          |


---

## 七、联调流程

### 7.1 日常联调

1. 按 [RUNBOOK.md](./RUNBOOK.md) 启动依赖服务
2. 前端 `VITE_USE_MOCK=false`，指向 `http://127.0.0.1:9000/api/v1`
3. 对照 [RUNBOOK.md](./RUNBOOK.md) §十二 逐步验收
4. 问题分类：**契约问题** → 改文档；**实现 bug** → 改对应模块代码

### 7.2 模块就绪定义（Ready for Frontend）

某 API 标记为 **DONE** 需同时满足：

- 后端已实现且经 Gateway 可达  
- `API.md` 与实现一致  
- 验收脚本 PASS 或 Postman/联调清单有对应步骤 PASS  
- 前端已关 Mock、页面走真实接口

### 7.3 里程碑验收脚本


| 组合         | 脚本                                  | 说明       |
| ---------- | ----------------------------------- | -------- |
| R-min      | `scripts/r-min-acceptance.ps1`      | 挂号、接诊、病历 |
| R-lis      | `scripts/r-lis-acceptance.ps1`      | 检验闭环     |
| R-pacs     | `scripts/r-pacs-acceptance.ps1`     | 检查闭环     |
| R-pharmacy | `scripts/r-pharmacy-acceptance.ps1` | 处方发药     |
| R-reversal | `scripts/r-reversal-acceptance.ps1` | 退号/退费/退药 |


新模块应新增或扩展现有脚本，**禁止**仅口头验收。

---

## 八、前后端 Handoff 清单

阶段 A 向阶段 B 交接时，前端 + 库表负责人提供：


| #   | 交付物                       | 位置                                               |
| --- | ------------------------- | ------------------------------------------------ |
| 1   | 页面 ↔ API 对照（含 PENDING 标记） | `FRONTEND_API_MAP.md`                            |
| 2   | HTTP 契约                   | `API.md`                                         |
| 3   | 表结构定稿                     | `DATABASE_DESIGN.md` + `docs/sql/schema.sql`     |
| 4   | 可演示前端                     | `hospital-frontend/`、`hospital-patient-miniapp/` |
| 5   | Mock 说明                   | `hospital-frontend/src/mock/README.md`（建议新建）     |
| 6   | 未实现 API 列表                | 见下表「当前 PENDING 摘要」或 `PROGRESS.md`                |


后端负责人认领后更新 `PROGRESS.md` 负责人列与状态。

---

## 九、模块认领表（模板）

> 复制到 `PROGRESS.md` 或飞书/Notion；**认领后填姓名**。


| 模块                   | 端口   | 负责人 | 状态  | 验收脚本                          |
| -------------------- | ---- | --- | --- | ----------------------------- |
| hospital-gateway     | 9000 |     |     | —                             |
| hospital-auth        | 9101 |     |     | —                             |
| hospital-his         | 9102 |     |     | r-min, r-pharmacy, r-reversal |
| hospital-lis         | 9103 |     |     | r-lis                         |
| hospital-pacs        | 9104 |     |     | r-pacs                        |
| hospital-management  | 9105 |     |     | r-modules-smoke               |
| hospital-ai-bridge   | 9106 |     |     | r-modules-smoke               |
| hospital-ai (Python) | 8000 |     |     | P4                            |
| PC 前端                | —    |     |     | 人工 + 联调清单                     |
| 患者小程序                | —    |     |     | 人工 + 联调清单                     |


---

## 十、当前实现与 PENDING 摘要（2026-05）

便于后端认领时对齐；**以代码与 PROGRESS 为准**，本文仅作协作快照。

### 10.1 已有后端接口（可关 Mock 联调）

- 患者：微信登录、挂号、模拟支付、退号、退费、档案、病历  
- 医生：队列、叫号、病历、开检验/检查/处方、查结果  
- 检验/检查：`/lis/`**、`/pacs/**` 队列、execute、result  
- 药房：待发药、发药、退药  
- 收费员：按病历号查账单、退费、退号  
- 管理：字典只读

### 10.2 文档有、后端 PENDING（前端可 Mock）


| 能力                 | 典型 API                                            | 建议负责模块             |
| ------------------ | ------------------------------------------------- | ------------------ |
| 窗口挂号/收费            | `POST /registrar/registers`、`POST /charge/settle` | his · registrar    |
| 门诊确诊               | `POST .../medical-record/confirm`                 | his · outpatient   |
| 结束看诊               | `POST /doctor/.../finish`                         | his · outpatient   |
| 处置全流程              | `/disposal-requests/**`、处置科 execute/result        | his + 处置子模块或 his   |
| 药库入库/交易记录          | `/admin/drugs` CRUD、发退药流水                         | management + his   |
| 排班 CRUD / Timefold | `/admin/schedules/**`                             | management         |
| AI SSE / CNN       | `/ai/**`、`hospital-ai`                            | ai-bridge + Python |
| 患者退款/支付记录列表        | `GET /patient/refunds`、`GET /patient/payments`    | his · patient      |


前端做这些页面时，在 `FRONTEND_API_MAP.md` 标 **PENDING** 并启用 Mock。

---

## 十一、站会与进度


| 频率     | 内容                                  |
| ------ | ----------------------------------- |
| 每周 1 次 | 对照 `PROGRESS.md`、PENDING 清零计划、阻塞项   |
| 契约变更   | 随时同步，**必须先改文档**                     |
| 里程碑前   | 跑通对应 `r-*-acceptance.ps1` + 前端全角色点检 |


---

## 十二、修订记录


| 日期      | 说明               |
| ------- | ---------------- |
| 2026-05 | 首版及后续协作/Git 约定迭代 |


