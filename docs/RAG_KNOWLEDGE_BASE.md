# RAG 医疗知识库文件说明

> 适用模块：`hospital-backend/hospital-ai-bridge`  
> 资源目录：`src/main/resources/rag/official`  
> 文档版本：v1.0（2026-06-22）

## 1. 文件组成

知识库使用4个UTF-8 TXT文件维护，但导入数据库后是100篇可独立检索的知识文档。

| 文件 | 知识类型 | 条目数 | 用途 |
|---|---|---:|---|
| `CLINICAL_GUIDELINE/clinical-guidelines.txt` | `CLINICAL_GUIDELINE` | 35 | 智能诊断与鉴别提示 |
| `TECHNOLOGY_GUIDE/technology-guides.txt` | `TECHNOLOGY_GUIDE` | 25 | 检查、检验选择依据 |
| `DRUG_INSTRUCTION/drug-instructions.txt` | `DRUG_INSTRUCTION` | 25 | 处方与用药安全复核 |
| `DISPOSAL_GUIDE/disposal-guides.txt` | `DISPOSAL_GUIDE` | 15 | 处置适应证、禁忌和转诊提示 |

文件少是为了方便版本管理；数据库文档独立是为了让每个医学主题能够单独召回、更新、审核和停用。

## 2. 文档分隔与格式

同一TXT中的知识使用以下固定行分隔：

```text
=== DOCUMENT ===
```

单篇知识格式：

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

## 3. 字段说明

| 字段 | 必填 | 说明 |
|---|---|---|
| `documentCode` | 是 | 全知识库唯一编码，发布后不应随意修改 |
| `title` | 是 | 独立医学主题标题 |
| `knowledgeType` | 是 | 四种固定知识类型之一 |
| `sourceName` | 是 | 来源机构或官方资料名称 |
| `sourceUrl` | 是 | 可供审核人员核对的来源地址 |
| `sourceVersion` | 是 | 来源版本或摘要批次 |
| `effectiveDate` | 否 | 知识生效日期，格式 `yyyy-MM-dd` |
| `accessedAt` | 是 | 查阅网页或资料日期 |
| `departments` | 否 | 逗号分隔的适用科室 |
| `topics` | 是 | 逗号分隔的症状、疾病或安全主题 |
| `reviewRequired` | 是 | 第一版固定为 `true` |
| 正文 | 是 | 医疗摘要正文 |

## 4. 编码规则

| 前缀 | 类型 | 示例 |
|---|---|---|
| `CLI-` | 诊断知识 | `CLI-001` |
| `TECH-` | 检查检验知识 | `TECH-001` |
| `DRUG-` | 药品知识 | `DRUG-001` |
| `DIS-` | 处置知识 | `DIS-001` |

编码用于数据库upsert和内容更新识别。修改现有知识时保留原编码；新增知识才分配新编码。

## 5. 内容编写原则

- 一篇知识只描述一个独立医学主题。
- 内容包括适用场景、需要评估的信息、危险信号、禁忌或注意事项。
- 不把摘要写成自动诊断或自动开单规则。
- 不编造具体药品剂量、疗程或检查结论。
- 药品和医技项目必须能够与院内业务目录分开管理。
- 优先依据卫健委、药监局、疾控机构、WHO等官方公开资料。
- 使用摘要和改写，不大段复制受版权保护的原文。
- 必须保留来源地址、版本和查阅日期。
- 所有第一版资料均标记 `reviewRequired=true`。

## 6. 新增和修改知识

### 新增

1. 在对应分类TXT末尾增加 `=== DOCUMENT ===`。
2. 填写完整元数据和正文。
3. 分配未使用的唯一 `documentCode`。
4. 保持文件编码为UTF-8。
5. 启动 `hospital-ai-bridge` 执行入库。

### 修改

1. 保留原 `documentCode`。
2. 修改标题、主题、来源或正文。
3. 更新来源版本或访问日期。
4. 重启服务；内容哈希变化后只重新向量化该文档。

### 停用

当前最小版本没有管理端页面。需要停用时，在数据库中将文档状态改为 `INACTIVE`，并在后续正式知识治理功能中补充审核记录。不要删除已经被引用过的历史文档。

## 7. 启动校验规则

启动解析必须满足：

- 总条目数为100；
- `documentCode`全部唯一；
- 分类数量为35、25、25、15；
- 知识类型只能是四种固定值；
- 标题、来源和正文不能为空。

校验失败时停止本次知识同步，但不会阻止AI Bridge启动，也不会停用原演示知识。

## 8. 当前覆盖范围

### 诊断知识

覆盖发热、咳嗽、头痛、胸痛、呼吸困难、腹痛、眩晕、高血压、糖尿病、胃肠道、泌尿、儿科、皮肤、眼科、妇科和外伤等常见门诊主题。

### 检查检验知识

覆盖血常规、炎症指标、尿便常规、肝肾功能、血糖、血脂、电解质、凝血、甲状腺、心肌标志物、胸部影像、CT、MRI、超声、心电图、动态心电图和肺功能等。

### 药品知识

覆盖当前演示药品目录中的20种药品，以及药物过敏、儿童和妊娠、肝肾功能、抗菌药物和重复成分等通用安全主题。

### 处置知识

覆盖清创换药、伤口缝合复核、雾化、氧疗、洗胃、外伤固定、烧伤、鼻出血、异物、导尿、灌肠、冷热敷、严重过敏、心肺复苏和留观转诊。

## 9. 与向量数据库的关系

TXT不是运行时查询数据库。服务启动后会把TXT解析为关系型文档记录，并将正文转换成pgvector向量。

```text
4个TXT文件
  → 100条ai_knowledge_document
  → 100条正式ai_knowledge_chunk
  → 每条embedding为vector(1024)
```

详细表结构、启动命令和验证SQL见 [RAG_VECTOR_DATABASE.md](./RAG_VECTOR_DATABASE.md)。

