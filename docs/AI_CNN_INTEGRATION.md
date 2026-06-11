# CT 金属伪影 CNN — 集成说明与变更清单

> **负责人**：wsh（CNN / hospital-ai）  
> **版本**：v1.0 | 2026-06-11  
> **依据**：`PROJECT_REQUIREMENTS.md` §0.1.5、`API.md` §六/§十一、`MICROSERVICES.md` §2.6

---

## 一、正式架构（未改团队约定）

```text
检查医生 PC（hospital-frontend :5173）
  → Gateway :9000
  → hospital-pacs :9104
       ├─ POST /pacs/imaging/upload
       ├─ POST /pacs/requests/{id}/ai-report
       ├─ GET  /pacs/imaging/preview/{id}/{kind}
       └─ POST /internal/imaging/callback  ← hospital-ai 内网回调

hospital-ai :8000（Python，仅 pacs 内网调用，不经 Gateway）
  → MinIO 读源影像 → CNN 推理 → 写 mask/预览 → HTTP 回调 pacs
```

**前端不直连 :8000**；6.8 个人工程的代码已**迁入** `NST-main`，不再外链 `6.8/` 文件夹。

---

## 二、从 6.8 迁入的代码映射

| 6.8 原路径 | NST-main 目标 |
|------------|---------------|
| `Detection/CTArtifactInfer.py` | `hospital-ai/app/inference/CTArtifactInfer.py` |
| `Utils/volume_loader.py` | `hospital-ai/app/inference/volume_loader.py` |
| `Model/AttentionUNet2D.py`、`UNet2D.py` | `hospital-ai/model/` |
| `Conf/Config.py` | `hospital-ai/model/Config.py` |
| `Model/weights/best.pth` | `hospital-ai/model/weights/best.pth`（不进 Git） |
| `frontend/src/components/MprViewer.vue` | `hospital-frontend/src/components/imaging/MprViewer.vue` |
| `frontend/src/App.vue` 交互逻辑 | `hospital-frontend/src/views/pacs/ImagingAiView.vue`（API 改为 pacs） |

---

## 三、新建文件

### 3.1 `hospital-ai/`（Python CNN 微服务）

| 文件 | 说明 |
|------|------|
| `app/main.py` | `/v1/health`、`/v1/inference/jobs` |
| `app/jobs.py` | 异步 job、MinIO 流水线、回调 pacs |
| `app/minio_client.py` | MinIO 读写 |
| `app/pacs_callback.py` | HTTP 回调 |
| `app/config.py` | 环境变量 |
| `app/inference/*` | CNN 推理（自 6.8 迁入） |
| `model/*` | 网络结构与权重目录 |
| `requirements.txt`、`.env.example` | 依赖与配置模板 |

### 3.2 `hospital-pacs`（Java 扩展）

| 文件 | 说明 |
|------|------|
| `config/MinioProperties.java` | MinIO 配置 |
| `config/HospitalAiProperties.java` | hospital-ai 地址与超时 |
| `config/PacsClientConfig.java` | RestTemplate Bean |
| `repository/ImagingStudyRepository.java` | `imaging_study` 表 |
| `service/MinioStorageService.java` | 上传/读取 MinIO |
| `service/ImagingCallbackRegistry.java` | 等待 CNN 回调 |
| `service/ImagingService.java` | 上传、ai-report、预览、回调落库 |
| `client/HospitalAiClient.java` | 调 `POST /v1/inference/jobs` |
| `controller/PacsImagingController.java` | 对外影像 API |
| `controller/InternalImagingController.java` | 内网回调 `/internal/imaging/callback` |

### 3.3 `hospital-frontend`

| 文件 | 说明 |
|------|------|
| `src/components/imaging/MprViewer.vue` | 自 6.8 完整三视图组件 |
| `src/views/pacs/ImagingAiView.vue` | 影像 AI 工作台（内嵌，走 pacs API） |

---

## 四、修改的既有团队文件

| 文件 | 变更说明 |
|------|----------|
| `hospital-backend/hospital-pacs/pom.xml` | 新增 `io.minio:minio:8.5.12` |
| `hospital-backend/hospital-pacs/.../application.yml` | `hospital.minio.*`、`hospital.ai.*`、multipart 512MB |
| `hospital-backend/hospital-pacs/.../CheckRequestRepository.java` | 新增 `findDetail()` |
| `hospital-backend/hospital-pacs/.../PacsController.java` | 移除 stub 上传，影像 API 迁至 `PacsImagingController` |
| `hospital-backend/hospital-pacs/.../PacsCheckService.java` | 移除 `imagingUploadStub()` |
| `hospital-backend/hospital-gateway/.../application.yml` | `codec.max-in-memory-size: 512MB`（大文件上传） |
| `hospital-frontend/package.json` | 新增 `@niivue/niivue@0.57.0` |
| `hospital-frontend/vite.config.js` | `host: 0.0.0.0`、代理 timeout 300s |
| `hospital-frontend/src/api/pacs.js` | 上传、ai-report、预览 blob；`savePacsResult` 改为 POST |
| `hospital-frontend/src/api/request.js` | blob 响应错误校验 |
| `hospital-frontend/src/config/integrations.js` | 影像工作台内嵌 `/pacs/imaging-ai`，取消外链跳转 |

**未改动的团队模块**：`hospital-auth`、`hospital-his`、`hospital-lis`、`hospital-disposal`、`hospital-management`、`hospital-gateway` 路由表（除 codec）、小程序端。

---

## 五、启动顺序

```text
1. PostgreSQL（hospital 库 + schema.sql + seed-dict.sql）
2. Nacos :8848
3. MinIO :9001
4. Java：auth → management → his → pacs → gateway
5. hospital-ai：
   cd hospital-ai && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
6. 演示检查单（队列非空，与 Mock #62001 对齐）：
   psql -U postgres -d hospital -f docs/sql/seed-demo-check.sql
   或：powershell -File scripts/seed-demo-check.ps1
7. 前端（**`VITE_USE_MOCK=false`**，改后需重启 `npm run dev`）：
   cd hospital-frontend && npm install && npm run dev
```

> **常见错误「检查申请不存在」**：前端仍为 `VITE_USE_MOCK=true` 时，队列是内存假数据（如 #62001），但影像上传走真实 pacs 库，ID 不存在即报错。请关 Mock 并执行 `seed-demo-check.sql`，或从医生开单→缴费走完整链路。

环境变量（pacs / hospital-ai 联调）：

```text
DB_PASSWORD=postgres
MINIO_ENDPOINT=http://127.0.0.1:9001
HOSPITAL_AI_BASE_URL=http://127.0.0.1:8000
```

---

## 六、验收步骤

1. `check01 / 123456` 登录 PC 前端  
2. 检查队列 → 开始执行 → **影像 AI 工作台**  
3. 选择 NIfTI 或 DICOM 文件夹（**选文件后自动上传源数据至 MinIO**，无需单独点上传）  
4. 点击 **开始 AI 检测**（约 15–60 秒；掩码与预览由 hospital-ai 自动写入 MinIO）  
5. 查看 AI 报告 + Niivue 三视图  
6. 返回队列录入 `resultText`

---

## 七、API 契约（pacs 对外）

| Method | 路径 | 说明 |
|--------|------|------|
| POST | `/api/v1/pacs/imaging/upload` | multipart：`checkRequestId` + `files[]` |
| POST | `/api/v1/pacs/requests/{id}/ai-report` | 触发 CNN，阻塞等待回调（最长 180s） |
| GET | `/api/v1/pacs/requests/{id}/result-detail` | 报告详情 |
| GET | `/api/v1/pacs/requests/{id}/imaging-preview` | 预览元数据 |
| GET | `/api/v1/pacs/imaging/preview/{id}/ct\|mask` | NIfTI 流 |
| GET | `/api/v1/pacs/imaging-studies` | 影像任务列表；支持 `?patientId=`、`?medicalRecordNo=` 按患者筛选 |

**跨模块按患者查影像**：详见 **[IMAGING_DATA_ACCESS.md](./IMAGING_DATA_ACCESS.md)**（MinIO 路径含义、`patient_id` → `checkRequestId` → 报告/文件）。

hospital-ai 内网（pacs 调用）：

| Method | 路径 |
|--------|------|
| POST | `/v1/inference/jobs` |
| GET | `/v1/health` |

回调：`POST http://127.0.0.1:9104/internal/imaging/callback`
