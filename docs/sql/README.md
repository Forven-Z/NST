# 数据库脚本说明

> **业务库**：PostgreSQL 15+，库名 `hospital`  
> **设计依据**：[`DATABASE_DESIGN.md`](../DATABASE_DESIGN.md) v1.3  
> **执行顺序**：`schema.sql` → `seed-dict.sql`（P1 联调必跑 seed）

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

REM 1. 建表（26 张业务表）
psql -U postgres -d hospital -f docs\sql\schema.sql

REM 2. 灌入 P1 字典与测试账号（ADR-012）
psql -U postgres -d hospital -f docs\sql\seed-dict.sql
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

**`schema.sql` 中的 NOTICE**：首次执行可能出现「约束 fk_prescription_ai_draft 不存在」之类提示，属 `DROP CONSTRAINT IF EXISTS` 的正常现象，可忽略。

**pgvector（P4 再装）**：完整分步见 [`DEV_ENV_SETUP.md`](../DEV_ENV_SETUP.md) **§6.1.6**（下载 zip → 停服务 → 复制到 PG 目录 → `CREATE EXTENSION vector`）。

扩展脚本（可选，P4）：[`docs/infra/init-db/01-extensions.sql`](../infra/init-db/01-extensions.sql)。

---

## 二、脚本清单

| 文件 | 用途 | 阶段 |
|------|------|------|
| `schema.sql` | 全量 DDL（26 表 + 索引） | P0.5 必跑 |
| `seed-dict.sql` | 科室、号别、员工、排班、测试登录 | P1 联调 |
| `vector.sql` | RAG 向量表（待 Spring AI 版本确定） | P4 |

---

## 三、测试账号（seed 写入后）

| 用途 | 用户名 | 密码 | 角色 |
|------|--------|------|------|
| 门诊医生 | `doctor01` | `123456` | OUTPATIENT_DOCTOR |
| 挂号收费 | `registrar01` | `123456` | REGISTRAR |
| 管理员 | `admin` | `123456` | ADMIN |

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
| v1.0 | 2026-05 | 首版 schema + seed，对齐 DATABASE_DESIGN |
| v1.1 | 2026-05 | 对齐 DEV_ENV_SETUP §6.1.3～6.1.5；CMD 示例与口令说明 |
| v1.2 | 2026-05 | `seed-dict.sql` 增加 `\encoding UTF8`；§一 Windows GBK/UTF8 排错 |
