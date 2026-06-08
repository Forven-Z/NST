# 智慧云脑诊疗平台（NST · Nexus Smart Treatment）项目需求文档

> 本文档基于**无 AI 版门诊系统**参考需求整理，并适配本项目的**微服务 + AI** 技术架构。  
> 原始文档中的流程图、用例图、ER 图、原型截图等图片资源可后续补充至 `docs/images/` 目录。

---

## 文档说明

| 项 | 说明 |
|---|---|
| **文档索引** | **[docs/README.md](./README.md)** — 全项目设计文档清单、权威范围、术语、端口 |
| **协作执行** | **[IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md)**、**[PROGRESS.md](./PROGRESS.md)**、**[API.md](./API.md)** |
| 参考来源 | 传统门诊信息化系统需求（挂号 → 看诊 → 检查/检验/处置 → 收费 → 发药） |
| 本项目定位 | 在完整门诊业务链路上，叠加 **AI 问诊/导诊、AI 辅助诊疗、影像智能分析、智能排班分诊** 等能力 |
| 架构约束 | 前后端分离、Java 微服务、**双前端**（患者微信小程序 + 医生/管理 PC）、Python FastAPI CNN，详见 §0.1 |
| 微服务边界 | 以 **[MICROSERVICES.md](./MICROSERVICES.md)** 为唯一权威（非本文重复展开） |

---

## 0. 老师规定 — 必用技术栈（权威）

> 以下为本课程/项目**强制**技术选型，设计与实现均不得擅自替换为核心方案。

| 层次 | 技术选型 | 说明 | 本仓库落地 |
|------|----------|------|------------|
| **前端** | Vue 3 + Element Plus + Pinia + Axios | **患者端**：**原生微信小程序**；**医生端、管理端**：PC 端 | 患者：`hospital-patient-miniapp/`；PC：`hospital-frontend/` → `views/doctor`、`views/admin` |
| **后端核心** | Java 17 + Spring Boot 3.2.4 | 主体业务逻辑；**微服务**拆分 | `hospital-backend/` 各子模块 + Gateway 9000 |
| **大模型 / AI 框架** | **Spring AI** | AI 智能问诊、AI 助理医生、AI 分诊排班等 **LLM 交互** | `hospital-ai-bridge` |
| **影像识别模型** | **CNN**（卷积神经网络） | 医学影像（CT、片检等）**识别与分类** | `hospital-ai/`（PyTorch CNN 推理，FastAPI 对外服务） |
| **数据存储** | **PostgreSQL（pgvector）** | 关系型业务（挂号、处方、病历）+ **向量数据**（RAG 知识库） | 单库或同实例：`vector` 扩展 + 业务表；Spring AI VectorStore |

**与仓库的对应关系（简图）**

```
患者原生微信小程序 (WXML+JS)     医生/管理 PC (Vue3+Element Plus+Pinia+Axios)
              \                            /
               \                          /
                ---- hospital-gateway :9000 ----
                              |
        ┌─────────────────────┼─────────────────────┐
        │ 业务微服务 Boot 3.2.4 │ hospital-ai-bridge │ hospital-ai │
        │ auth/his/lis/pacs/    │ Spring AI + RAG    │ FastAPI+CNN │
        │ management            │                    │  PyTorch    │
        └──────────┬────────────┴──────────┬─────────┴─────────────┘
                   │                       │
            PostgreSQL+pgvector          MinIO（影像对象）
```

**补充说明（不替代上表，仅为实现细节）**

| 项 | 约定 |
|----|------|
| 微服务治理 | Spring Cloud 2023.0.x + **Spring Cloud Alibaba**（Nacos 注册/配置）+ OpenFeign |
| 业务库访问 | MyBatis / MyBatis-Plus 访问 PostgreSQL 关系表 |
| 对象存储 | **MinIO（必配）**：医学影像、CT 原图等大文件 |
| 统一 API | `hospital-common` → `Result<T>` |
| Redis | 可选：网关限流、Token 黑名单等，**不替代** PostgreSQL |

---

## 0.1 项目定稿 —「智慧云脑诊疗平台」技术蓝图

> **产品正式名称**：**智慧云脑诊疗平台**（**NST** — Nexus Smart Treatment，枢纽智能诊疗；简称「云脑医疗」）。业务流程图、答辩材料、系统界面标题均使用此名。  
> **状态：已确认（2026-05）**。与老师 §0 必用栈一致；下列为实现层定稿，答辩与开发均以此为准。

### 0.1.1 前端（双工程）

| 工程 | 终端 | 技术栈 | 语言 |
|------|------|--------|------|
| **`hospital-patient-miniapp/`** | 患者 **微信小程序（原生）** | **WXML + WXSS + JavaScript**；**微信开发者工具**开发与预览；`wx.request` / `wx.uploadFile`；UI 可选 **WeUI** / **Vant Weapp** | **第一期 JavaScript** |
| **`hospital-frontend/`** | 医生 / 管理 **PC** | **Vue 3** + **Element Plus** + **Pinia** + **Axios** + Vite；医生端 **左右 7:3**（左 70% 病历，右 30% AI 助理 SSE 流式） | **第一期 JavaScript**；有余力再将本工程迁 **TypeScript** |

**与老师栈的说明（答辩口径）**

- **PC 医生/管理端**完整满足 **Vue 3 + Element Plus + Pinia + Axios**（老师必用栈）。
- **患者端**为 **微信官方原生小程序**（非 uni-app）：直接在 **微信开发者工具** 中开发，满足「患者端 = 微信小程序」终端要求；与 PC 端通过统一 Gateway API（`Result<T>`）对接。
- 原生小程序 **不使用 Vue / Pinia / Element Plus**（微信运行时限制）；状态可用 `globalData` + 页面 `data` 或轻量 `utils/store.js`。

**患者小程序运行**

- 用 **微信开发者工具** 打开仓库 **`hospital-patient-miniapp/`** 目录（原生工程根目录，含 `app.json`）。
- 开发阶段在工具中关闭「校验合法域名」；后端经 Gateway（默认 `http://localhost:9000`），生产需 HTTPS + 公众平台配置域名。

### 0.1.2 后端（微服务）

| 类别 | 选型 |
|------|------|
| 语言与核心 | **Java 17** + **Spring Boot 3.2.4** + **Spring AI 1.0.0-M7**（ai-bridge） |
| 治理 | **Nacos**（注册 + 配置）、**Spring Cloud Gateway**（:9000，路由 / JWT / 限流）、**OpenFeign**（服务间调用） |
| 实时通信 | 医生 AI 助理：**SSE**（Spring AI 流式）；CT 结果通知：SSE 或 WebSocket（二选一，首期可轮询） |

> **微服务划分与边界**：以 **[DESIGN_DECISIONS.md](./DESIGN_DECISIONS.md)** + **[MICROSERVICES.md](./MICROSERVICES.md)** 为准；架构摘要见 `MICROSERVICES.md` §八。

### 0.1.3 AI 与算法（双引擎 + 排班双轨）

| 引擎 | 技术 | 职责 |
|------|------|------|
| LLM | **Spring AI**（`hospital-ai-bridge`） | AI 问诊、AI 助理医生、分诊建议、RAG（pgvector） |
| 影像 CNN | **PyTorch** + FastAPI（`hospital-ai`） | 无人 CT / 片检 **识别与分类**（**仅 PyTorch，不用 TensorFlow**） |
| 排班运筹 | **Timefold**（`hospital-management`） | 硬约束求解：出诊规则、号源、诊室负载、急诊优先级 |
| 排班验收口径 | **双轨** | **Spring AI 给建议与解释 → Timefold 产出可行排班 → 管理员确认** |

### 0.1.4 数据与基础设施（必配）

| 组件 | 用途 |
|------|------|
| **PostgreSQL 15+** | 挂号、处方、病历、员工科室等关系数据 |
| **pgvector** | 医疗知识库向量，Spring AI RAG |
| **MinIO** | 患者上传影像、CT 原图、报告附件；**必配**，不进 PG 大对象 |
| Redis | 可选，非必配 |

### 0.1.5 医学影像链路（定稿：MinIO + HTTP + 异步）

**首期不上 gRPC。**

```text
[微信小程序] wx.uploadFile
    → [hospital-pacs] 鉴权 → MinIO → imaging_study（PENDING）
    → 异步 HTTP 调 hospital-ai（FastAPI）
    → [hospital-ai] 按 objectKey 从 MinIO 拉流 → PyTorch CNN 推理
    → 回写 PostgreSQL（结构化报告 + 结果图 MinIO 路径）
    → 状态=COMPLETED → 通知医生 PC（SSE/轮询）
```

| 状态（示例） | 含义 |
|--------------|------|
| `PENDING` | 已上传，待分析 |
| `PROCESSING` | CNN 推理中 |
| `COMPLETED` | 可查看报告 |
| `FAILED` | 失败，可重试 |

**原则**：禁止 CT 大图 base64 塞 JSON；元数据与路径在 PG，文件在 MinIO。

### 0.1.6 仓库目录总览

```text
NST/
├── hospital-patient-miniapp/   # 患者：原生微信小程序
├── hospital-frontend/          # 医生 + 管理：PC Web
├── hospital-backend/           # Java 微服务
├── hospital-ai/                # Python CNN 推理
└── docs/
```

### 0.1.7 微服务与「三系统」口径（摘要）

| 问题 | 定稿答案 |
|------|----------|
| 是否和「一体化」矛盾？ | **否**。一体化 = 同一平台、同一业务库、一条门诊闭环；微服务 = **多进程部署**与职责拆分 |
| 老师说的 HIS/LIS/PACS？ | **三个 Java 微服务**：`hospital-his` / `hospital-lis` / `hospital-pacs`；CNN 在 **hospital-ai（FastAPI）** |
| 微服务要做到什么程度？ | **M1～M10**、启动组合 **R-min～R-full**，见 **`MICROSERVICES.md`** |
| 故障隔离？ | 设计目标：尤其 **停 AI/影像，门诊仍可用**；共享 PostgreSQL/Gateway 为已知单点 |
| 逻辑子模块放哪？ | **his 内扁平 `controller.*` 包**（`patient` / `doctor` / `registrar` / `pharmacy`），见 **`MICROSERVICES.md` §2.3**；业务域分工见 **`TEAM_COLLABORATION.md` §9.3** |
| 患者登录？ | **his** `/patient/auth/wechat` + **auth** `/internal/token/patient`（方案 C） |
| 代码是否已完成？ | **P3 核心已完成**（R-min～R-reversal 验收通过）；P4 AI/CNN 待做；见 **`PROGRESS.md`** |

---

## 1. 项目开发

### 1.1 开发流程（整体）

| 阶段 | 要点 |
|------|------|
| 可行性研究 | 工作负荷、费用、技术可行性、备选方案、效益与合规 |
| 需求分析 | 功能与模块划分、性能指标、数据管理要求 |
| 系统设计 | 指导编码与协作，支撑测试与运维 |
| 系统实现 | 按模块迭代编码 |
| 系统测试 | 验证健壮性与功能完整性 |
| 系统维护 | 缺陷修复、二次开发、版本演进 |

**迭代策略（建议）**

1. **先启迭代**：确认需求，以框架搭建为主（当前阶段）
2. **精化迭代**：完善分析与设计，进入核心代码，少量测试
3. **构造迭代**：编码为主，完善需求与设计，强化测试，准备部署（可多次）
4. **移交迭代**：部署、验收、少量优化

> **技术架构图**：见 [`MICROSERVICES.md`](./MICROSERVICES.md) §八、[`images/tech-architecture.png`](./images/tech-architecture.png)  
> **API 接口文档**：见 [`API.md`](./API.md)（Java 完整契约；Python/LLM 预留 STUB）  
> **环境配置手册**：见 [`DEV_ENV_SETUP.md`](./DEV_ENV_SETUP.md)（**Windows 本机、不装 Docker**；团队基线 JDK17 / Maven3.9.10 / **Nacos2.2.3** / Node22 / Redis5 / PyTorch2.x+CUDA12.6）  
> 流程总览图、迭代示意图：见参考文档原图，待补充至 `docs/images/dev-process.png`

---

### 1.2 项目背景（AI 版）

本平台以人工智能为能力底座，围绕**智慧诊疗**建设目标，将传统门诊流程升级为**智能诊疗工作流**。

| 端 | 核心能力 |
|----|----------|
| **患者端（微信小程序）** | AI 智能问诊、智能导诊；线上挂号、电子病历查看、影像上传 |
| **医生端** | 门诊全流程数字化（病历、检查/检验/处置、处方）；**AI 助理**（辅助诊断、处方建议、电子病历生成等） |
| **管理端（PC）** | 科室/人员/排班、挂号级别、基础数据；**Spring AI 分诊建议 + Timefold 排班求解** |

**技术特征（对齐 §0 / §0.1 定稿）**

- **双前端**：患者 **原生** 微信小程序 + 医生/管理 **Vue 3** PC Web
- **Spring AI**：问诊、助理、分诊建议、RAG（`hospital-ai-bridge`）
- **Timefold**：排班硬约束求解（`hospital-management`）
- **CNN + MinIO**：PyTorch 推理；影像文件对象存储
- **PostgreSQL + pgvector**：业务库 + 向量库

---

### 1.3 开发目标

为医院门诊建设一套信息化系统，覆盖 **挂号 → 看诊 → 检查/检验/处置 → 确诊 → 处方 → 收费 → 发药** 全链路，为医护与患者提供便利，实现门诊业务的**系统化、可追溯、可统计**。

**AI 版增量目标**

- 患者侧：降低就医路径不确定性（导诊、预问诊）
- 医生侧：提升病历与医嘱编写效率，辅助决策（非替代医师终审）
- 管理侧：优化排班与分诊资源配置

---

### 1.4 需求调研（业务摘要）

| 调研对象 | 典型问题 | 业务要点 |
|----------|----------|----------|
| 挂号窗口 | 窗口功能 | 现场挂号/退号、收费/退费、费用查询、日结 |
| 门诊医生 | 坐诊管理 | 病历、初步判断、开立检查/检验、根据结果确诊、开药/处置 |
| 药房 | 药房管理 | 入库维护、按缴费发药、退药入库、交易记录 |
| 检查科 | 检查管理 | 按申请执行检查、录入结果、历史结果管理 |
| 检验科 | 检验管理 | 按申请执行检验、录入结果、历史结果管理 |
| 处置科 | 处置管理 | 按申请执行处置、录入结果、历史结果管理 |

---

### 1.5 本项目技术栈（与仓库对齐）

> 必用项以 **§0 老师规定** 为准；本节为目录与工程化补充。

#### 患者端 `hospital-patient-miniapp/`（原生微信小程序）

| 技术 | 用途 |
|------|------|
| **WXML + WXSS + JavaScript** | 页面结构与样式（微信官方框架） |
| **微信开发者工具** | 创建工程、编辑、预览、上传 |
| **`wx.request`** | 调用 Gateway REST API（统一 `Result<T>`） |
| **`wx.login` / `wx.uploadFile`** | 微信登录、影像上传 |
| **WeUI / Vant Weapp**（可选） | 原生组件库，替代 Element Plus |
| 状态 | `App.globalData` + 各页 `data`，或 `utils/store.js` |

典型页面：登录（微信 code）、挂号、待缴/缴费记录、AI 问诊（P4）、病历查看、影像上传、检查进度查询。

#### 医生 / 管理端 `hospital-frontend/`（PC，必用：Vue 3 + Element Plus + Pinia + Axios）

| 技术 | 用途 |
|------|------|
| Vue 3 + `<script setup>` | 页面与组件（**必用**） |
| Element Plus | UI 组件库（**必用**，仅 PC 端） |
| Pinia | 状态管理（**必用**） |
| Axios | 业务 API（**必用**）；AI 流式另用 **fetch + SSE** |
| Vite | 构建与 dev server |
| 语言 | **JavaScript**（第一期）；有余力再迁 **TypeScript** |

| 目录 | 终端 | 场景 |
|------|------|------|
| `src/views/doctor/` | **PC** | 患者队列、病历、医嘱；**7:3 布局** + AI 助理（SSE） |
| `src/views/admin/` | **PC** | 基础数据、排班发布、Timefold 结果确认、统计 |

> `hospital-frontend` **不再承载患者端**；原 `views/patient/` 仅作占位，实现迁移至 miniapp。

#### 后端 `hospital-backend/`（JDK 17，Spring Boot 3.2.4，Spring Cloud 2023.0.x，Spring AI 1.0.0-M7）

| 模块 | 职责 |
|------|------|
| `hospital-common` | 统一响应 `Result<T>`、全局异常、公共工具 |
| `hospital-gateway` | 网关，端口 **9000**，Nacos 注册发现 |
| `hospital-auth` | 认证鉴权、JWT |
| `hospital-his` | **HIS**：患者小程序、门诊医生、挂号收费、药房、**处置开立**（:9102） |
| `hospital-lis` | **LIS**：检验申请队列、执行、结果录入（:9103） |
| `hospital-pacs` | **PACS**：检查、影像任务、调 Python CNN（:9104） |
| `hospital-disposal` | **处置执行**：队列、执行、结果录入（:9105，ADR-017） |
| `hospital-management` | 管理端：基础数据、**Timefold 排班**（:9107） |
| `hospital-ai-bridge` | **Spring AI**：问诊、助理、RAG（:9106） |

#### 数据层（必用：PostgreSQL + pgvector）

| 用途 | 存储方式 |
|------|----------|
| 挂号、处方、病历、员工科室等 | PostgreSQL **关系表**（MyBatis 访问） |
| RAG 知识库、文档切片嵌入 | PostgreSQL **`vector` 扩展**（pgvector），由 **Spring AI VectorStore** 读写 |

| 技术 | 说明 |
|------|------|
| **PostgreSQL** | 主库（**不使用 MySQL**）；建议 15+ / 16+，并安装 `CREATE EXTENSION vector` |
| **pgvector** | 向量列类型 `vector(n)`，支撑相似度检索（RAG） |
| **MyBatis / MyBatis-Plus** | 仅负责关系型业务表（课程实现层补充） |
| `postgresql` 驱动 | `org.postgresql:postgresql` |

参考需求文档中的 `INT`/`DATETIME`/`DECIMAL` 等 MySQL 写法，落地 PG 时建议映射为：`INTEGER`/`BIGSERIAL`、`TIMESTAMP`/`TIMESTAMPTZ`、`NUMERIC(8,2)`、`VARCHAR`、`BOOLEAN` 或 `SMALLINT`（如 `delmark`）。

连接配置示例（业务数据源）：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${PG_HOST:127.0.0.1}:${PG_PORT:5432}/${PG_DATABASE:hospital}
    username: ${PG_USER:postgres}
    password: ${PG_PASSWORD:123456}
    driver-class-name: org.postgresql.Driver
```

RAG 向量表示例（Spring AI + pgvector，在 `hospital-ai-bridge` 配置）：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
-- 具体表结构随 Spring AI PgVectorStore 版本生成或见 docs/sql/vector.sql
```

表设计文档：**`docs/DATABASE_DESIGN.md`**（字段级设计，当前交付物）。  
建表脚本：[`docs/sql/schema.sql`](./sql/schema.sql)、[`docs/sql/seed-dict.sql`](./sql/seed-dict.sql)（向量/RAG：`docs/sql/vector.sql` P4 再写）。

#### 对象存储 MinIO（必配）

| 项 | 说明 |
|----|------|
| 用途 | CT/片检原图、推理结果图、报告 PDF 等 |
| 访问 | Java SDK；Python 推理服务按 `bucket` + `objectKey` 读取 |
| 配置 | `MINIO_ENDPOINT`、`MINIO_ACCESS_KEY` 等，建议 Nacos 统一管理 |

#### AI 影像 `hospital-ai/`（必用：CNN）

| 项 | 说明 |
|----|------|
| 框架 | **仅 PyTorch**（CNN） |
| 协议 | **HTTP**（FastAPI）；首期 **不用 gRPC** |
| 调用方式 | Java 异步任务触发；长耗时走任务状态轮询 |
| 与 Spring AI | 影像 = CNN；文本 = Spring AI，边界分离 |

---

## 2. 需求分析

### 2.1 一般性需求

- 门诊主流程完整、正确，交互清晰
- 基于浏览器，免复杂客户端安装，易扩展
- 模块可按实训周期**裁剪**（先 MVP 后完整）

### 2.2 功能性需求（核心业务）

| 编号 | 模块 | 主要用户 | 功能摘要 |
|------|------|----------|----------|
| F1 | 挂号管理 | 挂号收费员 | 现场挂号/退号、收费/退费、费用查询、发票与日结 |
| F2 | 坐诊管理 | 门诊医生 | 病案、模板、检查/检验/处置/处方开立与复诊 |
| F3 | 检查/检验管理 | 检查/检验科医师 | 申请查看、执行登记、结果录入、历史查询 |
| F4 | 处置管理 | 处置科医师 | 同 F3 模式 |
| F5 | 药物信息 | 药师 | 药库、发药、退药、发退药记录 |
| F6 | 患者端（扩展） | 患者 | 预约/挂号、费用查询、**AI 问诊/导诊** |
| F7 | 智能管理（扩展） | 管理员 | 排班、分诊、基础数据、**AI 排班建议** |

### 2.3 角色与功能分解

> **PC 端说明**：门诊医生、检查/检验/处置医生、药师、挂号收费员、管理员均使用 **`hospital-frontend`（PC 浏览器 Web）**，按 `employee.role_type` 登录后展示不同菜单；**不是**多个独立客户端。患者仅使用 **原生微信小程序**。七泳道与处置科分工见 `BUSINESS_FLOW.md` §四。

| 角色 | 终端 | 说明 |
|------|------|------|
| 挂号窗口 | PC | 现场挂号/退号、收费/退费、患者收费信息查询 |
| 门诊医生 | PC | 诊疗、开立检查/检验/处置/处方、复诊与确诊 |
| 检查医生 | PC | 检查申请处理、结果录入（如 B 超、CT 等） |
| 检验医生 | PC | 检验申请处理、结果录入（如血常规等） |
| 处置医生 | PC | 处置申请处理、结果录入（如洗胃等治疗性操作） |
| 药师 | PC | 发药、退药；药库入库与库存（可与「药房管理」合并菜单） |
| 患者 | 微信小程序 | 挂号/预约、缴费查询、**电子病历查看**、AI 预问诊与导诊（P4+） |
| 系统管理员 | PC | 科室、人员、排班规则、挂号级别、结算类别等 |

> 用例图：参考原图 `docs/images/use-case.png`（待补充）

#### 2.3.1 费用/支付流水可见范围（已定稿）

> **流水**指：`bill`（待缴单）、`payment_record`（支付流水）、`refund_record`（退款流水）及关联展示。  
> 原则：**全程留痕、按角色授权、患者只看本人**；医护人员以**医嘱是否已缴费**为主，不开放全院资金明细。

| 角色 | 可见内容 | 不可见 / 一期不做 |
|------|----------|-------------------|
| **患者**（小程序） | 本人待缴单、已支付记录、退款记录；可按 `register_id` 查看当次就诊费用汇总 | 他人流水、全院统计、收费员操作日志 |
| **挂号收费员**（PC） | 窗口收费/退费时：按病历号/姓名查**该患者**待缴与已付；本人经手收费/退款；**当班/日结汇总**（P2 可选） | 无授权时调阅全院任意患者历史流水 |
| **门诊医生**（PC） | 本次接诊相关医嘱的 **缴费状态**（未缴/已缴，用于流程判断） | 支付渠道、第三方单号、他人费用、全院流水 |
| **检查/检验/处置医生** | 本科室相关申请单的 **缴费状态** | 同上 |
| **药师** | 待发药处方的 **缴费状态**；发药操作记录（业务表，非资金流水） | 患者其他费用项、支付流水明细 |
| **系统管理员** | 按日期/科室/渠道的费用 **汇总统计**、异常退款处理（管理端） | 一期可不实现财务大屏；实现时仍须审计日志 |

**与流程图对应**

| 流程节点 | 主要使用角色 |
|----------|----------------|
| 费用记录查询（详细七泳道 · 收费员） | 患者（小程序「我的费用」）、挂号收费员 |
| 收费 / 退费 | 挂号收费员；线上待缴由患者在小程序微信支付 |
| 医生开立后执行/发药 | 医技/药师仅校验 **已缴费**，不查 `payment_record` 全字段 |

**接口实现约束（`hospital-auth` + 各微服务）**

- 患者 API：`patient_id` 必须来自登录态，禁止按他人 ID 查询。
- 收费员 API：按病历号/挂号 ID 查询单患者；列表接口需角色 `REGISTRAR`。
- 医生/医技 API：仅返回 `status` 或 `paid_flag`，不返回 `third_party_trade_no` 等敏感字段。
- 管理员 API：聚合查询，不替代患者隐私导出。

详见 **`DATABASE_DESIGN.md` §8.5**。

#### 2.3.2 诊疗数据存储与隐私访问（已定稿）

> **流水**（§2.3.1）管「钱」；本节管「病」——病历、医嘱、医技结果、处方、影像等。

**存储归属（权威数据源）**

| 数据类型 | 权威存储 | 说明 |
|----------|----------|------|
| 病历、诊断、医嘱文本 | PostgreSQL（`medical_record` 等） | 医生 PC 书写与保存；**不以小程序本地为唯一副本** |
| 检查/检验/处置申请与结果 | PostgreSQL（`*_request` 等） | 含 `result_text`；大文件走 MinIO |
| 处方与发药记录 | PostgreSQL（`prescription` 等） | |
| 医学影像原图/结果图 | MinIO + `imaging_study` 索引 | CNN 异步任务读写对象存储 |
| 患者小程序本地 | 仅 **登录态**（token 等） | 病历/结果通过 API **按需拉取展示**；换机、卸载后仍以院内库为准 |

**原则**

- **医院必须存储**病情与诊疗记录，否则无法完成接诊、医技执行、复诊与流程留痕；隐私保护依靠 **权限与审计**，而非「不存病历」。
- **患者端 = 访问层**：小程序「电子病历查看」展示 **本人、已授权范围内** 的数据，数据来自 `hospital-his` 查询院内库。
- **AI**（问诊、助理、RAG、CNN）仅处理 **服务端已落库或本次会话授权** 的数据；AI 输出为建议/草稿，**不替代**医师确认（见 `BUSINESS_FLOW.md` 补-24、补-25）。

**角色可见范围（诊疗数据，摘要）**

| 角色 | 可见内容 | 不可见 / 限制 |
|------|----------|----------------|
| **患者**（小程序） | 本人历次/当次 **已提交** 病历摘要、检查检验结果、处方摘要、费用（见 §2.3.1） | 他人病历；医护内部备注；未授权科室数据 |
| **门诊医生**（PC） | 本次及历史接诊相关 **病历、医嘱、医技结果**（按业务需要） | 无业务关联的其他患者随意调阅（一期可按病历号/队列限定） |
| **检查/检验/处置医生** | 本科室 **已缴费** 申请单及结果录入 | 其他科室病历全文；支付流水明细（§2.3.1） |
| **药师** | 待发药 **处方明细**（已缴费） | 完整病历、无关检查检验 |
| **挂号收费员** | 患者 **基本信息**、待缴/已付（§2.3.1） | 一般 **不看** 病历诊断全文（除非院方另行授权） |
| **系统管理员** | 基础数据、统计报表 | 一期 **不做** 批量导出全院病历；若做审计须留痕 |

**接口实现约束（与 §2.3.1 并列）**

- 患者 API：`patient_id` 必须来自登录态（JWT / 微信绑定），禁止通过路径参数查询他人 `register_id` / `medical_record_id`。
- 医护 API：按 `role_type` + 科室/申请单归属过滤；写操作记录 `doctor_id` / `executor_id` 等审计字段。
- 传输：**HTTPS**（Gateway）；生产环境敏感字段可按需脱敏展示（如身份证号部分掩码）。
- **保存年限**：真实医院须符合病历管理规范；**毕设一期** 以实现完整业务流程与权限为主，法定长期归档可作为扩展说明。

详见 **`DATABASE_DESIGN.md` §8.6**。

---

### 2.4 业务流程

**权威文字说明**：见 **[BUSINESS_FLOW.md](./BUSINESS_FLOW.md)**  

- **【图】** = 业务流程泳道图  
- **【态】** = 老师提供的关键状态图（§八），**单据状态与收费顺序以此为准**  
- **【补】** = 自行补充项；已解决见 §5.1，**仍待您决定见 §5.2**  
- **§3.11** = 完整主链叙述  

**原图归档**

| 图 | 文件 |
|----|------|
| 总览（三端 + AI） | [images/biz-flow-overview.png](./images/biz-flow-overview.png) |
| 详细（七泳道，2026-05 更新） | [images/biz-flow-detail.png](./images/biz-flow-detail.png) |
| 挂号/检查/检验/处方/处置状态图 | [images/state-*.png](./images/) · 见 BUSINESS_FLOW §八 |

#### 2.4.1 技术实现与业务流程的分工

| 文档 | 内容 |
|------|------|
| `BUSINESS_FLOW.md` | **业务做什么**（按您的流程图复述） |
| 本文 §0.1 | **系统用什么技术做**（Vue / Spring AI / CNN / PG 等） |

二者勿混读：例如「MinIO、异步 CNN、Timefold」等只出现在技术章节，**不擅自加入**业务流程正文。

**费用与就诊账户（业务定稿摘要，详见 BUSINESS_FLOW §二）**

- **先缴费后看病**；**不预存余额、按单支付**（一期不做充值中心）。  
- **就诊账户**：患者身份、病历、待缴单；**不设预存余额**。  
- **支付渠道**：小程序 **微信支付**；收费窗口 **现金、扫码** 等；每笔费用独立支付并记 **支付流水**。  
- **流水可见范围**：见 **§2.3.1**（患者本人、收费员、管理员汇总；医生/医技仅看缴费状态）。  
- **诊疗数据存储与隐私**：见 **§2.3.2**（院内 PostgreSQL/MinIO 为权威源；小程序仅展示本人数据）。  
- **挂号费**：普通号 **20 元**、专家号 **65 元**（配置于 `regist_level` 等）。  
- **线上挂号** 等同窗口挂号：付清当笔挂号费后 → `register` 状态 **已挂号**。  
- 检查/检验/处方/处置：**已开立 → 缴费 → 已缴费** 后再执行/发药（与老师状态图一致）。  
- **检查 vs 检验**：泳道图为 **医生条件选择**（参考 AI 智能诊断 + 医生主观判断）；AI 辅助开立，**医生确认后提交**；非 AI 自动双开。  
- **AI 辅助处方**：AI 生成建议草稿，医生 **可修改**，**点击确认后提交** 方为「已开立」；禁止 AI 直出已生效处方。  
- **退号**：挂号费 **原路退回**。

---

## 3. 系统设计

### 3.1 主线原型流程（操作步骤摘要）

以下保留参考文档中的**可执行步骤**，供前后端接口与页面拆分；界面原型图待补充。

#### 3.1.1 窗口挂号

1. 挂号员进入「窗口挂号」
2. 系统生成**病历号**；默认当前日期、当前午别（不可改）
3. 选择挂号级别、科室 → 系统显示挂号费、可出诊医生列表
4. 可选指定医生
5. 录入患者基本信息（姓名、性别、出生日期、年龄、结算类别、身份证、住址、是否要病历本等）
6. 点击「挂号」完成

#### 3.1.2 门诊医生 — 患者查看

1. 医生登录 →「患者查看」
2. 展示已看诊人数、排队人数；本医生挂号患者列表（按挂号时间分页）
3. 按病历号/姓名查询
4. 点击「创建病例」开始看诊

#### 3.1.3 病历首页

填写：主诉、现病史、现病治疗情况、既往史、过敏史、体格检查；初步诊断（选疾病）；检查/检验建议 → 保存。

#### 3.1.4 检查申请

新增检查项目（编码/名称/规格/单价查询）→ 填写目的、部位、备注 →「申请提交」。

#### 3.1.5 收费

输入病历号 → 展示患者信息与本次待缴项目（按开立时间降序）→ 勾选项目 → 自动合计 →「收费结算」。

#### 3.1.6～3.1.8 检查科

- **检查申请**：待检查队列 →「进行检查」登记  
- **患者录入**：分配科室/检查医生 →「开始检查」  
- **结果录入**：选已完成项目 → 录入结果 →「结果提交」

#### 3.1.9～3.1.11 医生复诊与确诊

- **看诊记录**：已看诊列表 →「更新病例」继续看诊  
- **查看检查结果**：按项目查看结果  
- **门诊确诊**：录入诊断结果与处理意见 →「确诊提交」

#### 3.1.12 开设处方

搜索药品 → 加入处方 → 填写用法、用量、频次、天数、嘱托、数量 → 可增删 →「开立处方」；界面显示金额合计。

#### 3.1.13 药房发药

查询待发药患者（病历号/姓名，状态：未发/已发/已退/全部）→ 选择患者 → 展示待发药品 →「发药」。

> 各步骤对应原型截图：见参考文档 3.1 节，建议统一放入 `docs/images/prototype/`。

---

### 3.2 微服务与参考单体模块映射

参考文档原后端为 **common + outpatient(8092) + drugstage(8091)** 单体划分（`outpatient` 为老师参考工程名）；本项目建议映射如下：

> **his 内部分包**（非独立 jar）：参考工程 `outpatient` 在代码中对应 **`controller.patient` + `controller.doctor` + `controller.registrar` + `controller.pharmacy`** 扁平结构，见 **`MICROSERVICES.md` §2.3**。

| 参考模块 | 本仓库微服务 | 说明 |
|----------|--------------|------|
| common | `hospital-common` | 已实现 `Result`、全局异常 |
| 门诊/挂号/医生/药房 | `hospital-his` | 患者/医生/收费/发药 API |
| 检验 | `hospital-lis` | `/api/v1/lis/**` |
| 检查/影像 | `hospital-pacs` + `hospital-ai` | Java 流程 + Python CNN |
| 字典/排班 | `hospital-management` | `/api/v1/admin/**` |
| 药库 | `hospital-management` 或独立子域 API | 可与管理端共用服务 |
| 认证 | `hospital-auth` | JWT、角色权限 |
| AI 对话/ RAG | `hospital-ai-bridge` | 对接大模型 |
| 影像推理 | `hospital-ai` | Python FastAPI |

**网关**

- 端口：`9000`
- 服务发现：Nacos（`NACOS_SERVER_ADDR` 等环境变量）

---

### 3.3 数据设计

#### 3.3.1 核心实体（ER）

主要实体：员工、科室、挂号级别、结算类别、排班、挂号记录、检查/检验/处置申请、医技项目、病历、疾病、处方、药品等。

> ER 图：`docs/images/er-diagram.png`（待补充）

#### 3.3.2 数据表清单

> **完整表设计（字段、类型、主外键、状态枚举）见 [`DATABASE_DESIGN.md`](./DATABASE_DESIGN.md)**。  
> 下表为与参考需求对照的**索引**；实际共 **26 张业务表**（含患者、支付、处方明细等扩展）。

| 分组 | 表名 | 说明 |
|------|------|------|
| 基础 | `department`, `regist_level`, `settle_category`, `employee`, `scheduling`, `drug_info`, `disease`, `medical_technology` | 字典与排班 |
| 患者/认证 | `patient`, `patient_wechat`, `sys_user` | 就诊账户、微信绑定、员工登录 |
| 就诊 | `register`, `medical_record`, `medical_record_disease` | 挂号、病历 |
| 医技 | `check_request`, `inspection_request`, `disposal_request` | 检查/检验/处置 |
| 处方 | `prescription`, `prescription_item`, `ai_prescription_draft` | 处方头、明细、AI 草稿 |
| 支付 | `bill`, `payment_record`, `payment_bill`, `refund_record` | 按单待缴、支付、合并付、退款 |
| AI/影像 | `imaging_study`, `ai_chat_session` | CNN 任务、对话会话（可选） |

#### 3.3.3 关键字段与状态（实现必读）

**挂号表 `register.visit_state`**

| 值 | 含义 |
|----|------|
| 1 | 已挂号 |
| 2 | 医生接诊 |
| 3 | 看诊结束 |
| 4 | 已退号 |

**检查/检验/处置申请**

- 共性字段：`register_id`、`medical_technology_id`、开立时间、执行人、结果录入人、结果、状态、备注
- 状态流转：**已开立(10) → 已缴费(20) → 执行完成(30) → 已出结果(40)**；**已退费(50)** 见 `BUSINESS_FLOW.md` §8.2

**处方 `prescription.status`**

- **已开立(10) → 已缴费(20) → 已发药(30)**；退药链：**已退药(40) → 已退费(50)**

**病历 `medical_record`**

- 主诉 `readme`、现病史 `present`、体格检查 `physique`、诊断 `diagnosis`、处理意见 `cure` 等（列级定义见 **`DATABASE_DESIGN.md` §5.2**）
- **患者小程序仅可读 `status = 2`（已确诊提交）** 的病历（见 `DATABASE_DESIGN.md` §1.5 `medical_record_status`）

> 建表脚本已交付：**`docs/sql/schema.sql`**（PostgreSQL，对齐 **DATABASE_DESIGN v1.14**）；执行说明见 **`docs/sql/README.md`**。

---

### 3.4 关键状态图（说明）

| 图 | 内容 | 文件占位 |
|----|------|----------|
| 挂号患者状态 | 已挂号 → 接诊 → 结束 / 退号 | `docs/images/state-register.png` |
| 开立检查状态 | 申请 → 收费 → 执行 → 出结果 | `docs/images/state-check.png` |
| 开立检验状态 | 同上 | `docs/images/state-inspection.png` |
| 开立处方状态 | 开立 → 收费 → 发药 | `docs/images/state-prescription.png` |
| 开立处置状态 | 同检查检验模式 | `docs/images/state-disposal.png` |

---

### 3.5 前端模块与页面（参考命名 → 本项目规划）

#### 3.5.1 参考文档前端模块

| 模块 | 路由/页面示例 |
|------|----------------|
| registration | 窗口挂号、退号、收费、退费、费用管理 |
| physician | 患者查看、病历首页、检查/检验/处置申请、看诊记录、结果查看、确诊、处方 |
| check | 检查申请、患者录入、结果录入、检查管理 |
| inspection | 检验申请、患者录入、检验录入、检验管理 |
| drugstore | 发药、退药、药库、交易记录 |
| disposal | 处置申请、患者录入、处置录入、处置管理 |

#### 3.5.2 本项目前端目录规划

```
hospital-patient-miniapp/          # 患者微信小程序（原生 WXML+JS）
├── pages/                         # 挂号、问诊、病历、影像上传
├── utils/                         # store.js、auth.js 等
└── api/                           # wx.request 封装

hospital-frontend/src/views/       # 仅 PC
├── doctor/                        # 7:3 + AI 助理 SSE
└── admin/                         # 排班、基础数据、药房/医技配置
```

管理端可合并原 `registration`、`drugstore` 等能力；医技科室可作为 `admin` 子角色，按实训裁剪。

---

## 4. 非功能需求（摘录）

| 类型 | 要求 |
|------|------|
| 性能 | 列表分页；网关与核心接口响应可监控 |
| 安全 | JWT 鉴权；敏感字段脱敏；AI 输出需医生确认 |
| 可用性 | 医生/管理：浏览器；患者：微信小程序；统一 `Result<T>` |
| 小程序 | 微信登录、合法域名、上传大小限制；开发工具域名校验可关闭 |
| 可维护 | 微服务边界以 [MICROSERVICES.md](./MICROSERVICES.md) 为准；全文档索引 [README.md](./README.md) |
| 可扩展 | 新增医技项目、药品、疾病字典无需改核心流程代码 |

---

## 5. 实施建议（分阶段）

| 阶段 | 范围 | 交付物 / 微服务里程碑 |
|------|------|---------------------|
| P0（当前） | 父 POM、common、gateway、his/lis/pacs 模块占位 | **代码**：gateway/common 可核对；**文档**：`MICROSERVICES.md` 等定稿 |
| P0.5 | 定稿文档、**执行 schema+seed**、小程序/PC 骨架 | 见 `sql/README.md`、`IMPLEMENTATION_PLAN` §二 |
| P1 | 微信登录 + 挂号 + 接诊 + 病历 + 字典 | **R-min**：gateway + auth + **his** + **management**（或 `seed-dict.sql`，见 ADR-012） |
| P2 | 收费 + **lis** 检验闭环 | **R-lis** |
| P3 | **pacs** 检查 + 处方 + 发药 | **R-pacs** / 完整门诊链 |
| P4 | ai-bridge + **hospital-ai**（FastAPI）CNN | **R-full**；**M7～M10** |
| P5 | Spring AI 分诊建议 + **Timefold** 排班 | `management` + `ai-bridge` |
| P6（可选） | `hospital-frontend` 迁 TypeScript | 医生/管理 PC 类型化 |

---

## 6. 附录

### 6.1 待补充图片清单

| 编号 | 描述 | 建议路径 |
|------|------|----------|
| 1 | 项目开发流程图 | `docs/images/dev-process.png` |
| 2 | 迭代开发示意图 | `docs/images/iteration.png` |
| 3 | 技术架构图 | [`docs/images/tech-architecture.png`](./images/tech-architecture.png)、[`MICROSERVICES.md`](./MICROSERVICES.md) §八 |
| 4 | 用例图 | `docs/images/use-case.png` |
| 5 | 整体/详细业务流程图 | `docs/images/biz-flow-*.png`；Mermaid 见 `docs/BUSINESS_FLOW.md` |
| 6 | Axure 原型截图 | `docs/images/prototype/*.jpeg` |
| 7 | ER 图、表设计大图 | `docs/images/er-diagram.png`（可据 `DATABASE_DESIGN.md` §二 绘制） |
| 8 | 各业务状态图 | `docs/images/state-*.png` |

### 6.2 参考文档中暂不纳入首期实现的内容

- 可行性研究中的投资/法律长篇论述（项目管理文档另行维护）
- 与原单体端口 8091/8092 强绑定的部署描述（改为 Nacos + Gateway）
- 无 AI 描述的纯重复段落（已合并进 AI 背景）

---

*文档版本：v1.6 | 对齐 `MICROSERVICES.md`：his/lis/pacs 三业务服务；分期 R-min～R-full。*
