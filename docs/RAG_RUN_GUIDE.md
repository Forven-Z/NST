# 医生端 RAG 运行指南

> 适用模块：`hospital-backend/hospital-ai-bridge`  
> 服务端口：`9106`  
> 数据库：PostgreSQL `hospital`  
> 向量扩展：pgvector  
> Embedding：DashScope `text-embedding-v3`（1024维）  
> 文档版本：v1.0（2026-06-22）

## 1. RAG组成

医生端RAG不需要MySQL，也不需要单独部署Milvus等向量数据库。它使用现有PostgreSQL数据库并安装pgvector扩展。

```text
PostgreSQL hospital数据库
├── 原有HIS业务表
├── ai_knowledge_document    知识文档、分类、来源和状态
└── ai_knowledge_chunk       知识正文和vector(1024)
```

知识来源位于：

```text
D:\NST\hospital-backend\hospital-ai-bridge\src\main\resources\rag\official
```

目录中有4个TXT，共100条知识：

- 诊断知识35条；
- 检查检验知识25条；
- 药品知识25条；
- 处置知识15条。

## 2. 首次运行前准备

需要安装并启动：

- JDK 17；
- Maven；
- PostgreSQL 16；
- pgvector；
- 可用的DashScope API Key。

确认PostgreSQL服务：

```powershell
Get-Service postgresql-x64-16
Start-Service postgresql-x64-16
```

如果服务名称不同，执行以下命令查找：

```powershell
Get-Service *postgres*
```

## 3. 启用pgvector

### 3.1 检查 `hospital` 数据库是否已经存在

很多开发者已经运行过HIS或初始化脚本，此时 `hospital` 数据库通常已经存在。RAG必须复用这个数据库，**不要删除、清空或重新创建已有数据库**。

先检查：

```powershell
$env:PGPASSWORD="123456"

psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='hospital';"
```

如果输出：

```text
1
```

表示数据库已经存在，直接进入下一节安装或确认vector扩展，不需要再执行创建数据库命令。已有HIS业务表和数据会被保留。

如果没有输出，才创建数据库：

```powershell
createdb -U postgres hospital
```

也可以使用带判断的PowerShell命令：

```powershell
$env:PGPASSWORD="123456"
$exists = (psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='hospital';" | Out-String).Trim()

if ($exists -eq "1") {
    Write-Host "hospital数据库已经存在，直接复用。"
} else {
    createdb -U postgres hospital
    Write-Host "hospital数据库创建完成。"
}
```

禁止为了运行RAG而执行以下操作：

```text
DROP DATABASE hospital
删除原有HIS业务表
重新运行会清空数据的初始化脚本
```

### 3.2 在已有或新建的数据库中启用pgvector

在PowerShell中执行：

```powershell
$env:PGPASSWORD="123456"

psql -U postgres -d hospital -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d hospital -c "SELECT extname,extversion FROM pg_extension WHERE extname='vector';"
```

正常结果类似：

```text
 extname | extversion
---------+------------
 vector  | 0.8.2
```

RAG表由 `hospital-ai-bridge` 启动时自动创建，不需要手工创建第二个数据库：

- `hospital` 已存在且没有RAG表：只新增两张RAG表；
- `hospital` 已存在且RAG表也存在：保留现有表并执行增量知识同步；
- `hospital` 不存在：先创建数据库，再启用vector并启动AI Bridge；
- `CREATE EXTENSION IF NOT EXISTS` 和 `CREATE TABLE IF NOT EXISTS` 可以重复执行，不会重复创建对象。

## 4. 配置环境变量

环境变量只在当前PowerShell窗口有效。关闭窗口后需要重新设置。

```powershell
$env:DB_HOST="127.0.0.1"
$env:DB_PORT="5432"
$env:DB_NAME="hospital"
$env:DB_USER="postgres"
$env:DB_PASSWORD="123456"

$env:DASH_SCOPE_API_KEY="替换为你的DashScope API Key"
$env:DASHSCOPE_EMBEDDING_MODEL="text-embedding-v3"
$env:DASHSCOPE_EMBEDDING_DIMENSIONS="1024"

$env:HOSPITAL_AI_RAG_ENABLED="true"
$env:HOSPITAL_AI_RAG_INITIALIZE_SCHEMA="true"
```

只单独测试RAG、不启动Nacos时增加：

```powershell
$env:NACOS_DISCOVERY_ENABLED="false"
```

完整项目联调并已启动Nacos时，不要设置该变量为 `false`。

不要把真实API Key写入Git、YAML或文档。

## 5. 打包并启动

先确认9106端口没有旧进程：

```powershell
Get-NetTCPConnection -LocalPort 9106 -State Listen -ErrorAction SilentlyContinue
```

进入后端目录并打包：

```powershell
cd D:\NST\hospital-backend

mvn -pl hospital-ai-bridge -am -DskipTests package
```

启动：

```powershell
java -jar D:\NST\hospital-backend\hospital-ai-bridge\target\hospital-ai-bridge-1.0-SNAPSHOT.jar
```

开发环境也可以直接运行：

```powershell
cd D:\NST\hospital-backend\hospital-ai-bridge
mvn spring-boot:run -DskipTests
```

如果提示jar无法重命名或删除，通常是旧的AI Bridge进程仍占用jar。先确认对应进程确实是本项目服务，再停止旧进程并重新打包。

## 6. 首次启动发生什么

服务启动时依次执行：

1. 创建vector扩展和两张RAG表（不存在时）；
2. 读取4个UTF-8 TXT；
3. 解析并校验100条独立知识；
4. 使用DashScope生成1024维Embedding；
5. 写入 `ai_knowledge_document` 和 `ai_knowledge_chunk`；
6. 100条全部成功后，将原8条演示知识改为 `INACTIVE`。

首次启动需要调用约100次Embedding，耗时取决于网络和DashScope服务。不要在导入过程中反复重启。

成功日志类似：

```text
Official knowledge sync finished: updated=100, skipped=0, failed=0, demoDeactivated=8
```

第二次启动内容没有变化时应显示：

```text
Official knowledge sync finished: updated=0, skipped=100, failed=0, demoDeactivated=0
```

## 7. 检查服务

直接访问AI Bridge健康接口：

```powershell
Invoke-RestMethod http://127.0.0.1:9106/api/v1/ai/health
```

如果通过网关联调，则使用：

```text
http://127.0.0.1:9000/api/v1/ai/health
```

通过网关需要同时启动Nacos和 `hospital-gateway`。

## 8. 验证向量入库

统计正式知识：

```powershell
$env:PGPASSWORD="123456"

psql -U postgres -d hospital -c "SELECT knowledge_type,status,COUNT(*) FROM ai_knowledge_document WHERE metadata->>'dataLevel'='OFFICIAL_SUMMARY' GROUP BY knowledge_type,status ORDER BY knowledge_type;"
```

预期：

```text
CLINICAL_GUIDELINE  ACTIVE  35
DISPOSAL_GUIDE      ACTIVE  15
DRUG_INSTRUCTION    ACTIVE  25
TECHNOLOGY_GUIDE    ACTIVE  25
```

检查正式知识切片和向量维度：

```powershell
psql -U postgres -d hospital -c "SELECT COUNT(*) AS chunks,MIN(vector_dims(c.embedding)) AS min_dims,MAX(vector_dims(c.embedding)) AS max_dims FROM ai_knowledge_chunk c JOIN ai_knowledge_document d ON d.id=c.document_id WHERE d.metadata->>'dataLevel'='OFFICIAL_SUMMARY';"
```

预期：

```text
chunks = 100
min_dims = 1024
max_dims = 1024
```

检查演示知识：

```powershell
psql -U postgres -d hospital -c "SELECT source_version,status,COUNT(*) FROM ai_knowledge_document WHERE source_version='DEMO-1.0' GROUP BY source_version,status;"
```

预期8条均为 `INACTIVE`。

## 9. Postman测试RAG

不经过网关，直接测试AI Bridge：

```http
POST http://127.0.0.1:9106/api/v1/ai/diagnosis/suggest
Content-Type: application/json
```

请求体：

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

RAG成功的关键字段：

```json
{
  "ragEnabled": true,
  "evidence": [
    {
      "title": "急性上呼吸道感染",
      "sourceVersion": "官方公开资料摘要-2026.06",
      "score": 0.708,
      "sourceUrl": "https://...",
      "reviewRequired": true
    }
  ]
}
```

实际相似度会随病例内容变化，但 evidence 应满足：

- 不为空；
- `sourceVersion` 不是 `DEMO-1.0`；
- `score >= 0.55`；
- 有来源地址；
- `reviewRequired=true`。

## 10. 修改知识后的更新方式

修改知识文件后保留原 `documentCode`，然后重启AI Bridge。

系统使用内容哈希判断变化：

- 未修改知识直接跳过；
- 修改过的知识重新生成向量；
- 不会重复插入同一编码；
- 单篇失败不会阻断门诊主流程。

知识文件格式和维护规则见 [RAG_KNOWLEDGE_BASE.md](./RAG_KNOWLEDGE_BASE.md)。

## 11. 常见问题

### `ragEnabled=false`

检查：

- `HOSPITAL_AI_RAG_ENABLED` 是否为 `true`；
- DashScope API Key 是否存在；
- PostgreSQL是否可连接；
- vector扩展是否安装；
- Embedding维度是否为1024。

### evidence为空

可能原因：

- 病历内容过少；
- 对应知识分类没有相似度达到0.55的内容；
- 正式知识尚未完成入库；
- Embedding请求失败。

### `vector dimension mismatch`

配置和数据库必须同时为1024维：

```powershell
$env:DASHSCOPE_EMBEDDING_DIMENSIONS="1024"
```

不要在已有向量表上直接切换为其他维度。

### 9106端口被占用

```powershell
Get-NetTCPConnection -LocalPort 9106 -State Listen | Select-Object LocalPort,OwningProcess
Get-Process -Id <OwningProcess>
```

确认进程身份后再决定是否停止，不要盲目结束系统进程。

## 12. 相关文档

- [RAG_VECTOR_DATABASE.md](./RAG_VECTOR_DATABASE.md)：表结构、向量化和检索原理；
- [RAG_KNOWLEDGE_BASE.md](./RAG_KNOWLEDGE_BASE.md)：知识TXT格式和维护规则；
- [RUNBOOK.md](./RUNBOOK.md)：完整项目启动和联调。
