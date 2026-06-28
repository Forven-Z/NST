# 智慧云脑诊疗平台 — 数据库表设计说明

> **文档性质**：表级设计说明书（**非建表脚本**）。  
> **文档索引**：[README.md](./README.md)  
> **数据库**：PostgreSQL 15+（业务库 `hospital`）；向量扩展见 **§十**。  
> **状态枚举**：§1.5；与 `BUSINESS_FLOW.md` §八、【态】及 `PROJECT_REQUIREMENTS.md` §2.4 一致。  
> **微服务表写归属**：§1.4（与 [MICROSERVICES.md](./MICROSERVICES.md) §二 一致）  
> **版本**：v1.15 | 2026-06

---

## 一、设计约定

### 1.1 命名与类型

| 约定 | 说明 |
|------|------|
| 表名、字段名 | 小写 + 下划线（`snake_case`） |
| 主键 | `id`，类型 `BIGSERIAL` |
| 业务标识 | 单据类表 **`prescription`**、**`bill`**、**`payment_record`**、**`refund_record`** **不设**独立 `*_no` 字段；列表展示、API 关联、`bill.biz_id` 指向的目标均使用各表 **`id`**（§7.1、§八）。仍保留 **`patient.medical_record_no`**、**`employee.emp_no`**、**`imaging_study.study_no`**、**`ai_chat_session.session_no`** 等档案/外部编号字段 |
| 时间 | `TIMESTAMPTZ`（带时区） |
| 金额 | `NUMERIC(10,2)`，单位：元 |
| 逻辑删除 | `delmark`：`0` 有效，`1` 删除（与参考需求一致）。**例外**：`scheduling` 不用 `delmark`，作废见 `publish_status=2`（§1.5） |
| 审计字段 | 单据/档案类表默认含 `create_time`、`update_time`；部分表含 `create_by`、`update_by`（员工 `employee.id`）。**例外**：`prescription_item` 不含 `create_time`（见 §7.2）；`scheduling_leave_request` 仅含 `create_time`（见 §3.9）；`prescription` 开立/发药时间见 §7.1 约定（无 `order_time` / `dispense_time`） |
| 字段表格式 | 各表字段表含 **业务说明** 列，描述该字段在门诊流程中的含义与用途（对齐教学 PPT 写法） |
| 空值列缩写 | 表中「空」列：`N` = NOT NULL，`Y` = 可空 |

### 1.2 键符号说明（下文表格「键」列）

| 符号 | 含义 |
|------|------|
| **PK** | 主键 |
| **FK** | 外键 → `表名(字段)` |
| **UK** | 唯一约束 |
| **IX** | 建议普通索引（非唯一） |

### 1.3 表分组总览

| 分组 | 表数量 | 表名 |
|------|--------|------|
| A. 基础字典 | 8 | `department`, `regist_level`, `settle_category`, `employee`, `scheduling`, `drug_info`, `disease`, `medical_technology` |
| A′. 排班扩展 | 1 | `scheduling_leave_request`（职员请假；DDL 见 `schema.sql`、`patch-scheduling-leave.sql`） |
| B. 患者与认证 | 3 | `patient`, `patient_wechat`, `sys_user` |
| B′. 患者扩展 | 1 | `patient_family_link`（小程序家属；DDL 见 `schema.sql`） |
| C. 挂号就诊 | 3 | `register`, `medical_record`, `medical_record_disease` |
| D. 医技医嘱 | 3 | `check_request`, `inspection_request`, `disposal_request` |
| E. 处方 | 3 | `prescription`, `prescription_item`, `ai_prescription_draft` |
| F. 收费支付 | 4 | `bill`, `payment_record`, `payment_bill`, `refund_record` |
| G. 影像 AI | 2 | `imaging_study`, `ai_chat_session` |
| H. 向量/RAG | — | 见 **§十**（由 Spring AI 管理，本文仅说明） |

**合计：26 张核心业务关系表 + 2 张扩展表**（`patient_family_link`、`scheduling_leave_request`；不含向量表）。

### 1.4 微服务与表写归属矩阵

> 共享库 `hospital`；**写** 须遵守下表，**读** 可只读他表或通过 Feign。权威说明见 `MICROSERVICES.md` §二。

| 表名 | 主写服务 | 说明 |
|------|----------|------|
| `sys_user` | hospital-auth | 员工登录账号 |
| `patient`, `patient_wechat`, `patient_family_link` | hospital-his | 患者主数据、微信绑定与家属关系 |
| `register`, `medical_record`, `medical_record_disease` | hospital-his | 挂号与病历 |
| `prescription`, `prescription_item`, `ai_prescription_draft` | hospital-his | 处方（开立、发药状态由 his 协调） |
| `disposal_request` | hospital-his + hospital-disposal | **his**：开立与 status 10/20/50；**disposal**：执行与结果 30/40（ADR-017） |
| `bill`, `payment_record`, `payment_bill`, `refund_record` | hospital-his | 待缴与支付流水 |
| `inspection_request` | hospital-lis | 全生命周期写；**开立字段**由 his 创建时写入 |
| `check_request`, `imaging_study` | hospital-pacs | 检查单与影像任务；开立由 his Feign 触发 |
| `department`, `employee`, `regist_level`, `settle_category`, `scheduling`, `scheduling_leave_request`, `drug_info`, `disease`, `medical_technology` | hospital-management | 字典、排班与请假 |
| `ai_chat_session` | hospital-ai-bridge | 对话会话（P4+） |

### 1.5 全局状态枚举（实现用 `SMALLINT` 或 PostgreSQL `ENUM`）

#### `visit_state` — 挂号看诊状态（`register.visit_state`）

| 值 | 含义 | 对应【态】 |
|----|------|------------|
| 1 | 已挂号 | 患者窗口挂号（含挂号费已付） |
| 2 | 医生接诊 | 医生叫号后 |
| 3 | 看诊结束 | 医生开立处方或处置后（终态） |
| 4 | 已退号 | 退号终态 |

#### `medical_order_status` — 检查/检验/处置单（`check_request` 等 `.status`）

| 值 | 含义 |
|----|------|
| 10 | 已开立 |
| 20 | 已缴费 |
| 30 | 执行完成 |
| 40 | 已出结果 |
| 50 | 已退费 |

> 约束：**仅 `已缴费(20)` 后可进入 `执行完成(30)`**；**`已退费(50)` 仅来自 `已缴费(20)`**。

#### `prescription_status` — 处方主表（`prescription.status`）

| 值 | 含义 |
|----|------|
| 10 | 已开立 |
| 15 | 药师驳回 |
| 20 | 已缴费 |
| 30 | 已发药 |
| 40 | 已退药 |
| 50 | 已退费 |

#### `bill_status` — 待缴单（`bill.status`）

| 值 | 含义 |
|----|------|
| 0 | 待支付 |
| 1 | 已支付 |
| 2 | 已退款 |
| 9 | 已作废 |

#### `bill_biz_type` — 待缴单业务类型（`bill.biz_type`）

| 值 | 含义 | `biz_id` 指向 |
|----|------|----------------|
| `REGISTER` | 挂号费 | `register.id` |
| `CHECK` | 检查费 | `check_request.id` |
| `INSPECTION` | 检验费 | `inspection_request.id` |
| `DISPOSAL` | 处置费 | `disposal_request.id` |
| `PRESCRIPTION` | 处方费 | `prescription.id` |

#### `payment_channel` — 支付渠道

| 值 | 含义 |
|----|------|
| `WECHAT` | 微信小程序微信支付 |
| `CASH` | 窗口现金 |
| `SCAN` | 窗口扫码（支付宝/微信收款码等，记账用） |

#### `payment_status` — 支付单状态

| 值 | 含义 |
|----|------|
| 0 | 待支付 |
| 1 | 支付成功 |
| 2 | 支付失败 |
| 3 | 已关闭 |

#### `imaging_study_status` — 影像任务

| 值 | 含义 |
|----|------|
| `PENDING` | 已上传，待分析 |
| `PROCESSING` | CNN 推理中 |
| `COMPLETED` | 可查看报告 |
| `FAILED` | 失败 |

#### `scheduling_publish_status` — 排班发布状态（`scheduling.publish_status`）

| 值 | 含义 | 患者端可挂号列表 |
|----|------|------------------|
| 0 | 草稿 | 否 |
| 1 | 已发布 | 是（且须 `work_date` ≥ 服务器当前日期、有剩余号源） |
| 2 | 已取消 | 否 |

> 取消排班：将 `publish_status` 置为 **2**，**不使用** `delmark`。已有 `register` 引用时 **禁止物理删除**，仅取消；`used_quota=0` 且无挂号引用时管理端可选物理删除（实现约定）。

#### `medical_record_status` — 病历状态（`medical_record.status`）

| 值 | 含义 | 患者小程序 |
|----|------|------------|
| 0 | 书写中 | **不可见** |
| 1 | 已保存 | **不可见** |
| 2 | 已确诊提交 | **可见**（电子病历等仅 `status = 2`） |

> 医生保存(1)与确诊提交(2)区分书写进度；**患者端定稿：仅 2**。

#### `sys_user_status` — 登录账号状态（`sys_user.status`）

| 值 | 含义 |
|----|------|
| 1 | 启用（可登录） |
| 0 | 禁用 |

#### `ai_prescription_draft_status` — AI 处方草稿（`ai_prescription_draft.status`）

| 值 | 含义 |
|----|------|
| 0 | 草稿 |
| 1 | 已提交（已写入正式 `prescription`） |
| 9 | 已废弃 |

#### `refund_status` — 退款流水（`refund_record.status`）

| 值 | 含义 |
|----|------|
| 0 | 处理中 |
| 1 | 退款成功 |
| 2 | 退款失败 |

#### `leave_request_status` — 排班请假状态（`scheduling_leave_request.status`）

| 值 | 含义 | 业务说明 |
|----|------|----------|
| 0 | 待审 | 职员已提交，等待管理员审批 |
| 1 | 已批准 | 管理员批准；排班 `employee_id` 仍为原申请人，待手工/AI 替班 |
| 2 | 已驳回 | 管理员驳回，可填 `reject_remark` |
| 3 | 已撤销 | 职员在待审期间自行撤销 |
| 4 | 已替班 | 管理员将排班 `employee_id` 换为替班医生后写入；记录 `substitute_employee_id`、`substitute_time` |

> 同一排班在 **待审(0)** 或 **已批准(1)** 状态下最多一条记录（部分唯一索引，见 §3.9）。**不修改** `scheduling` 表结构；替班仅 `UPDATE scheduling SET employee_id = ?`。

---

## 二、ER 关系概要

```text
patient ──1:N── register ──1:1── medical_record
   │              │
   │              ├──1:N── check_request / inspection_request / disposal_request
   │              ├──1:N── prescription ──1:N── prescription_item ──FK── drug_info
   │              └──1:N── bill ──N:M── payment_record (经 payment_bill)
   │
   └──1:1── patient_wechat

employee ──FK── department
scheduling ──FK── employee, regist_level（出诊科室经 employee.dept_id 推导，无跨科会诊排班）
scheduling ──1:N── scheduling_leave_request ──FK── employee（申请人 / 替班人）
register ──FK── department, employee(医生), regist_level, settle_category

medical_technology ──FK── check_request / inspection_request / disposal_request
disease ──N:M── medical_record (medical_record_disease)
```

> ER 大图占位：`docs/images/er-diagram.png`（可根据本文绘制）。  
> **业务单号**：处方与费用/支付/退款以各表 **`id`** 标识；`bill.biz_id` → 对应业务表 **`id`**（`register` / `*_request` / `prescription`）。排班出诊科室经 **`employee.dept_id`** 推导，`scheduling` 不存 `dept_id`。

---

## 三、A 组 — 基础字典

### 3.1 `department` — 科室表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| dept_code | VARCHAR(32) | N | — | UK | 科室编码；院内唯一，用于挂号、排班与统计归类。 |
| dept_name | VARCHAR(64) | N | — | — | 科室名称；展示给患者与医护（如「内科」「放射科」）。 |
| dept_type | SMALLINT | Y | NULL | — | 科室类别；1 门诊 2 医技 3 药房 4 行政等，用于权限与菜单路由。 |
| parent_id | BIGINT | Y | NULL | — | FK → department(id)；上级科室 ID；支持科室树形结构，无上级则为空。 |
| sort_no | INTEGER | Y | 0 | — | 同级科室显示排序号；数值越小越靠前。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 3.2 `regist_level` — 挂号级别表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| level_code | VARCHAR(32) | N | — | UK | 号别编码；如 NORMAL（普通号）、EXPERT（专家号）。 |
| level_name | VARCHAR(32) | N | — | — | 号别名称；如普通号、专家号，窗口与小程序展示用。 |
| regist_fee | NUMERIC(10,2) | N | — | — | 挂号费单价（元）；普通号 20、专家号 65，开立挂号待缴单时快照引用。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 3.3 `settle_category` — 结算类别表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| category_code | VARCHAR(32) | N | — | UK | 结算类别编码；如自费、医保（一期可仅 SELF_PAY）。 |
| category_name | VARCHAR(32) | N | — | — | 如 自费、医保（一期可仅自费）；结算类别名称；挂号与收费界面展示。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 3.4 `employee` — 医院员工表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| emp_no | VARCHAR(32) | N | — | UK | 工号；员工院内唯一标识，可与登录名对应。 |
| real_name | VARCHAR(64) | N | — | — | 员工真实姓名；界面展示与处方/病历署名。 |
| gender | SMALLINT | Y | NULL | — | 性别；1 男 2 女。 |
| dept_id | BIGINT | N | — | — | FK → department(id)；员工所属科室；权限与 **`scheduling` 出诊科室推导**（经本字段 JOIN，排班表不重复存科室）。 |
| title | VARCHAR(32) | Y | NULL | — | 职称；如主治医师、主管药师。 |
| role_type | VARCHAR(32) | N | — | IX | 岗位角色；决定 PC 登录后菜单与 API 权限（门诊医生、检查医生、药师等）。 |
| phone | VARCHAR(20) | Y | NULL | — | 联系电话；人事与内部联系用。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

> 登录密码在 `sys_user`，不在本表存明文。

---

### 3.5 `scheduling` — 排班表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| employee_id | BIGINT | N | — | — | FK → employee(id)；出诊医生；该排班时段由该医生接诊。出诊科室 **不单独存**，经 `employee.dept_id` 查询（无跨科会诊排班；不考虑员工调科历史）。 |
| regist_level_id | BIGINT | N | — | — | FK → regist_level(id)；号别；决定挂号费与专家/普通队列。 |
| work_date | DATE | N | — | IX | 出诊日期；患者选号与线上挂号依据。 |
| noon_type | SMALLINT | N | — | — | 午别：1 上午 2 下午 3 晚上；午别；1 上午 2 下午 3 晚上。 |
| total_quota | INTEGER | N | 0 | — | 总号源数；该排班时段可挂号的总名额。 |
| used_quota | INTEGER | N | 0 | — | 已挂数量；挂号成功后递增，用于号源控制。 |
| publish_status | SMALLINT | N | 0 | IX | 发布状态，见 §1.5 `scheduling_publish_status`：**0 草稿**、**1 已发布**（可挂号）、**2 已取消**（作废，替代逻辑删除）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

**建议索引**：`IX (work_date, employee_id, noon_type)`、`IX (publish_status, work_date)`。

**建议唯一约束**：同一医生、同一午别、同一号别、同一出诊日，在 **非已取消** 状态下仅一条排班——PostgreSQL 部分唯一索引示例：`UNIQUE (work_date, employee_id, noon_type, regist_level_id) WHERE publish_status <> 2`。

**按科室查排班（患者挂号）**：`scheduling` JOIN `employee` ON `employee_id`，`publish_status = 1`，`work_date` ≥ 当前日期，过滤 `employee.dept_id`。

**管理端**：可按日期范围查询含 **已取消(2)** 的历史排班；取消操作即 `publish_status := 2`。

**请假关联**：职员对某排班申请请假写入 **`scheduling_leave_request`**（§3.9）；本表结构不变。批准待替班时 `employee_id` 仍为原医生；替班完成后更新 `employee_id` 并将请假置 **已替班(4)**。

**周模板预填**：管理员维护的固定周模板见 **`scheduling_template`**（§3.5.1）；模板仅作批量排班时的预填来源，应用后仍写入本表（草稿/发布流程不变）。

---

### 3.5.1 `scheduling_template` — 排班周模板表

> **业务定位**：管理员为职员维护「每周固定」出诊模板（职员 × 星期 × 午别）；用于管理端周网格批量排班时的 **预填**，不承载实际出诊记录。设计见 `docs/superpowers/specs/2026-06-28-scheduling-batch-design.md`。

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| employee_id | BIGINT | N | — | — | FK → employee(id)；模板所属职员；出诊科室经 `employee.dept_id` 推导（与 `scheduling` 一致，本表不存 `dept_id`）。 |
| weekday | SMALLINT | N | — | — | 星期；**ISO 8601 约定**：**1=周一** … **7=周日**；与周网格列对应。 |
| noon_type | SMALLINT | N | — | — | 午别：1 上午 2 下午 3 晚上；与 `scheduling.noon_type` 一致。 |
| regist_level_id | BIGINT | N | — | — | FK → regist_level(id)；号别；决定预填时的号别与默认号源规则。 |
| total_quota | INTEGER | N | — | — | 总号源数；应用模板写入 `scheduling` 草稿时的 `total_quota` 预填值。 |
| enabled | SMALLINT | N | 1 | — | 是否启用；**1 启用**、**0 停用**；停用模板不参与「应用模板」预填。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

**建议唯一约束**：同一职员、同一星期、同一午别仅一条模板——`UNIQUE (employee_id, weekday, noon_type)`（`ux_scheduling_template_slot`）。

**与 `scheduling` 的关系**：模板 **仅作预填来源**；「应用模板」或周网格编辑后，实际排班仍 **INSERT/UPDATE `scheduling`**（含 `work_date`、`publish_status` 等），发布、挂号、请假闭环均以 `scheduling` 为准，本表不参与患者端查询。

---

### 3.6 `drug_info` — 药品信息表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| drug_code | VARCHAR(32) | N | — | UK | 药品编码快照；开立时复制，防止字典改码影响历史处方。 |
| drug_name | VARCHAR(128) | N | — | IX | 药品名称；药房发药、处方开立下拉展示。 |
| drug_format | VARCHAR(255) | Y | NULL | — | 药品规格；如 0.5g×24片、100ml/瓶（对齐参考需求 `drug_format`）。 |
| drug_dosage | VARCHAR(64) | Y | NULL | — | 药剂类型/剂型；如片剂、胶囊、注射剂、口服液。 |
| drug_type | VARCHAR(64) | Y | NULL | IX | 药品类型/品种类别；如处方药、非处方药、抗生素、中成药；可用于筛选与权限。 |
| unit | VARCHAR(16) | Y | NULL | — | 计价/发药单位；如盒、支、瓶。 |
| retail_price | NUMERIC(10,2) | N | — | — | 零售价【补-22】；零售单价（元）；开立处方时快照至明细，改价不影响历史处方。 |
| manufacturer | VARCHAR(128) | Y | NULL | — | 生产厂家；药品溯源与库房管理。 |
| stock_qty | INTEGER | Y | 0 | — | 库存数量（简化，一期单库）；当前库存数量；发药扣减、退药回增（一期简化单库）。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 3.7 `disease` — 疾病字典表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| disease_code | VARCHAR(32) | N | — | UK | 疾病编码；ICD 或院内编码，用于诊断标准化。 |
| disease_name | VARCHAR(128) | N | — | IX | 疾病名称；病历诊断下拉与统计。 |
| disease_category | VARCHAR(64) | Y | NULL | — | 疾病分类；如呼吸系统、消化系统。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 3.8 `medical_technology` — 医技项目字典表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| item_code | VARCHAR(32) | N | — | UK | 医技项目编码；检查/检验/处置字典唯一标识。 |
| item_name | VARCHAR(128) | N | — | — | 医技项目名称；医嘱开立与待缴单标题展示。 |
| tech_type | VARCHAR(16) | N | — | IX | 项目类型；CHECK 检查、INSPECTION 检验、DISPOSAL 处置。 |
| price | NUMERIC(10,2) | N | — | — | 项目单价（元）；医生开立时快照至申请单。 |
| dept_id | BIGINT | Y | NULL | — | FK → department(id)；所属科室；决定数据权限与排班归属。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 3.9 `scheduling_leave_request` — 排班请假申请表

> **业务定位**：门诊医生等职员对已发布排班申请请假；管理员审批后通过 **手工替班**（`PUT /admin/scheduling/{id}` 更换 `employee_id`）完成闭环。实现见 `hospital-management` · `LeaveRequestService`；设计见 `docs/superpowers/specs/2026-06-12-admin-scheduling-leave-design.md` §3。

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；请假单业务 ID。 |
| scheduling_id | BIGINT | N | — | FK → scheduling(id) | 关联排班；一条请假对应一个排班时段。 |
| employee_id | BIGINT | N | — | FK → employee(id) | 申请人；须与 JWT 中职员 `employeeId` 一致（职员自助接口）。 |
| reason | VARCHAR(256) | N | — | — | 请假原因；职员提交时必填。 |
| status | SMALLINT | N | 0 | IX | 请假状态，见 §1.5 `leave_request_status`：**0 待审**、**1 已批准**、**2 已驳回**、**3 已撤销**、**4 已替班**。 |
| approve_admin_id | BIGINT | Y | NULL | — | 审批管理员 ID；可选，一期可不填。 |
| approve_time | TIMESTAMPTZ | Y | NULL | — | 批准或驳回时间；`status` 变为 1 或 2 时写入。 |
| reject_remark | VARCHAR(256) | Y | NULL | — | 驳回备注；`status=2` 时可选填。 |
| substitute_employee_id | BIGINT | Y | NULL | FK → employee(id) | 替班医生；`status=4` 时写入，对应排班 `scheduling.employee_id` 新值。 |
| substitute_time | TIMESTAMPTZ | Y | NULL | — | 替班完成时间；排班换人成功后写入。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 申请提交时间；管理端列表默认按此倒序。 |

**建议索引**：`IX (status, create_time DESC)`（`ix_leave_request_status`）— 管理端按状态筛选列表。

**建议部分唯一约束**：同一排班在 **待审(0)** 或 **已批准(1)** 状态下仅一条活跃请假——PostgreSQL 示例：`UNIQUE (scheduling_id) WHERE status IN (0, 1)`（`ux_leave_request_active`）。

**业务规则**：

- 职员仅可对 **已发布**（`scheduling.publish_status=1`）、未过期且无活跃请假的排班提交申请。
- 仅 **待审(0)** 可撤销（→ **3**）或审批（→ **1** / **2**）。
- 替班：不新增排班记录，仅 `UPDATE scheduling SET employee_id = ?`；若存在 **已批准(1)** 请假且 `employee_id` 变更，则将请假置 **已替班(4)** 并写入 `substitute_employee_id`、`substitute_time`。
- 不使用 `delmark`；终态记录（驳回/撤销/已替班）保留供审计与列表展示。

---

## 四、B 组 — 患者与认证

### 4.1 `patient` — 患者主表（就诊账户）

> **不设预存余额**；本表仅存身份与档案，费用通过 `bill` / `payment_record` 按单结算。

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| medical_record_no | VARCHAR(32) | N | — | UK | 病历号；患者院内终身标识，窗口挂号与查询主键。 |
| real_name | VARCHAR(64) | N | — | IX | 员工真实姓名；界面展示与处方/病历署名。 |
| gender | SMALLINT | Y | NULL | — | 性别；1 男 2 女。 |
| birth_date | DATE | Y | NULL | — | 出生日期；用于计算年龄与档案。 |
| age | SMALLINT | Y | NULL | — | 年龄（可冗余，便于列表展示）；年龄；可冗余存储便于列表展示（可由出生日期计算）。 |
| id_card | VARCHAR(18) | Y | NULL | IX | 身份证号；实名与档案核对，展示时需脱敏。 |
| phone | VARCHAR(20) | Y | NULL | UX | 联系电话；**可空**（儿童等）；**非空时全院唯一**（部分唯一索引 `ux_patient_phone`）。 |
| address | VARCHAR(256) | Y | NULL | — | 联系住址；患者档案信息。 |
| settle_category_id | BIGINT | Y | NULL | — | FK → settle_category(id)；当次挂号结算类别；计费规则快照。 |
| need_medical_book | BOOLEAN | N | FALSE | — | 是否要病历本；是否购买/使用病历本；窗口挂号时勾选。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 4.2 `patient_wechat` — 患者微信绑定表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| patient_id | BIGINT | N | — | — | FK → patient(id), UK；关联患者主表；一条微信绑定对应一名患者。 |
| openid | VARCHAR(64) | N | — | UK | 微信小程序用户唯一标识；登录鉴权与支付关联。 |
| unionid | VARCHAR(64) | Y | NULL | — | 微信开放平台 unionid；多应用统一用户时可选。 |
| session_key | VARCHAR(128) | Y | NULL | — | 加密会话（按需，注意安全存储）；微信会话密钥；服务端解密用户信息用，须安全存储、定期失效。 |
| last_login_time | TIMESTAMPTZ | Y | NULL | — | 最近一次微信登录时间；安全审计用。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 4.3 `patient_family_link` — 家属就诊人关系表（小程序扩展）

> **业务定位（ADR-016 · 方案 A）**：微信登录用户为 **操作者**（`owner_patient_id` = JWT `patientId`）；`patient_family_link` **仅存储** 操作者代管的 **非本人** 就诊人（`member_patient_id`）。本人出现在 `/family-members` 列表中但 **不写入本表**。代挂号、代缴费时通过 Query `visitPatientId` 或 Body `memberPatientId` 指定 **就诊人**；业务单（`register`/`bill` 等）挂在就诊人 `patient_id`，支付流水记在操作者。

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键。 |
| owner_patient_id | BIGINT | N | — | FK → patient(id), IX | 账号持有人（微信登录对应的 `patient.id`）。 |
| member_patient_id | BIGINT | N | — | FK → patient(id) | 就诊人（家属或本人，均指向 `patient` 主表）。 |
| relation_type | SMALLINT | N | 4 | — | 与本人关系，见下表；API 字段 `relationType`。 |
| no_id_card | BOOLEAN | N | FALSE | — | 无身份证号患儿；`true` 时 member 的 `patient.id_card` 为空。 |
| guardian_name | VARCHAR(64) | Y | NULL | — | 陪诊人/监护人姓名（无身份证患儿必填）。 |
| guardian_id_card | VARCHAR(18) | Y | NULL | — | 陪诊人身份证号（须与 `owner` 本人档案一致）。 |
| guardian_phone | VARCHAR(20) | Y | NULL | — | 陪诊人联系电话。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除；解绑置 1，重新绑定可置回 0。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 绑定创建时间。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 最后更新时间。 |

**唯一约束**：`UNIQUE (owner_patient_id, member_patient_id)` — 同一持有人下同一就诊人仅一条有效绑定。

**`relation_type` 枚举（与 `API.md` §4.1.3 一致）**

| 值 | 含义 |
|----|------|
| 0 | 本人（仅 API 展示用，**不写入 link 表**） |
| 1 | 父母 |
| 2 | 配偶 |
| 3 | 子女 |
| 4 | 其他（缺省） |

**接口**：`GET/POST /api/v1/patient/family-members`（`hospital-his` · `controller.patient`）。

---

### 4.4 `sys_user` — 系统用户表（员工/管理员登录）

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| username | VARCHAR(64) | N | — | UK | 登录用户名；员工 PC 端登录凭证。 |
| password_hash | VARCHAR(128) | N | — | — | BCrypt 等哈希，非明文；密码哈希值；BCrypt 等算法，禁止存明文密码。 |
| employee_id | BIGINT | Y | NULL | — | FK → employee(id), UK；关联员工档案；管理员账号可为空。 |
| user_type | VARCHAR(16) | N | — | — | `STAFF` / `ADMIN`；用户类型；STAFF 医护、ADMIN 系统管理员。 |
| status | SMALLINT | N | 1 | — | 账号状态，见 §1.5 `sys_user_status`：**1 启用**、**0 禁用**（与病历 `medical_record.status` 无关）。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

## 五、C 组 — 挂号与病历

### 5.1 `register` — 患者历次挂号表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| patient_id | BIGINT | N | — | — | FK → patient(id), IX；关联患者主表；一条微信绑定对应一名患者。 |
| scheduling_id | BIGINT | Y | NULL | — | FK → scheduling(id)；关联排班记录；线上/按排班挂号时使用。 |
| dept_id | BIGINT | N | — | — | FK → department(id)；**当次挂号科室**（快照）。有 `scheduling_id` 时写入应对齐 `scheduling → employee.dept_id`；窗口无排班时由操作员选择。 |
| employee_id | BIGINT | Y | NULL | — | FK → employee(id)；关联员工档案；管理员账号可为空。 |
| regist_level_id | BIGINT | N | — | — | FK → regist_level(id)；号别；决定挂号费与专家/普通队列。 |
| settle_category_id | BIGINT | Y | NULL | — | FK → settle_category(id)；当次挂号结算类别；计费规则快照。 |
| visit_date | DATE | N | — | IX | 计划就诊日期；与排班、叫号一致。 |
| noon_type | SMALLINT | N | — | — | 午别；1 上午 2 下午 3 晚上。 |
| visit_state | SMALLINT | N | 1 | IX | 看诊状态；已挂号→接诊→看诊结束/退号，见 §1.5。 |
| channel | VARCHAR(16) | N | — | — | `ONLINE` 小程序 / `WINDOW` 窗口；支付渠道；微信/现金/扫码，见 §1.5。 |
| regist_fee | NUMERIC(10,2) | N | — | — | 应收挂号费（快照）；挂号费单价（元）；开立挂号待缴单时快照引用（普通 20 / 专家 65）。 |
| registrar_id | BIGINT | Y | NULL | — | FK → employee(id)；窗口挂号员；线下挂号时记录经办人。 |
| call_time | TIMESTAMPTZ | Y | NULL | — | 叫号时间；门诊医生接诊起点。 |
| visit_end_time | TIMESTAMPTZ | Y | NULL | — | 看诊结束时间；开立处方或处置后写入。 |
| remark | VARCHAR(256) | Y | NULL | — | 挂号备注；特殊说明或窗口录入信息。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

**业务说明**：状态进入 **已挂号(1)** 前，须存在对应 `bill`（`biz_type=REGISTER`）且已支付成功。按排班挂号时 `employee_id`、`regist_level_id`、`visit_date`、`noon_type` 应与所选 `scheduling` 一致。

---

### 5.2 `medical_record` — 患者病历表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| register_id | BIGINT | N | — | — | FK → register(id), UK；关联当次挂号；医嘱归属本次就诊（等同老师 OrderID→RegID 关系）。 |
| patient_id | BIGINT | N | — | — | FK → patient(id), IX；关联患者主表；一条微信绑定对应一名患者。 |
| doctor_id | BIGINT | N | — | — | FK → employee(id)；开立医生；门诊医生确认提交后写入，用于审计。 |
| readme | TEXT | Y | NULL | — | 主诉；患者本次就诊最主要的不适描述。 |
| present | TEXT | Y | NULL | — | 现病史；当前疾病发生、发展过程。 |
| present_treat | TEXT | Y | NULL | — | 现病治疗情况；就诊前已接受治疗说明。 |
| history | TEXT | Y | NULL | — | 既往史；以往重大疾病、手术史等。 |
| allergy | TEXT | Y | NULL | — | 过敏史；药物/食物过敏，影响处方安全。 |
| physique | TEXT | Y | NULL | — | 体格检查；体征与查体记录。 |
| diagnosis | TEXT | Y | NULL | — | 初步/确诊诊断（文本）；诊断；初步或确诊结论（文本，可配合疾病字典）。 |
| cure | TEXT | Y | NULL | — | 处理意见；治疗原则与随访建议。 |
| check_advice | TEXT | Y | NULL | — | 检查建议；拟开检查项目的文字说明（可与申请单并存）。 |
| inspection_advice | TEXT | Y | NULL | — | 检验建议；拟开检验项目的文字说明。 |
| status | SMALLINT | N | 0 | — | 病历状态，见 §1.5 `medical_record_status`：**0 书写中**、**1 已保存**、**2 已确诊提交**；**患者小程序仅可读 `status=2`**。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 5.3 `medical_record_disease` — 病历-疾病关联表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| medical_record_id | BIGINT | N | — | — | FK → medical_record(id), IX；关联病历；一条记录表示病历上一个诊断条目。 |
| disease_id | BIGINT | N | — | — | FK → disease(id)；关联疾病字典；标准化诊断编码与名称。 |
| disease_type | SMALLINT | Y | 1 | — | 诊断类型；1 主要诊断 2 次要诊断。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 关联记录创建时间。 |

**建议唯一**：`UK (medical_record_id, disease_id)`。

---

## 六、D 组 — 医技医嘱（检查 / 检验 / 处置）

三张表结构对称，以下以 **`check_request`** 为模板；`inspection_request`、`disposal_request` 字段相同，仅表名与 `tech_type` 业务含义不同。

### 6.1 `check_request` — 检查申请表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| register_id | BIGINT | N | — | — | FK → register(id), IX；关联当次挂号；医嘱归属本次就诊（等同老师 OrderID→RegID 关系）。 |
| patient_id | BIGINT | N | — | — | FK → patient(id), IX；关联患者主表；一条微信绑定对应一名患者。 |
| medical_technology_id | BIGINT | N | — | — | FK → medical_technology(id)；医技项目；关联字典中的检查/检验/处置项目及单价。 |
| doctor_id | BIGINT | N | — | — | FK → employee(id)；开立医生；门诊医生确认提交后写入，用于审计。 |
| item_price | NUMERIC(10,2) | N | — | — | 开立时单价快照；开立时项目单价快照（元）；防止字典调价影响已开单据。 |
| purpose | VARCHAR(256) | Y | NULL | — | 检查目的；检查/检验/处置目的；指导医技科室执行。 |
| body_part | VARCHAR(64) | Y | NULL | — | 检查部位；如头部、胸部（检查类常用）。 |
| remark | VARCHAR(256) | Y | NULL | — | 医嘱备注；补充说明。 |
| status | SMALLINT | N | 10 | IX | 医嘱执行状态；10 已开立→20 已缴费→30 执行完成→40 已出结果，见 §1.5。 |
| order_time | TIMESTAMPTZ | N | NOW() | — | 医嘱开立时间；医生确认提交后 status=已开立。 |
| executor_id | BIGINT | Y | NULL | — | FK → employee(id)；执行人；医技科室执行检查/检验/处置的医护。 |
| execute_time | TIMESTAMPTZ | Y | NULL | — | 执行完成时间；标本采集或检查完成时刻。 |
| result_input_id | BIGINT | Y | NULL | — | FK → employee(id)；结果录入人；对结果负责的医护。 |
| result_time | TIMESTAMPTZ | Y | NULL | — | 结果录入时间；结果回传门诊医生前写入。 |
| result_text | TEXT | Y | NULL | — | 文字结果；报告结论、检验数值等（影像大图走 MinIO）。 |
| result_attachment | VARCHAR(512) | Y | NULL | — | 附件路径（MinIO key 等）；结果附件路径；MinIO 对象 key 或报告 PDF 链接。 |
| from_ai | BOOLEAN | N | FALSE | — | 是否经 AI 辅助开立；true 表示源自 AI 草稿且经医生确认。 |
| confirm_time | TIMESTAMPTZ | Y | NULL | — | 医生确认提交时间；AI 辅助开立时与 order_time 一致或略早。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

### 6.2 `inspection_request` — 检验申请表

字段与 **§6.1** 相同。

### 6.3 `disposal_request` — 处置申请表

字段与 **§6.1** 相同。

---

## 七、E 组 — 处方

> **业务定位**：记录药品处方的执行信息，连接门诊医嘱（开立）与药房发药、按单收费；头表 `prescription` 以 **`id`** 作为处方业务标识（对应老师 PPT **PrescID**），明细表 `prescription_item` 对应 **DrugCode、用法用量、数量** 等。

### 7.1 `prescription` — 处方主表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；**兼作处方业务 ID**（对应 PrescID）；药房、待缴单 `bill.biz_id`、发药查询均使用本字段。 |
| register_id | BIGINT | N | — | — | 关联当次挂号；药品类医嘱归属本次就诊（等同老师 OrderID→RegID）。 |
| patient_id | BIGINT | N | — | — | 患者 ID；冗余便于按患者查处方，须与 register.patient_id 一致。 |
| doctor_id | BIGINT | N | — | — | FK → employee(id)；开立医生；门诊医生确认提交后写入，用于审计。 |
| total_amount | NUMERIC(10,2) | N | 0 | — | 处方合计金额（元）；明细行 amount 汇总，生成待缴单依据。 |
| status | SMALLINT | N | 10 | IX | 处方流转状态；10 已开立 15 药师驳回 20 已缴费 30 已发药 40 已退药 50 已退费，见 §1.5。 |
| pharmacist_id | BIGINT | Y | NULL | — | FK → employee(id)；发药药师；发药核对通过时写入。 |
| reject_reason | VARCHAR(256) | Y | NULL | — | 药师驳回原因；`status=15` 时由药房填写。 |
| reject_pharmacist_id | BIGINT | Y | NULL | — | FK → employee(id)；执行驳回的药师。 |
| reject_time | TIMESTAMPTZ | Y | NULL | — | 药师驳回时间。 |
| ai_draft_id | BIGINT | Y | NULL | — | FK → ai_prescription_draft(id)；来源 AI 草稿 ID；追溯 AI 辅助开立链路（补-25）。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 处方创建时间（医生确认提交 INSERT 时写入）；待缴列表按开立时间排序可用本字段。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 最后更新时间；发药等状态变更时刷新。 |

> **时间约定**：不设单独的 `order_time` / `dispense_time`；**开立**以 `create_time`（提交 INSERT）为准，**发药**以 `status=30` + `pharmacist_id` + `update_time` 为准。

---

### 7.2 `prescription_item` — 处方明细表

> 一张处方包含多条药品明细；对应老师处方表中的药品编码、用法、用量、频次与发放数量。

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| prescription_id | BIGINT | N | — | — | FK → prescription(id), IX；所属处方主表；一张处方可含多条药品明细。 |
| drug_id | BIGINT | N | — | FK → drug_info(id) | 药品字典 ID；关联 drug_info 维护价目与库存。 |
| drug_code | VARCHAR(32) | N | — | — | 药品编码快照；开立时从 drug_info 复制，防止字典改码影响历史处方。 |
| drug_name | VARCHAR(128) | N | — | — | 药品名称快照；药房发药时核对药品实体。 |
| drug_format | VARCHAR(255) | Y | NULL | — | 规格快照；开立时从 `drug_info.drug_format` 复制。 |
| drug_dosage | VARCHAR(64) | Y | NULL | — | 剂型快照；开立时从 `drug_info.drug_dosage` 复制。 |
| drug_type | VARCHAR(64) | Y | NULL | — | 药品类型快照；开立时从 `drug_info.drug_type` 复制。 |
| unit_price | NUMERIC(10,2) | N | — | — | 单价快照（元）；开立时从 drug_info.retail_price 复制。 |
| quantity | NUMERIC(10,2) | N | — | — | 发药数量；本次处方该药品的总发放量。 |
| amount | NUMERIC(10,2) | N | — | — | 行金额（元）；unit_price × quantity，汇总入 prescription.total_amount。 |
| usage_method | VARCHAR(64) | Y | NULL | — | 用法；如口服、静脉滴注，指导患者用药。 |
| dosage | VARCHAR(64) | Y | NULL | — | 单次用量；如每次 2 片。 |
| frequency | VARCHAR(64) | Y | NULL | — | 用药频次；如每日 3 次。 |
| days | INTEGER | Y | NULL | — | 用药天数；疗程长度。 |
| entrust | VARCHAR(256) | Y | NULL | — | 医嘱嘱托；如饭后服用、忌酒等。 |
| sort_no | INTEGER | Y | 0 | — | 明细行序号；处方内药品显示顺序。 |

> 明细行不设 `create_time`；开立时间以 **`prescription.create_time`** 为准。

---

### 7.3 `ai_prescription_draft` — AI 处方草稿表

> 对应【补-25】：医生修改并 **确认提交** 后写入 `prescription`，草稿不再生效。

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| register_id | BIGINT | N | — | — | 关联当次挂号；药品类医嘱归属本次就诊（等同老师 OrderID→RegID）。 |
| doctor_id | BIGINT | N | — | — | FK → employee(id)；开立医生；门诊医生确认提交后写入，用于审计。 |
| draft_content | JSONB | N | — | — | AI 建议药品列表（JSON）；AI 生成的处方建议 JSON；未经医生确认前不可计费、不可发药。 |
| doctor_edited_content | JSONB | Y | NULL | — | 医生修改后内容；医生修改后的草稿 JSON；确认提交后写入正式处方。 |
| status | SMALLINT | N | 0 | — | AI 草稿状态，见 §1.5 `ai_prescription_draft_status`：**0 草稿**、**1 已提交**、**9 已废弃**。 |
| submit_time | TIMESTAMPTZ | Y | NULL | — | 提交时间；提交 AI 分析时间。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

## 八、F 组 — 收费支付（按单付 · 无余额）

> **业务单号约定**：`bill`、`payment_record`、`refund_record` **不设** `bill_no` / `payment_no` / `refund_no`；关联与展示统一使用各表 **`id`**。微信等渠道的商户侧订单号由实现约定（如 `out_trade_no` 使用 `payment_record.id`）；渠道对账仍用 `third_party_trade_no` / `third_party_refund_no`。

### 8.1 `bill` — 待缴费用单

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；**兼作待缴单业务 ID**；`payment_bill.bill_id`、`bill.biz_id` 关联均使用本字段。 |
| patient_id | BIGINT | N | — | — | FK → patient(id), IX；关联患者主表；一条微信绑定对应一名患者。 |
| register_id | BIGINT | Y | NULL | — | FK → register(id), IX；关联当次挂号；医嘱归属本次就诊（等同老师 OrderID→RegID 关系）。 |
| biz_type | VARCHAR(16) | N | — | IX | 业务类型；区分挂号费/检查/检验/处置/处方，见 §1.5。 |
| biz_id | BIGINT | N | — | IX | 业务主键；指向 register 或各医嘱/处方表的 id。 |
| bill_title | VARCHAR(128) | N | — | — | 展示标题，如「头部 CT」；待缴单标题；小程序与窗口展示，如「头部 CT」「处方药品费」。 |
| amount | NUMERIC(10,2) | N | — | — | 应收金额；分摊金额（元）；该待缴单在本笔支付中结算的金额。 |
| status | SMALLINT | N | 0 | IX | 待缴单状态，见 §1.5 `bill_status`：**0 待支付**、**1 已支付**、**2 已退款**、**9 已作废**。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |
| paid_time | TIMESTAMPTZ | Y | NULL | — | 支付完成时间；bill.status 变为已支付时写入。 |

**建议唯一**：`UK (biz_type, biz_id)`（同一业务单仅一张有效待缴单）。

**联动规则**：

- 支付成功 → 更新 `bill.status=1`，并驱动业务单 `status` → 已缴费（或挂号 `visit_state`）。
- 退款成功 → `bill.status=2`，业务单 → 已退费。

---

### 8.2 `payment_record` — 支付流水表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；**兼作支付流水业务 ID**；`payment_bill.payment_id`、`refund_record.payment_id` 均使用本字段。 |
| patient_id | BIGINT | N | — | FK → patient(id), IX | 付款患者；与待缴单 patient_id 一致。 |
| total_amount | NUMERIC(10,2) | N | — | — | 实付总金额（元）；一次支付可合并结算多张待缴单。 |
| channel | VARCHAR(16) | N | — | — | 支付渠道；WECHAT/CASH/SCAN，见 §1.5 `payment_channel`。 |
| status | SMALLINT | N | 0 | IX | 支付单状态；0 待支付 1 成功 2 失败 3 已关闭，见 §1.5。 |
| third_party_trade_no | VARCHAR(64) | Y | NULL | IX | 第三方支付流水号；如微信 transaction_id，对账用（医护端不可见）。 |
| operator_id | BIGINT | Y | NULL | — | FK → employee(id)；经办收费员；窗口收费时记录。 |
| pay_time | TIMESTAMPTZ | Y | NULL | — | 支付成功时间；驱动业务单进入已缴费状态。 |
| remark | VARCHAR(256) | Y | NULL | — | 支付备注；窗口收费说明等。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 8.3 `payment_bill` — 支付-待缴单关联表

> 支持 **合并支付**（补-15）：一次 `payment_record` 结算多张 `bill`。

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| payment_id | BIGINT | N | — | — | FK → payment_record(id), IX；原支付单；退款必须关联已成功的 payment_record。 |
| bill_id | BIGINT | N | — | — | FK → bill(id), IX；可选关联待缴单；指明退的是哪一笔费用。 |
| amount | NUMERIC(10,2) | N | — | — | 该单分摊金额；分摊金额（元）；该待缴单在本笔支付中结算的金额。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 关联记录创建时间。 |

**建议唯一**：`UK (payment_id, bill_id)`。

---

### 8.4 `refund_record` — 退款流水表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；**兼作退款流水业务 ID**。 |
| payment_id | BIGINT | N | — | — | FK → payment_record(id), IX；原支付单；退款必须关联已成功的 payment_record。 |
| bill_id | BIGINT | Y | NULL | — | FK → bill(id)；可选关联待缴单；指明退的是哪一笔费用。 |
| patient_id | BIGINT | N | — | — | FK → patient(id)；关联患者主表；一条微信绑定对应一名患者。 |
| refund_amount | NUMERIC(10,2) | N | — | — | 退款金额（元）；一般不大于原支付金额。 |
| channel | VARCHAR(16) | N | — | — | 原路渠道；支付渠道；微信/现金/扫码，见 §1.5。 |
| status | SMALLINT | N | 0 | — | 退款状态，见 §1.5 `refund_status`：**0 处理中**、**1 退款成功**、**2 退款失败**。 |
| third_party_refund_no | VARCHAR(64) | Y | NULL | — | 微信退款单号；第三方退款单号；如微信退款 id。 |
| operator_id | BIGINT | Y | NULL | — | FK → employee(id)；经办收费员；窗口收费时记录。 |
| refund_time | TIMESTAMPTZ | Y | NULL | — | 退款成功时间；业务单进入已退费状态。 |
| reason | VARCHAR(256) | Y | NULL | — | 退号/退费等；退款原因；如退号、检查未做、退药等。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 8.5 费用/支付流水 — 可见范围与接口约束

> **已定稿（2026-05）**，与 `PROJECT_REQUIREMENTS.md` §2.3.1、`BUSINESS_FLOW.md`「费用记录查询」一致。  
> 流水表：`bill`、`payment_record`、`payment_bill`、`refund_record`。

#### 8.5.1 角色可见矩阵

| 角色 | `bill` | `payment_record` | `refund_record` | 说明 |
|------|--------|------------------|-----------------|------|
| 患者 | 本人 R | 本人 R（摘要） | 本人 R | 小程序：待缴、已付、退款；含金额、项目名、时间 |
| 挂号收费员 | 单患者 R/W | 经手 R/W | 经手 R/W | 窗口收费、退费、患者费用查询；可查当班经手记录 |
| 门诊医生 | — | — | — | **不看流水表**；仅通过业务单 `status` 知悉是否已缴费 |
| 检查/检验/处置医生 | — | — | — | 同上，仅本科室申请单状态 |
| 药师 | — | — | — | 仅处方 `status` ≥ 已缴费 方可发药 |
| 管理员 | 汇总 R | 汇总 R | 汇总 R | 统计维度：日/科室/渠道；一期可选 |

图例：R 只读，W 写入（收费/退费产生流水）。

#### 8.5.2 患者端展示字段（建议）

| 展示项 | 数据来源 |
|--------|----------|
| 待缴列表 | `bill` where `patient_id` = 当前用户 and `status` = 0 |
| 单号/流水号 | 各表 **`id`**（无 `bill_no` / `payment_no` / `refund_no`；界面可格式化为「待缴 #123」等） |
| 缴费记录 | `payment_record` + `payment_bill` → `bill` 标题 |
| 退款记录 | `refund_record` |
| 不展示 | `operator_id` 姓名以外的收费员内部备注、他人数据 |

#### 8.5.3 收费员端展示字段（建议）

| 功能 | 数据范围 |
|------|----------|
| 患者收费查询 | 输入病历号 → 该患者全部 `bill`（本次就诊可筛 `register_id`）；列表主键为 **`bill.id`** |
| 收费结算 | 勾选 `bill` → 生成 `payment_record`（**`id`** 兼作支付流水号）+ `payment_bill` |
| 退费 | 关联原 **`payment_id`** / **`bill_id`**（均为各表 `id`）→ `refund_record` |
| 费用记录查询（泳道图） | 与上相同，缴费成功后供患者/收费员查询 |

#### 8.5.4 医护端（仅状态，不看流水）

| 业务表 | 医生/医技/药师可见 |
|--------|-------------------|
| `check_request` / `inspection_request` / `disposal_request` | `status`（是否已缴费、是否可执行） |
| `prescription` | `status`（已缴费后才可发药） |
| 不可通过通用接口返回 | `payment_record.third_party_trade_no`、`payment_record.channel` 等 |

#### 8.5.5 安全与实现

| 约束 | 说明 |
|------|------|
| 患者隔离 | 所有患者 API 的 `patient_id` 取自 JWT/会话，禁止路径参数越权 |
| 收费员角色 | `employee.role_type = REGISTRAR` 或权限码 `registration:charge` |
| 管理员 | 仅聚合 SQL / 报表接口，避免 `SELECT * FROM payment_record` 无分页暴露 |
| 审计 | 收费、退费写操作记录 `operator_id`、`create_time`（表字段已含） |

#### 8.5.6 分期

| 阶段 | 范围 |
|------|------|
| P1～P2 | 患者本人查询 + 收费员单患者收费/退费 + 医护仅状态 |
| P3+ | 管理员日结/科室汇总、发票（若做） |

### 8.6 诊疗数据 — 存储归属与可见范围

> **已定稿（2026-05）**，与 `PROJECT_REQUIREMENTS.md` §2.3.2 一致。  
> 本节管病历/医嘱/医技结果/处方/影像；**费用流水**仍见 §8.5。

#### 8.6.1 权威存储（非小程序本地）

| 数据 | 主存储 | 关联表（示例） |
|------|--------|----------------|
| 门诊病历 | PostgreSQL | `medical_record`, `medical_record_disease` |
| 检查/检验/处置 | PostgreSQL | `check_request`, `inspection_request`, `disposal_request` |
| 处方 | PostgreSQL | `prescription`, `prescription_item` |
| 影像文件 | MinIO | `imaging_study.source_object_key` 等 |
| 患者身份 | PostgreSQL | `patient`, `patient_wechat` |

患者微信小程序 **不** 作为上述数据的唯一持久化副本；仅缓存 token，展示数据经 API 从服务端读取。

#### 8.6.2 角色可见矩阵（诊疗）

| 角色 | `medical_record` | `*_request` 结果 | `prescription` | `imaging_study` / MinIO |
|------|------------------|------------------|----------------|-------------------------|
| 患者 | 本人 R（**仅 `status=2` 已确诊提交**） | 本人 R（已出结果） | 本人 R（摘要） | 本人 R（授权检查） |
| 门诊医生 | 接诊患者 R/W | 相关申请 R/W、结果 R | 开立 R/W | 相关 R |
| 检查医生 | — | 本科室检查单 R/W | — | 本科室 R/W |
| 检验医生 | — | 本科室检验单 R/W | — | — |
| 处置医生 | — | 本科室处置单 R/W | — | — |
| 药师 | — | — | 待发药 R/W | — |
| 挂号收费员 | 一般不看全文 | — | — | — |
| 管理员 | 统计/元数据（一期可选） | 统计（一期可选） | 统计（一期可选） | 配置/任务监控 |

图例：R 只读，W 写入。实现时通过 `patient_id`、`register_id`、科室与 `role_type` 组合过滤。

#### 8.6.3 安全与实现（与 §8.5.5 并列）

| 约束 | 说明 |
|------|------|
| 患者隔离 | 患者 API 的 `patient_id` 取自 JWT/会话，禁止越权 `register_id`；**病历查询须 `medical_record.status = 2`** |
| 医护范围 | 医技仅处理 **已缴费** 且归属本科室的申请单；门诊医生写病历须绑定 `register_id` |
| 逻辑删除 | `delmark=1` 后患者端默认不可见，管理端是否可查由实现约定 |
| 审计 | 病历保存、结果录入、发药等写操作记录操作人 ID 与时间 |
| 传输 | 对外接口 HTTPS；身份证等可在展示层脱敏 |

#### 8.6.4 分期

| 阶段 | 范围 |
|------|------|
| P1～P2 | 病历 CRUD、患者本人病历查看、医技结果录入与回传 |
| P3+ | 影像 MinIO + CNN；更细脱敏与操作审计（若做） |

---

## 九、G 组 — 影像与 AI 会话

### 9.1 `imaging_study` — 医学影像检查任务表

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| study_no | VARCHAR(32) | N | — | UK | 影像任务流水号；CNN 分析与报告查询用。 |
| check_request_id | BIGINT | Y | NULL | — | FK → check_request(id)；关联检查申请；可选，衔接门诊开立的检查单。 |
| register_id | BIGINT | N | — | — | FK → register(id), IX；关联当次挂号；医嘱归属本次就诊（等同老师 OrderID→RegID 关系）。 |
| patient_id | BIGINT | N | — | — | FK → patient(id)；关联患者主表；一条微信绑定对应一名患者。 |
| modality | VARCHAR(16) | Y | NULL | — | 如 `CT_HEAD`、`CT_LUNG`；影像类型/算法；如 CT_HEAD、CT_LUNG。 |
| status | VARCHAR(16) | N | 'PENDING' | IX | 影像任务状态，见 §1.5 `imaging_study_status`：`PENDING` / `PROCESSING` / `COMPLETED` / `FAILED`。 |
| source_bucket | VARCHAR(64) | Y | NULL | — | MinIO bucket；MinIO 原图存储桶名。 |
| source_object_key | VARCHAR(512) | Y | NULL | — | 原图路径；MinIO 原图对象路径；患者或设备上传的 DICOM/图片。 |
| result_bucket | VARCHAR(64) | Y | NULL | — | 结果图 bucket；MinIO 结果图存储桶名。 |
| result_object_key | VARCHAR(512) | Y | NULL | — | 分割/标注结果图；MinIO 结果图路径；分割标注等 AI 输出。 |
| report_json | JSONB | Y | NULL | — | CNN + 报告结构化结果；结构化报告 JSON；CNN 结论与指标，供医生 PC 展示。 |
| error_message | VARCHAR(512) | Y | NULL | — | 分析失败原因；便于重试与运维。 |
| submit_time | TIMESTAMPTZ | Y | NULL | — | 提交分析时间；提交 AI 分析时间。 |
| complete_time | TIMESTAMPTZ | Y | NULL | — | 分析完成时间；status=COMPLETED 时写入。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

---

### 9.2 `ai_chat_session` — AI 对话会话表（可选 · P4）

| 字段名 | 数据类型 | 空 | 默认值 | 键 | 业务说明 |
|--------|----------|----|--------|-----|------|
| id | BIGSERIAL | N | — | PK | 主键；系统自动生成的唯一标识，作为本条记录的身份 ID。 |
| session_no | VARCHAR(32) | N | — | UK | AI 会话编号；问诊/助理会话的业务标识。 |
| scene | VARCHAR(32) | N | — | IX | `TRIAGE` 问诊 / `ASSISTANT` 医生助理 / `DIAGNOSIS` 辅助诊断 |
| patient_id | BIGINT | Y | NULL | — | FK → patient(id)；关联患者主表；一条微信绑定对应一名患者。 |
| register_id | BIGINT | Y | NULL | — | FK → register(id)；关联当次挂号；医嘱归属本次就诊（等同老师 OrderID→RegID 关系）。 |
| doctor_id | BIGINT | Y | NULL | — | FK → employee(id)；开立医生；门诊医生确认提交后写入，用于审计。 |
| messages | JSONB | Y | NULL | — | 消息列表或仅存摘要；对话消息 JSON；存摘要或全量，依 Spring AI 方案二选一。 |
| delmark | SMALLINT | N | 0 | — | 逻辑删除标记；0 表示有效，1 表示已删除（业务列表默认不展示已删记录）。 |
| create_time | TIMESTAMPTZ | N | NOW() | — | 记录创建时间；用于审计追溯、列表排序。 |
| update_time | TIMESTAMPTZ | N | NOW() | — | 记录最后更新时间；业务数据变更时由系统刷新。 |

> 若 Spring AI 自带会话存储，本表可作为业务侧索引；实现阶段二选一。

---

## 十、H 组 — 向量 / RAG（说明）

| 项 | 说明 |
|----|------|
| 扩展 | `CREATE EXTENSION vector;` |
| 表结构 | 由 **Spring AI `PgVectorStore`** 按版本自动建表或维护于 `docs/sql/vector.sql`（**后续脚本阶段再写**） |
| 用途 | 疾病知识、诊疗指南等 RAG 文档切片与 embedding |
| 本文档 | 不展开向量表列定义，避免与框架版本漂移 |

---

## 十一、与参考需求 15 表对照

| 原清单表名 | 本文档表名 | 变更说明 |
|------------|------------|----------|
| employee | employee | 一致；登录拆至 `sys_user` |
| department | department | 一致 |
| regist_level | regist_level | 一致 |
| settle_category | settle_category | 一致 |
| scheduling | scheduling | 无 `dept_id`（出诊科室经 `employee.dept_id`）；无 `delmark`，作废用 `publish_status=2` |
| — | **scheduling_leave_request** | 新增（排班请假闭环；不修改 `scheduling` 结构，替班改 `employee_id`） |
| register | register | 增补 `channel`、支付关联；`dept_id` 为当次挂号科室快照 |
| check_request | check_request | `status` 与老师【态】对齐 |
| inspection_request | inspection_request | 同上 |
| disposal_request | disposal_request | 同上 |
| medical_technology | medical_technology | 增补 `tech_type` |
| medical_record | medical_record | 增补 `status` 三态（§1.5）；**患者端仅 `status=2` 可见** |
| medical_record_disease | medical_record_disease | 一致 |
| disease | disease | 一致 |
| prescription | **prescription + prescription_item** | 拆分为头/明细；无 `prescription_no` / `order_time` / `dispense_time` / 头表 `remark`；业务 ID 为 **`prescription.id`**；明细无 `create_time` |
| drug_info | drug_info | 对齐参考：`drug_format`、`drug_dosage`、`drug_type`；增补 `manufacturer`、`stock_qty` 等 |
| — | **patient** | 新增（原清单缺失） |
| — | **patient_wechat, sys_user** | 新增（认证） |
| — | **bill, payment_record, payment_bill, refund_record** | 新增（按单付）；无 `bill_no` / `payment_no` / `refund_no`，业务 ID 为各表 **`id`** |
| — | **ai_prescription_draft, imaging_study, ai_chat_session** | 新增（AI/影像） |

---

## 十二、实施阶段建议（供排期，非建表）

| 阶段 | 建议优先落地的表 |
|------|------------------|
| P1 | patient, patient_wechat, department, regist_level, employee, sys_user, scheduling, scheduling_leave_request, register, bill, payment_record, payment_bill, medical_record |
| P2 | medical_technology, check_request, inspection_request, disposal_request, refund_record |
| P3 | drug_info, prescription, prescription_item, ai_prescription_draft |
| P4 | imaging_study, ai_chat_session + 向量扩展 |

---

## 十三、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05 | 首版：26 张业务表；按单支付；状态与老师【态】对齐；不含 DDL |
| v1.1 | 2026-05 | 新增 §8.5 费用/支付流水可见范围与接口约束 |
| v1.2 | 2026-05 | 新增 §8.6；全表字段 **业务说明** 列；`prescription_item` 增补 `drug_code`；修正向量 §十 引用与表名笔误 |
| v1.3 | 2026-05 | 新增 §1.4 微服务与表写归属矩阵；**同步 `docs/sql/schema.sql`** |
| v1.4 | 2026-06 | `scheduling` 移除 `dept_id`（出诊科室经 `employee.dept_id` 推导；无跨科会诊、不考虑员工调科历史）；§5.1 `register.dept_id` 业务说明同步 |
| v1.5 | 2026-06 | `scheduling` 移除 `delmark`；`publish_status` 增 **2=已取消**（§1.5）；部分唯一索引与患者端查询规则 |
| v1.6 | 2026-06 | `drug_info`：`specification` 改回 **`drug_format`**，增补 **`drug_dosage`**、**`drug_type`**；`prescription_item` 同步快照字段 |
| v1.7 | 2026-06 | 患者病历可见 **定稿：`medical_record.status = 2` 仅此**；§1.5 增 `medical_record_status`；§8.6 同步 |
| v1.8 | 2026-06 | `prescription` 移除 **`prescription_no`**；处方业务标识统一为 **`id`** |
| v1.9 | 2026-06 | `prescription` 移除 **`order_time`**、**`dispense_time`**；开立/发药时间见 `create_time` / `update_time` 约定 |
| v1.10 | 2026-06 | `prescription` 移除 **`remark`**（整单备注；药品级说明见 `prescription_item.entrust`） |
| v1.11 | 2026-06 | 修正 **`sys_user` / `bill` / `ai_prescription_draft` / `refund_record` / `imaging_study`** 的 `status` 业务说明误贴；§1.5 增补对应枚举 |
| v1.12 | 2026-06 | `prescription_item` 移除 **`create_time`**；开立时间以 `prescription.create_time` 为准 |
| v1.13 | 2026-06 | 移除 **`bill_no`**、**`payment_no`**、**`refund_no`**；费用/支付/退款业务标识统一为各表 **`id`**（§八 约定） |
| v1.14 | 2026-06 | **表结构定稿不再改动**；§1.1 业务标识、§2 ER 脚注、§8.5 展示字段、§11 对照与 v1.4～v1.13 对齐；**`docs/sql/schema.sql` 已重写对齐**；§4.3 补全 **`patient_family_link`** 字段说明 |
| v1.15 | 2026-06 | 新增 **`scheduling_leave_request`**（§3.9、§1.5 `leave_request_status`）；§3.5 请假关联说明；§1.3 A′ 排班扩展、§1.4 写归属、§2 ER；**`docs/sql/schema.sql`** 与 **`patch-scheduling-leave.sql`** 已对齐 |

---

*本文档 **v1.15** 为业务表设计权威说明（非建表脚本）。建表脚本见 **`docs/sql/schema.sql`**（含 `scheduling_leave_request`、`patient_family_link`）；旧库升级见 **`docs/sql/patch-scheduling-leave.sql`**。*
