# 窗口挂号与收费后端设计规格

> **版本**：v2.0 | 2026-06-09  
> **状态**：已定稿（brainstorming 确认）  
> **模块**：`hospital-his` · `controller.registrar` / `service` / `repository`  
> **关联契约**：[API.md](../../API.md) §八、[DATABASE_DESIGN.md](../../DATABASE_DESIGN.md) §5.1 / §1.5  
> **替代**：v1.1 一步式窗口挂号方案（register + pay 同事务）

---

## 一、背景与目标

PC 端已有「窗口挂号」页（`RegisterView.vue`）与「窗口收费」页（`ChargeView.vue`）。前端 Mock 与页面流程为**两步**：

1. **窗口挂号**：录入患者信息、选排班 → 生成待支付挂号单  
2. **窗口收费**：按病历号查待缴账单 → 批量缴费

v1.1 后端曾实现「一步完成」（`POST /registrar/registers` 当场支付，`visit_state=1`），与前端 Mock / `ChargeView` 不一致。本 spec 将后端对齐 API.md 与前端两页流程。

**目标**：

1. `POST /registrar/registers`：患者建档或匹配 + 占号 + 生成待支付账单，`visit_state=0`  
2. `POST /registrar/charges`：窗口统一收费（挂号费、病历本、及后续检验/处方/处置等）  
3. 支付完成后，`REGISTER` 账单付清 → `visit_state=1`

**不在本次范围**：

- 真实微信/支付宝 SDK 验单（P4）  
- 现金找零（`ChargeView` 暂无 `receivedAmount` 字段；后续可加）  
- PC 前端大改（`RegisterView` / `ChargeView` 已对齐 Mock，后端对接即可）

---

## 二、业务决策（已定稿）

| 决策 | 选择 |
|------|------|
| 架构 | **方案 1**：`RegistrarRegisterService` + `RegistrarChargeService` 分离；结算逻辑从 `PaymentService` 抽取复用 |
| 收费时机 | **两步**：挂号 `visit_state=0`；收费页 `POST /charges` 后进入已挂号 |
| 患者识别 | **策略 B**：身份证或手机号至少填一项；两者都有时须指向同一 patient |
| 病历本 | 勾选 `needRecordBook` 时生成 **2 条待支付 bill**：`REGISTER` + `MEDICAL_BOOK`（¥1） |
| 窗口支付渠道 | **多渠道记账**（对齐 `ChargeView`）：`CASH` / `WECHAT` / `ALIPAY` / `INSURANCE` / `SCAN`；开发期均记账 |
| 号源占用 | 挂号时占用（与患者在线挂号一致）；待支付退号释放号源 |
| `need_medical_book` | **病历本 bill 付清时**更新 `patient.need_medical_book = true`（非挂号时） |

---

## 三、API 契约

### 3.1 `POST /registrar/registers`（改）

| 项 | 值 |
|----|-----|
| Method | `POST` |
| Path | `/api/v1/registrar/registers` |
| 服务 | `hospital-his` :9102 |
| 角色 | `REGISTRAR` 或 `ADMIN` |

**Request Body — `WindowRegisterRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `patientName` | string | 是 | 姓名 |
| `gender` | int | 否 | 1 男 / 2 女 |
| `birthDate` | date | 否 | `yyyy-MM-dd` |
| `age` | int | 否 | 可选 |
| `idCard` | string | 条件 | 与 `phone` **至少一项** |
| `phone` | string | 条件 | 与 `idCard` **至少一项** |
| `address` | string | 否 | 住址 |
| `settleCategoryId` | long | 否 | 默认 `1`（自费） |
| `needRecordBook` | boolean | 否 | 默认 `false`；`true` 时额外生成 `MEDICAL_BOOK` bill（¥1） |
| `schedulingId` | long | 是 | 排班 ID |
| `deptId` | long | 是 | 须与排班一致 |
| `employeeId` | long | 是 | 须与排班一致 |
| `registLevelId` | long | 是 | 须与排班一致 |

> **v2 删除字段**：`payChannel`、`receivedAmount`、`tradeNo`（支付移至 `/charges`）

**示例**：

```json
{
  "patientName": "张三",
  "gender": 1,
  "phone": "13800138000",
  "idCard": "110101199001011234",
  "settleCategoryId": 1,
  "needRecordBook": true,
  "schedulingId": 1,
  "deptId": 1,
  "employeeId": 1,
  "registLevelId": 1
}
```

**Response `data`**

| 字段 | 说明 |
|------|------|
| `registerId` | 挂号 ID |
| `patientId` | 患者 ID |
| `medicalRecordNo` | 病历号（收费页查询用） |
| `billIds` | 本次待支付账单 ID 列表（1～2 条） |
| `amount` | 应付合计（挂号费 + 可选病历本 ¥1） |
| `visitState` | 固定 **`0`（待支付）** |
| `deptName`, `doctorName`, `workDate`, `noonLabel`, `registLevelName` | 展示用 |
| `message` | 如「挂号成功，请至收费窗口缴纳挂号费后进入「已挂号」状态」 |

> 无 `paymentId`、`receivedAmount`、`changeAmount`。

---

### 3.2 `POST /registrar/charges`（新）

| 项 | 值 |
|----|-----|
| Method | `POST` |
| Path | `/api/v1/registrar/charges` |
| 角色 | `REGISTRAR` 或 `ADMIN` |

**Request Body — `WindowChargeRequest`**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `billIds` | long[] | 是 | 待支付账单 ID；可批量（同一患者） |
| `payChannel` | string | 是 | `CASH` / `WECHAT` / `ALIPAY` / `INSURANCE` / `SCAN` |

**示例**（对齐 `ChargeView` / `registrar.js`）：

```json
{
  "billIds": [81001, 81002],
  "payChannel": "CASH"
}
```

**Response `data`**

| 字段 | 说明 |
|------|------|
| `paymentId` | 支付记录 ID |
| `paidAmount` | 本次实收合计 |
| `message` | 如「收费成功，实收 ¥21.00」 |

---

### 3.3 已有读 API（不变）

| API | 用途 |
|-----|------|
| `GET /registrar/patients/{medicalRecordNo}/bills?status=0` | 收费页查待缴 |
| `GET /registrar/departments` 等 | 挂号页字典/排班 |

**账单列表字段**：后端返回 `billTitle`；前端 `ChargeView` 使用 `itemName` 时可做映射（`row.billTitle ?? row.itemName`）。

---

### 3.4 API.md 回写要点

- `POST /registrar/registers`：说明改为两步式，`visit_state=0`  
- `POST /registrar/charges`：⬜ → ✅，补充 Request/Response  
- 删除 registers 示例中的支付字段

---

## 四、数据模型与字段语义

### 4.1 `register` 表

| 字段 | 窗口挂号取值 |
|------|--------------|
| `channel` | **`WINDOW`** |
| `visit_state` | **`0`（待支付）**；`REGISTER` bill 付清后 → **`1`（已挂号）** |
| `registrar_id` | 当前 JWT 收费员 `employeeId`（挂号时写入） |

### 4.2 `bill` 表

| 场景 | biz_type | bill_title | amount | status（挂号后） |
|------|----------|------------|--------|------------------|
| 挂号费 | `REGISTER` | `挂号费` | 排班 `regist_fee` | **`0` 待支付** |
| 病历本 | `MEDICAL_BOOK` | `病历本` | `1.00` | **`0` 待支付**（仅 `needRecordBook=true`） |

收费成功后：`status → 1`，写 `paid_time`。

### 4.3 `payment_record` 表（收费时写入）

| 字段 | 取值 |
|------|------|
| `patient_id` | 账单所属患者 |
| `total_amount` | 本次勾选账单合计 |
| `channel` | 请求 `payChannel` |
| `status` | `1`（记账成功） |
| `pay_time` | 当前时间 |
| `operator_id` | 收费员 `employeeId` |
| `third_party_trade_no` | 可选，首期不传 |

### 4.4 `patient` 表

- 建档/更新：`real_name`, `gender`, `birth_date`, `phone`, `id_card`, `address`, `settle_category_id`（策略 B）  
- `need_medical_book = true`：**`MEDICAL_BOOK` bill 付清时**更新

---

## 五、事务流程

### 5.1 挂号 `RegistrarRegisterService.windowRegister`

单方法 `@Transactional`：

```
1. requireRegistrar() → registrarId
2. validateRequest() → idCard/phone 至少一项（upsertForWindow）
3. resolvePatient() → upsertForWindow(...)
4. schedulingRepository.findByIdForUpdate(schedulingId)
5. 校验 remain > 0；deptId/employeeId/registLevelId 与排班一致
6. registerRepository.insertRegister(..., channel=WINDOW, visitState=0, registrarId)
7. schedulingRepository.incrementUsedQuota(schedulingId)
8. billRepository.insertBill(REGISTER, ..., status=0)
9. [optional] billRepository.insertBill(MEDICAL_BOOK, ...) if needRecordBook
10. 组装响应（无 payment_record）
```

**失败回滚**：任一步失败则整笔回滚（含号源、register、bill）。

### 5.2 收费 `RegistrarChargeService.windowCharge`

单方法 `@Transactional`：

```
1. requireRegistrar() → operatorId
2. 校验 billIds 非空；payChannel 合法（PaymentChannel.isRegistrarChargeAllowed）
3. billRepository.findByIds(billIds) → 全部存在、status=0、同一 patientId
4. totalAmount = sum(amount)
5. paymentRepository.insertPayment(patientId, totalAmount, payChannel, operatorId, null)
6. 对每个 bill：linkBill → markPaid → settlePaidBill(bill)
   - REGISTER → register.visit_state = 1
   - MEDICAL_BOOK → patient.need_medical_book = true
   - INSPECTION/CHECK/PRESCRIPTION/DISPOSAL → 同 PaymentService.mockPay
7. 返回 paymentId, paidAmount, message
```

`settlePaidBill(bill)` 从 `PaymentService.mockPay` 抽取，供患者端与窗口端共用。

### 5.3 待支付退号（增强 `RegisterCancelService`）

| visit_state | 行为 |
|-------------|------|
| `0` 待支付 | 关联待支付 bills → `status=9`（VOID）；`visit_state → 4`（CANCELLED）；`decrementUsedQuota` |
| `1` 已挂号 | 保持现有逻辑（退费 + 退号） |

患者端 `POST /patient/registers/{id}/cancel` 同步支持 `visit_state=0`。

---

## 六、患者建档（策略 B）

复用 `PatientLoginPersistence.upsertForWindow(...)`：

| 输入 | 行为 |
|------|------|
| 仅 `phone` | 按手机号查找；无则新建 |
| 仅 `idCard` | 按身份证查找；无则新建 |
| 两者都有 | 分别查找；若都存在且 ID 不同 → 400；否则合并更新 |
| 两者都无 | **400**「请填写身份证号或手机号」 |

---

## 七、支付渠道

### 7.1 窗口收费允许渠道

`CASH` · `WECHAT` · `ALIPAY` · `INSURANCE` · `SCAN`

```java
PaymentChannel.isRegistrarChargeAllowed(channel)
```

### 7.2 与小程序在线支付的关系

- 小程序：`POST /patient/payments`，`payment_record.channel = WECHAT`（mock）  
- 窗口：`POST /registrar/charges`，`payChannel` 写入 `payment_record.channel`  
- 渠道值可复用，业务入口不同；开发期均为记账，不调用第三方支付 SDK

### 7.3 首期不做

- 现金找零（`receivedAmount` / `changeAmount`）  
- `tradeNo` 验单

---

## 八、代码结构

### 8.1 新增

| 文件 | 职责 |
|------|------|
| `dto/registrar/WindowChargeRequest.java` | 收费请求 DTO |
| `service/RegistrarChargeService.java` | 窗口收费编排 |

### 8.2 修改

| 文件 | 变更 |
|------|------|
| `RegistrarRegisterService.java` | 去掉支付；`visit_state=0` |
| `WindowRegisterRequest.java` | 删除支付字段 |
| `RegistrarController.java` | `POST /charges` |
| `PaymentChannel.java` | + `ALIPAY`/`INSURANCE`；`isRegistrarChargeAllowed()` |
| `PaymentService.java` | 抽取 `settlePaidBill(bill)` |
| `RegisterCancelService.java` | 支持 `visit_state=0` 退号 |
| `BillRepository.java` | 可选 `markVoid(billId)` |
| `docs/API.md` | §八 更新 |
| `docs/PROGRESS.md` | 状态更新 |

### 8.3 不修改

- `RegisterService`（患者在线挂号）  
- `RegistrarQueryService`（读 API）  
- Gateway 路由  
- 前端 `RegisterView` / `ChargeView`（已对齐 Mock）

---

## 九、错误码

**`POST /registrar/registers`**

| HTTP | code | 场景 |
|------|------|------|
| 403 | FORBIDDEN | 非 REGISTRAR/ADMIN |
| 400 | BAD_REQUEST | 身份证/手机均未填；手机格式/冲突；身份证手机不匹配；号源已满；排班不一致 |
| 404 | NOT_FOUND | 排班不存在或 `publish_status≠1` |

**`POST /registrar/charges`**

| HTTP | code | 场景 |
|------|------|------|
| 403 | FORBIDDEN | 非 REGISTRAR/ADMIN |
| 400 | BAD_REQUEST | `billIds` 为空；账单不存在；非待支付；跨患者混选；`payChannel` 非法 |

---

## 十、与现有实现对比

| | v1.1（一步式，待回改） | v2（本 spec） |
|--|------------------------|---------------|
| registers | 挂号 + 支付同事务 | 仅挂号 + 待支付 bills |
| visit_state | 1 | 0 → charges 后 1 |
| 支付字段 | 在 registers Request | 在 charges Request |
| 窗口渠道 | CASH/SCAN only | 多渠道（对齐前端） |
| need_medical_book | 挂号时设置 | 病历本 bill 付清时设置 |

| | 在线 `RegisterService` | 窗口 `RegistrarRegisterService` |
|--|------------------------|----------------------------------|
| 鉴权 | 患者 JWT | 收费员 STAFF JWT |
| visit_state | 0 | 0 |
| channel | ONLINE | WINDOW |
| 支付 | `POST /patient/payments` | `POST /registrar/charges` |

---

## 十一、验收标准

| # | 场景 | 断言 |
|---|------|------|
| R1 | 窗口挂号 | `visitState=0`；`REGISTER` bill `status=0`；无 `paymentId` |
| R2 | `needRecordBook=true` | 2 条待支付 bill；`amount = registFee + 1` |
| R3 | `GET .../bills?status=0` | 返回上述 bills |
| R4 | `POST /charges` CASH | bills `status=1`；`visitState=1`；`payment_record` 存在 |
| R5 | 批量 charges | 挂号费 + 病历本一次付清 |
| R6 | 医生队列 | R4 后 `GET /doctor/queues` 可见 |
| R7 | 待支付退号 | `visit_state=0` cancel → 号源释放、bills VOID |
| R8 | 已挂号退号 | 现有 cancel + refund 不变 |

**数据库断言（R4 后）**：

```sql
SELECT channel, visit_state, registrar_id FROM register WHERE id = :registerId;
-- WINDOW, 1, 非空

SELECT status, biz_type FROM bill WHERE register_id = :registerId;
-- 全部 status=1

SELECT channel, status FROM payment_record WHERE id = :paymentId;
-- CASH|WECHAT|..., 1
```

---

## 十二、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-09 | 初稿：一步完成、策略 B、CASH/SCAN 记账 |
| v1.1 | 2026-06-09 | 窗口支付渠道收窄为 CASH/SCAN；WECHAT 仅限小程序 |
| v2.0 | 2026-06-09 | **挂号/收费分离**；对齐前端 Mock；窗口多渠道；charges 新 API；待支付退号 |
