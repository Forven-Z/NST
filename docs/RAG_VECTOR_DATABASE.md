# RAG 向量数据库说明

> 适用模块：`hospital-backend/hospital-ai-bridge`  
> 数据库：PostgreSQL `hospital`  
> 向量扩展：pgvector 0.8.2  
> Embedding：DashScope `text-embedding-v3`，1024 维  
> 文档版本：v1.0（2026-06-22）

## 1. 用途与数据流

向量数据库保存医生端 RAG 的医学知识正文、向量和来源信息，为以下功能提供参考证据：

- AI 智能诊断；
- AI 生成检查草稿；
- AI 生成检验草稿；
- AI 生成处方草稿；
- AI 生成处置草稿。

数据流：

```text
知识库TXT
  → 解析为独立知识文档
  → 标题+主题+正文生成Embedding
  → ai_knowledge_document保存文档信息
  → ai_knowledge_chunk保存正文和vector(1024)
  → 病历生成查询向量
  → pgvector余弦相似度检索
  → evidence返回给医生端AI
```

药品、检查和检验的真实业务 ID、价格、库存及启停状态仍由普通 SQL 目录查询负责，向量知识不能创建目录外项目。

## 2. 数据表

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

索引：

- `document_code` 唯一索引；
- `(knowledge_type, status)` 普通索引。

### 2.2 `ai_knowledge_chunk`

一行代表一个可检索的知识切片。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `BIGINT` | 切片主键 |
| `document_id` | `BIGINT` | 关联文档，文档删除时级联删除 |
| `chunk_no` | `INTEGER` | 文档内切片序号，从1开始 |
| `content` | `TEXT` | 返回给AI的知识正文 |
| `token_count` | `INTEGER` | 近似Token数量 |
| `embedding` | `vector(1024)` | DashScope生成的1024维向量 |
| `metadata` | `JSONB` | 文档元数据副本 |
| `create_time` | `TIMESTAMPTZ` | 创建时间 |

索引：

- `(document_id, chunk_no)` 唯一索引；
- `embedding vector_cosine_ops` HNSW 向量索引。

## 3. 向量化规则

第一版知识摘要通常少于800字，因此一般采用：

```text
1篇知识文档 = 1个chunk = 1个1024维向量
```

超过800字时按自然段切分；单个自然段仍超过800字时再按长度分段。不同知识文档不会拼接成同一个chunk。

用于生成向量的文本：

```text
标题：{title}
主题：{topics}
正文：{chunk content}
```

内容哈希覆盖标题、知识类型、主题和正文。启动时按照 `document_code` 增量同步：

- 新编码：生成向量并新增；
- 内容哈希变化：重新生成该文档向量；
- 内容哈希未变化且向量维度正确：跳过Embedding；
- 单篇失败：记录日志，不阻断服务启动。

## 4. 分类与检索

| 医生端功能 | `knowledge_type` |
|---|---|
| AI智能诊断 | `CLINICAL_GUIDELINE` |
| AI生成检查草稿 | `TECHNOLOGY_GUIDE` |
| AI生成检验草稿 | `TECHNOLOGY_GUIDE` |
| AI生成处方草稿 | `DRUG_INSTRUCTION` |
| AI生成处置草稿 | `DISPOSAL_GUIDE` |

病历查询文本由症状摘要、主诉、现病史、既往史、过敏史、体格检查和初步诊断中的非空字段拼接生成。

检索条件：

```text
知识类型与当前AI功能一致
status = ACTIVE
effective_date为空或已经生效
expire_date为空或尚未过期
余弦相似度 >= 0.55
最多返回6条
```

相似度计算：

```sql
1 - (c.embedding <=> CAST(:embedding AS vector))
```

## 5. 初始化与运行

确认扩展：

```powershell
$env:PGPASSWORD="123456"
psql -U postgres -d hospital -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d hospital -c "SELECT extname,extversion FROM pg_extension WHERE extname='vector';"
```

设置 DashScope Key 后启动 `hospital-ai-bridge`：

```powershell
$env:DASH_SCOPE_API_KEY="你的DashScope API Key"
cd D:\NST\hospital-backend
mvn -pl hospital-ai-bridge -am -DskipTests package
java -jar D:\NST\hospital-backend\hospital-ai-bridge\target\hospital-ai-bridge-1.0-SNAPSHOT.jar
```

启动阶段会自动解析知识文件并执行增量同步。首次导入需要调用Embedding接口，后续未修改知识会直接跳过。

## 6. 验证SQL

正式知识分类统计：

```sql
SELECT knowledge_type,status,COUNT(*)
FROM ai_knowledge_document
WHERE metadata ->> 'dataLevel' = 'OFFICIAL_SUMMARY'
GROUP BY knowledge_type,status
ORDER BY knowledge_type,status;
```

预期：

```text
CLINICAL_GUIDELINE  ACTIVE  35
DISPOSAL_GUIDE      ACTIVE  15
DRUG_INSTRUCTION    ACTIVE  25
TECHNOLOGY_GUIDE    ACTIVE  25
```

验证正式知识切片和维度：

```sql
SELECT COUNT(*) AS chunks,
       MIN(vector_dims(c.embedding)) AS min_dims,
       MAX(vector_dims(c.embedding)) AS max_dims
FROM ai_knowledge_chunk c
JOIN ai_knowledge_document d ON d.id = c.document_id
WHERE d.metadata ->> 'dataLevel' = 'OFFICIAL_SUMMARY';
```

当前预期结果为100个正式知识chunk，最小和最大维度均为1024。数据库总chunk数还包含8个已停用演示文档的历史切片。

查询演示资料状态：

```sql
SELECT source_version,status,COUNT(*)
FROM ai_knowledge_document
WHERE source_version = 'DEMO-1.0'
GROUP BY source_version,status;
```

预期8篇均为 `INACTIVE`。

## 7. 故障与安全边界

- pgvector或Embedding不可用时，医生端RAG降级，不能阻断病历和手工开单。
- 只有100篇正式知识全部成功入库后才停用演示知识。
- `reviewRequired=true` 表示资料仍需医院专业人员审核。
- 不在日志、文档或数据库中保存DashScope API Key。
- 医疗知识仅作为医生辅助依据，不能代替医生诊断、处方或医嘱。

