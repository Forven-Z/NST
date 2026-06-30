# 医生端 RAG 指南

> **适用模块**：`hospital-backend/hospital-ai-bridge`  
> **服务端口**：9106（经 Gateway：`9000/api/v1/ai/*`）  
> **数据库**：PostgreSQL `hospital` + pgvector  
> **Embedding**：DashScope `text-embedding-v3`（1024 维）  
> **版本**：v1.0 | 2026-06-04  
> **状态**：🟨 开发收尾中（知识入库与 `diagnosis/suggest` 已可联调）

---

## 一、架构概览

医生端 RAG **不**使用 MySQL 或独立 Milvus；复用现有 PostgreSQL 单库 `hospital`，安装 pgvector 扩展。

```text
PostgreSQL hospital
├── 原有 HIS 业务表
├── ai_knowledge_document    知识文档、分类、来源和状态
└── ai_knowledge_chunk       知识正文 + vector(1024)

知识源 TXT（4 个文件，100 条）
  → 启动时解析 + DashScope Embedding
  → 写入上述两表
  → 病历查询向量 → pgvector 余弦检索 → evidence 返回医生端 AI
```

**知识文件目录**：

```text
hospital-backend/hospital-ai-bridge/src/main/resources/rag/official/
├── CLINICAL_GUIDELINE/clinical-guidelines.txt   35 条 · 智能诊断
├── TECHNOLOGY_GUIDE/technology-guides.txt       25 条 · 检查/检验草稿
├── DRUG_INSTRUCTION/drug-instructions.txt       25 条 · 处方草稿
└── DISPOSAL_GUIDE/disposal-guides.txt           15 条 · 处置草稿
```

**数据流**：

```text
知识库 TXT
  → 解析为独立知识文档
  → 标题+主题+正文生成 Embedding
  → ai_knowledge_document 保存文档信息
  → ai_knowledge_chunk 保存正文和 vector(1024)
  → 病历生成查询向量
  → pgvector 余弦相似度检索
  → evidence 返回给医生端 AI
```

药品、检查和检验的真实业务 ID、价格、库存及启停状态仍由普通 SQL 目录查询负责，向量知识**不能**创建目录外项目。

---

## 二、数据表与向量化

### 2.1 `ai_knowledge_document`

一行代表一篇可独立审核、启停和更新的知识文档。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 文档主键 |
| `document_code` | `VARCHAR(128)` | 唯一业务编码，如 `CLI-001` |
| `title` | `VARCHAR(255)` | 知识标题 |
| `knowledge_type` | `VARCHAR(64)` | 知识类型 |
| `source_name` | `VARCHAR(255)` | 来源机构 |
| `source_version` | `VARCHAR(64)` | 来源或摘要版本 |
| `effective_date` | `DATE` | 生效日期，可为空 |
| `expire_date` | `DATE` | 失效日期，可为空 |
| `status` | `VARCHAR(16)` | `ACTIVE` 或 `INACTIVE` |
| `metadata` | `JSONB` | 科室、主题、来源地址、内容哈希和审核标记 |
| `create_time` | `TIMESTAMPTZ` | 创建时间 |
| `update_time` | `TIMESTAMPTZ` | 更新时间 |

索引：`document_code` 唯一；`(knowledge_type, status)` 普通索引。

### 2.2 `ai_knowledge_chunk`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 切片主键 |
| `document_id` | `BIGINT` | 关联文档，级联删除 |
| `chunk_no` | `INTEGER` | 文档内切片序号，从 1 开始 |
| `content` | `TEXT` | 返回给 AI 的知识正文 |
| `token_count` | `INTEGER` | 近似 Token 数量 |
| `embedding` | `vector(1024)` | DashScope 1024 维向量 |
| `metadata` | `JSONB` | 文档元数据副本 |
| `create_time` | `TIMESTAMPTZ` | 创建时间 |

索引：`(document_id, chunk_no)` 唯一；`embedding vector_cosine_ops` HNSW 向量索引。

### 2.3 向量化规则

第一版知识摘要通常少于 800 字，因此一般采用：

```text
1 篇知识文档 = 1 个 chunk = 1 个 1024 维向量
```

超过 800 字时按自然段切分。用于生成向量的文本：

```text
标题：{title}
主题：{topics}
正文：{chunk content}
```

内容哈希覆盖标题、知识类型、主题和正文。启动时按 `document_code` 增量同步：新编码新增；哈希变化重新向量化；未变化且维度正确则跳过；单篇失败不阻断启动。

### 2.4 分类与检索

| 医生端功能 | `knowledge_type` |
|---|---|
| AI 智能诊断 | `CLINICAL_GUIDELINE` |
| AI 生成检查/检验草稿 | `TECHNOLOGY_GUIDE` |
| AI 生成处方草稿 | `DRUG_INSTRUCTION` |
| AI 生成处置草稿 | `DISPOSAL_GUIDE` |

检索条件：知识类型与当前 AI 功能一致；`status = ACTIVE`；生效/失效日期合法；余弦相似度 ≥ 0.55；最多 6 条。

相似度 SQL：`1 - (c.embedding <=> CAST(:embedding AS vector))`

---

## 三、知识库 TXT 格式与维护

### 3.1 文档分隔

同一 TXT 中的知识使用固定行分隔：

```text
=== DOCUMENT ===
```

单篇格式示例：

```text
---
documentCode: CLI-001
title: 成人发热初步评估
knowledgeType: CLINICAL_GUIDELINE
sourceName: 世界卫生组织基层和急症临床资料
sourceUrl: https://www.who.int/...
sourceVersion: 官方公开资料摘要-2026.06
effectiveDate:
accessedAt: 2026-06-22
departments: 内科,急诊科
topics: 发热,危险信号
reviewRequired: true
---
# 成人发热初步评估

成人发热应记录病程、最高体温……
```

### 3.2 字段与编码

| 字段 | 必填 | 说明 |
|---|---|---|
| `documentCode` | 是 | 全库唯一；发布后勿随意改 |
| `title` / `knowledgeType` / `sourceName` / `sourceUrl` / `sourceVersion` / `accessedAt` / `topics` / `reviewRequired` | 是 | 见上例 |
| `effectiveDate` / `departments` | 否 | 可选 |
| 正文 | 是 | 医疗摘要，非自动开单规则 |

编码前缀：`CLI-` 诊断 · `TECH-` 检查检验 · `DRUG-` 药品 · `DIS-` 处置。

### 3.3 编写原则

- 一篇知识只描述一个独立医学主题；含适用场景、危险信号、禁忌。
- 不编造具体剂量、疗程或检查结论；须可与院内目录分开管理。
- 优先卫健委、药监局、WHO 等公开资料；保留来源 URL 与查阅日期。
- 第一版均 `reviewRequired=true`。

### 3.4 新增 / 修改 / 停用

| 操作 | 步骤 |
|------|------|
| 新增 | TXT 末尾加 `=== DOCUMENT ===` → 新 `documentCode` → 重启 ai-bridge |
| 修改 | **保留**原 `documentCode` → 改正文/来源 → 重启（哈希变则重新向量化） |
| 停用 | DB 中将文档 `status` 改为 `INACTIVE`（暂无管理端页面） |

启动校验：总条目 100；`documentCode` 唯一；分类 35/25/25/15；四种 `knowledgeType` 之一。

---

## 四、首次运行前准备

需要：JDK 17、Maven、PostgreSQL 16、pgvector、DashScope API Key。

```powershell
Get-Service *postgres*
# 示例：Start-Service postgresql-x64-16
```

### 4.1 复用已有 `hospital` 库

RAG **必须**复用 HIS 已有库，**禁止** `DROP DATABASE hospital` 或清空业务表。

```powershell
$env:PGPASSWORD="<你的密码>"
psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='hospital';"
```

输出 `1` 表示已存在，直接进入下一节；否则 `createdb -U postgres hospital`。

### 4.2 启用 pgvector

```powershell
psql -U postgres -d hospital -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d hospital -c "SELECT extname,extversion FROM pg_extension WHERE extname='vector';"
```

RAG 表由 `hospital-ai-bridge` 启动时自动创建（`CREATE TABLE IF NOT EXISTS` 可重复执行）。

---

## 五、环境变量与启动

环境变量仅在当前 PowerShell 窗口有效：

```powershell
$env:DB_HOST="127.0.0.1"
$env:DB_PORT="5432"
$env:DB_NAME="hospital"
$env:DB_USER="postgres"
$env:DB_PASSWORD="<你的密码>"

$env:DASH_SCOPE_API_KEY="<你的 DashScope Key>"
$env:DASHSCOPE_EMBEDDING_MODEL="text-embedding-v3"
$env:DASHSCOPE_EMBEDDING_DIMENSIONS="1024"

$env:HOSPITAL_AI_RAG_ENABLED="true"
$env:HOSPITAL_AI_RAG_INITIALIZE_SCHEMA="true"
```

仅单独测 RAG、不启 Nacos 时：`$env:NACOS_DISCOVERY_ENABLED="false"`。完整联调且 Nacos 已启则勿设 `false`。

**不要把真实 API Key 写入 Git、YAML 或文档。**

打包并启动：

```powershell
cd hospital-backend
mvn -pl hospital-ai-bridge -am -DskipTests package
java -jar hospital-ai-bridge/target/hospital-ai-bridge-1.0-SNAPSHOT.jar
```

或开发模式：

```powershell
cd hospital-backend/hospital-ai-bridge
mvn spring-boot:run -DskipTests
```

9106 被占用时先确认进程归属再结束，勿误杀系统进程。

### 5.1 首次启动行为

1. 创建 vector 扩展与 RAG 表（若不存在）  
2. 读取 4 个 UTF-8 TXT，校验 100 条知识  
3. 调用 DashScope 生成 Embedding 并入库  
4. 100 条全部成功后，原 8 条演示知识改为 `INACTIVE`

成功日志示例：

```text
Official knowledge sync finished: updated=100, skipped=0, failed=0, demoDeactivated=8
```

二次启动无变更：`updated=0, skipped=100`。首次约 100 次 Embedding，勿反复重启。

---

## 六、验证

### 6.1 健康检查

```powershell
Invoke-RestMethod http://127.0.0.1:9106/api/v1/ai/health
# 经网关：http://127.0.0.1:9000/api/v1/ai/health（需 Nacos + gateway）
```

### 6.2 SQL 验证

```powershell
$env:PGPASSWORD="<你的密码>"

# 正式知识分类
psql -U postgres -d hospital -c "SELECT knowledge_type,status,COUNT(*) FROM ai_knowledge_document WHERE metadata->>'dataLevel'='OFFICIAL_SUMMARY' GROUP BY knowledge_type,status ORDER BY knowledge_type;"

# 预期：CLINICAL 35 / DISPOSAL 15 / DRUG 25 / TECH 25，均为 ACTIVE

# 切片与维度
psql -U postgres -d hospital -c "SELECT COUNT(*) AS chunks,MIN(vector_dims(c.embedding)) AS min_dims,MAX(vector_dims(c.embedding)) AS max_dims FROM ai_knowledge_chunk c JOIN ai_knowledge_document d ON d.id=c.document_id WHERE d.metadata->>'dataLevel'='OFFICIAL_SUMMARY';"
# 预期：chunks=100, min/max_dims=1024

# 演示知识已停用
psql -U postgres -d hospital -c "SELECT source_version,status,COUNT(*) FROM ai_knowledge_document WHERE source_version='DEMO-1.0' GROUP BY source_version,status;"
# 预期：8 条 INACTIVE
```

### 6.3 Postman：`diagnosis/suggest`

```http
POST http://127.0.0.1:9106/api/v1/ai/diagnosis/suggest
Content-Type: application/json
```

```json
{
  "registerId": 3001,
  "readme": "发热、咳嗽、咽痛3天",
  "present": "最高体温38.5℃，伴轻度胸闷",
  "history": "无特殊病史",
  "allergy": "无",
  "physique": "咽部充血，双肺呼吸音粗"
}
```

RAG 成功时关注：`ragEnabled: true`；`evidence` 非空；`sourceVersion` 非 `DEMO-1.0`；`score >= 0.55`；`reviewRequired: true`。

演示病历样例见 [DEMO_MEDICAL_RECORD_SAMPLES.md](./DEMO_MEDICAL_RECORD_SAMPLES.md)。

---

## 七、常见问题

| 现象 | 排查 |
|------|------|
| `ragEnabled=false` | `HOSPITAL_AI_RAG_ENABLED`、DashScope Key、PG 连接、vector 扩展、维度 1024 |
| `evidence` 为空 | 病历过短；相似度未达 0.55；正式知识未入库；Embedding 失败 |
| `vector dimension mismatch` | 配置与库须同为 1024，勿在已有向量表上切换维度 |
| 9106 端口占用 | `Get-NetTCPConnection -LocalPort 9106` 查进程 |

**安全边界**：pgvector 或 Embedding 不可用时 RAG 降级，不阻断病历与手工开单；`reviewRequired=true` 表示须专业人员审核；知识不能代替医生诊断。

---

## 八、相关文档

| 文档 | 内容 |
|------|------|
| [API.md §七](./API.md) | AI Bridge HTTP 契约 |
| [DESIGN_DECISIONS.md](./DESIGN_DECISIONS.md) | ADR-015 AI 开单 suggest |
| [RUNBOOK.md](./RUNBOOK.md) | 全项目启动与联调 |
| [DEMO_MEDICAL_RECORD_SAMPLES.md](./DEMO_MEDICAL_RECORD_SAMPLES.md) | 演示病历与 RAG 测试用例 |

---

## 九、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-04 | 合并 `RAG_RUN_GUIDE`、`RAG_VECTOR_DATABASE`、`RAG_KNOWLEDGE_BASE` 为唯一 RAG 文档 |
