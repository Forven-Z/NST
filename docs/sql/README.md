# 数据库脚本说明

> **业务库**：PostgreSQL 15+，库名 `hospital`  
> **设计依据**：[`DATABASE_DESIGN.md`](../DATABASE_DESIGN.md) **v1.14**  
> **执行顺序**：`schema.sql` → `seed-dict.sql`（P1 联调必跑 seed）  
> **说明**：`patient_family_link` 已并入 `schema.sql`（小程序家属）；`patch-family-link.sql` 仅用于旧库增量升级。

---

## 一、首次建库（Windows 示例）

> **数据库超级用户**：`postgres` / **`123456`**（与 `DEV_ENV_SETUP.md` §6.1、`PG_PASSWORD` 一致）。  
> **`psql` 找不到**：先将 `PostgreSQL\16\bin` 加入 Path，见 `DEV_ENV_SETUP.md` §6.1.2。  
> **口令只输一次**：在 `用户 postgres 的口令：` 处输入；进入 `postgres=#` 后输入的是 SQL，不要再输密码（见 §6.1.3）。

**前置**：已创建库 `hospital`（若未建库，先执行 §6.1.4 或下面第 0 步）。

```cmd
cd /d C:\Users\你的用户名\Desktop\NST

REM 0. 创建库（已存在可跳过）
psql -U postgres -h localhost -c "CREATE DATABASE hospital ENCODING 'UTF8';"

REM 1. 建表（26 张核心表 + patient_family_link）
psql -U postgres -d hospital -f docs\sql\schema.sql

REM 2. 灌入 P1 字典与测试账号（ADR-012）
psql -U postgres -d hospital -f docs\sql\seed-dict.sql

REM 3. 可选：演示患者 + 今日挂号（小程序/窗口联调）
psql -U postgres -d hospital -f docs\sql\seed-demo-patients.sql
```

每条命令提示口令时填 **`123456`**。业务测试账号（如 `doctor01`）密码也是 `123456`，见 §三。

**Windows 编码（`seed-dict.sql` 含中文，必看）**

`schema.sql` 多为英文，一般可直接跑；`seed-dict.sql` 在中文 Windows 上可能报错：

```text
错误: 编码"GBK"的字符 ... 在编码"UTF8"没有相对应值
```

原因：数据库是 **UTF8**，而 CMD/PowerShell 下 `psql` 默认按 **GBK** 读脚本。

**处理顺序**（任选其一，推荐 1）：

1. 脚本已内置 `\encoding UTF8`（文件开头），**重新执行**即可：

```cmd
psql -U postgres -d hospital -f docs\sql\seed-dict.sql
```

2. PowerShell 先设客户端编码再跑：

```powershell
$env:PGCLIENTENCODING = "UTF8"
psql -U postgres -d hospital -f docs\sql\seed-dict.sql
```

3. CMD 先切 UTF-8 代码页再跑：

```cmd
chcp 65001
psql -U postgres -d hospital -f docs\sql\seed-dict.sql
```

若曾失败过，数据可能未写入（事务已 `ROLLBACK`），修复编码后**再执行一次** `seed-dict.sql` 即可。

### 导入后验收 SQL

> 登录账号在 **`sys_user`**，角色在 **`employee.role_type`**。项目中**没有** `staff_account` 表；后端 `StaffAuthRepository` 联查 `sys_user` + `employee`。

**本机**：

```cmd
psql -U postgres -d hospital -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
psql -U postgres -d hospital -c "SELECT id, username, user_type FROM sys_user ORDER BY id;"
psql -U postgres -d hospital -c "SELECT u.username, e.real_name, e.role_type FROM sys_user u JOIN employee e ON e.id = u.employee_id ORDER BY u.id;"
```

**ECS Docker**（在 `/opt/hospital` 下）：

```bash
docker exec -it hospital-postgres psql -U postgres -d hospital -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
docker exec -it hospital-postgres psql -U postgres -d hospital -c "SELECT id, username, user_type FROM sys_user ORDER BY id;"
docker exec -it hospital-postgres psql -U postgres -d hospital -c "SELECT u.username, e.real_name, e.role_type FROM sys_user u JOIN employee e ON e.id = u.employee_id ORDER BY u.id;"
```

期望：表数量约 **26～28**；`sys_user` 含 `doctor01`、`lab01`、`check01`、`admin` 等。

**v1.14 相对旧库的主要 DDL 变更**（须 **DROP SCHEMA 重建** 或自行写 migration，勿直接覆盖旧表）：

- 移除 `bill_no` / `payment_no` / `refund_no` / `prescription_no`
- `scheduling` 移除 `dept_id`、`delmark`；增加部分唯一索引 `ux_scheduling_active_slot`
- `prescription` 移除 `order_time` / `dispense_time` / `remark`
- `drug_info` / `prescription_item`：`specification` → `drug_format`，增补 `drug_dosage`、`drug_type`
- `prescription_item` 移除 `create_time`

**pgvector（P4 再装）**：完整分步见 [`DEV_ENV_SETUP.md`](../DEV_ENV_SETUP.md) **§6.1.6**（下载 zip → 停服务 → 复制到 PG 目录 → `CREATE EXTENSION vector`）。

扩展脚本（可选，P4）：[`docs/infra/init-db/01-extensions.sql`](../infra/init-db/01-extensions.sql)。

---

## 二、脚本清单

| 文件 | 用途 | 阶段 |
|------|------|------|
| `schema.sql` | 全量 DDL（26 表 + `patient_family_link` + 索引） | P0.5 必跑 |
| `seed-dict.sql` | 科室、号别、员工、排班、测试登录 | P1 联调 |
| `seed-demo-patients.sql` | 演示患者 `MR202606040100` + 今日内科挂号 | P1 可选 |
| `seed-demo-check.sql` | 影像演示：检查申请 #62001（赵大爷 · 头部 CT） | P3/P4 可选 |
| `patch-family-link.sql` | 旧库补家属表（新环境勿单独跑） | 增量 |
| `patch-patient-phone-unique.sql` | 旧库：`phone` 部分唯一索引 `ux_patient_phone` | 增量 |
| `vector.sql` | RAG 向量表（待 Spring AI 版本确定） | P4 |

---

## 三、测试账号（seed 写入后）

| 用途 | 用户名 | 密码 | 角色 |
|------|--------|------|------|
| 门诊医生（内科） | `doctor01` | `123456` | OUTPATIENT_DOCTOR |
| 门诊医生（内科） | `doctor02` 李医生 / `doctor03` 陈教授（专家号） | `123456` | OUTPATIENT_DOCTOR |
| 门诊医生（外科） | `doctor04` 王医生 / `doctor05` 刘教授（专家号）/ `doctor06` 赵医生 | `123456` | OUTPATIENT_DOCTOR |
| 处置医生 | `disposal01` | `123456` | DISPOSAL_DOCTOR |
| 检验 | `lab01` 李检验 / `lab02` 周检验 | `123456` | LAB_DOCTOR |
| 检查（放射/CT） | `check01` 王检查 / `check02` 李影像 / `check03` 陈影像 | `123456` | CHECK_DOCTOR |
| 药房 | `pharmacy01` | `123456` | PHARMACIST |
| 挂号收费 | `registrar01` | `123456` | REGISTRAR |
| 管理员 | `admin` | `123456` | ADMIN（信息科） |

> 密码哈希为 BCrypt；**仅开发环境**，生产须更换。

---

## 四、重置（开发慎用）

```powershell
psql -U postgres -d hospital -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
psql -U postgres -d hospital -f docs/sql/schema.sql
psql -U postgres -d hospital -f docs/sql/seed-dict.sql
```

---

## 五、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05-01 | 首版 schema + seed，对齐 DATABASE_DESIGN |
| v1.1 | 2026-05-15 | 对齐 DEV_ENV_SETUP §6.1.3～6.1.5；CMD 示例与口令说明 |
| v1.2 | 2026-05-20 | `seed-dict.sql` 增加 `\encoding UTF8`；§一 Windows GBK/UTF8 排错 |
| v1.3 | 2026-06-01 | **对齐 DATABASE_DESIGN v1.14**：重写 `schema.sql` / `seed-dict.sql`；家属表并入 schema |
| v1.4 | 2026-06-04 | 文档全库对齐 v1.14（与 `API.md` v1.4 同步） |
| v1.5 | 2026-06-04 | §一 补充导入后验收 SQL（`sys_user` / `employee`）；清单增加 `seed-demo-check.sql` |
| v1.6 | 2026-06-04 | `seed-dict.sql` 扩展：外科/处置科、doctor02～05、disposal01 及排班（原 seed 行不变） |
| v1.7 | 2026-06-04 | 排班：每日含周日；同一半天可多名普通医生；张/李、王/赵各休 1 天/周 |
| v1.8 | 2026-06-04 | 外科 +1 普通医生 `doctor06` 赵医生（emp 12）及排班 |
| v1.9 | 2026-06-04 | `DIS-INF` 静脉输液；`inspection01`/`lab02`；新增 `seed-demo-patients.sql` |
| v1.10 | 2026-06-04 | 检验统一 `lab01`/`lab02`；检查扩展 `check02`/`check03`；废弃 `inspection01` |
| v1.11 | 2026-06-04 | 新增科室「信息科」`INFO_CENTER`(id=8)；`admin` 由挂号处迁至信息科 |
