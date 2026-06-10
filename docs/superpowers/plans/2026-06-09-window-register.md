# 窗口挂号与收费（v2 分离式）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将窗口挂号改为两步式：`POST /registrar/registers` 仅建档+占号+待支付 bills（`visit_state=0`）；新增 `POST /registrar/charges` 窗口统一收费，对齐前端 Mock 与 `ChargeView`。

**Architecture:** 回改 `RegistrarRegisterService` 去掉支付；新增 `RegistrarChargeService`；从 `PaymentService.mockPay` 抽取 `settlePaidBill(bill)` 复用结算副作用；扩展 `RegisterCancelService` 支持待支付退号；`PaymentChannel` 扩展多渠道。

**Tech Stack:** Java 17 · Spring Boot 3.2 · JdbcClient · Jakarta Validation · PostgreSQL · Gateway :9000

**Spec:** [docs/superpowers/specs/2026-06-09-window-register-design.md](../specs/2026-06-09-window-register-design.md) v2.0

---

## File Map

| 操作 | 路径 | 职责 |
|------|------|------|
| Modify | `hospital-common/.../PaymentChannel.java` | +ALIPAY/INSURANCE；`isRegistrarChargeAllowed()` |
| Modify | `hospital-his/.../BillRepository.java` | +`markVoid` + `findPendingByRegisterId` |
| Modify | `hospital-his/.../PaymentService.java` | 抽取 `settlePaidBill(bill)` |
| Modify | `hospital-his/.../RegistrarRegisterService.java` | 去掉支付；`visit_state=0` |
| Modify | `hospital-his/.../dto/registrar/WindowRegisterRequest.java` | 删除支付字段 |
| Create | `hospital-his/.../dto/registrar/WindowChargeRequest.java` | 收费 DTO |
| Create | `hospital-his/.../service/RegistrarChargeService.java` | 窗口收费 |
| Modify | `hospital-his/.../controller/registrar/RegistrarController.java` | `POST /charges` |
| Modify | `hospital-his/.../RegisterCancelService.java` | 待支付退号 |
| Modify | `scripts/r-registrar-register.ps1` | v2 验收（register + charge 两步） |
| Modify | `docs/API.md` | §八 更新 |
| Modify | `docs/PROGRESS.md` | 状态更新 |

---

### Task 1: 扩展 `PaymentChannel`

**Files:**
- Modify: `hospital-backend/hospital-common/src/main/java/com/hospital/common/constant/PaymentChannel.java`

- [ ] **Step 1: 替换文件内容**

```java
package com.hospital.common.constant;

import java.util.Locale;
import java.util.Set;

public final class PaymentChannel {

    public static final String CASH = "CASH";
    public static final String WECHAT = "WECHAT";
    public static final String ALIPAY = "ALIPAY";
    public static final String INSURANCE = "INSURANCE";
    public static final String SCAN = "SCAN";

    private static final Set<String> REGISTRAR_CHARGE_ALLOWED = Set.of(
            CASH, WECHAT, ALIPAY, INSURANCE, SCAN);

    private PaymentChannel() {
    }

    /** 窗口收费页允许渠道（对齐 ChargeView 下拉） */
    public static boolean isRegistrarChargeAllowed(String channel) {
        if (channel == null) {
            return false;
        }
        return REGISTRAR_CHARGE_ALLOWED.contains(channel.trim().toUpperCase(Locale.ROOT));
    }
}
```

- [ ] **Step 2: 编译 common**

Run: `cd hospital-backend; mvn -q -pl hospital-common -am compile -DskipTests`  
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 全局搜索 `isWindowAllowed`**

Run: `rg "isWindowAllowed" hospital-backend`  
Expected: 仅 `RegistrarRegisterService`（Task 4 会删除引用）

---

### Task 2: `BillRepository` 作废与按挂号查待支付

**Files:**
- Modify: `hospital-backend/hospital-his/src/main/java/com/hospital/his/repository/BillRepository.java`

- [ ] **Step 1: 在类末尾、`mapBillRow` 之前追加方法**

```java
public List<Map<String, Object>> findPendingByRegisterId(Long registerId) {
    return jdbcClient.sql("""
                    SELECT id, patient_id, register_id, biz_type, biz_id, bill_title, amount, status
                    FROM bill
                    WHERE register_id = :registerId AND status = 0
                    ORDER BY id
                    """)
            .param("registerId", registerId)
            .query((rs, rowNum) -> mapBillRow(rs))
            .list();
}

public void markVoid(Long billId) {
    jdbcClient.sql("""
                    UPDATE bill SET status = 9, update_time = NOW()
                    WHERE id = :id AND status = 0
                    """)
            .param("id", billId)
            .update();
}
```

- [ ] **Step 2: 编译 his**

Run: `cd hospital-backend; mvn -q -pl hospital-his -am compile -DskipTests`  
Expected: `BUILD SUCCESS`

---

### Task 3: 抽取 `PaymentService.settlePaidBill`

**Files:**
- Modify: `hospital-backend/hospital-his/src/main/java/com/hospital/his/service/PaymentService.java`

- [ ] **Step 1: 注入 `PatientRepository`**

在类字段区增加：

```java
private final PatientRepository patientRepository;
```

- [ ] **Step 2: 将 `mockPay` 中 for-loop 内 bizType 分支替换为调用 `settlePaidBill`**

`mockPay` 循环体改为：

```java
for (Map<String, Object> bill : bills) {
    long billId = ((Number) bill.get("id")).longValue();
    paymentRepository.linkBill(paymentId, billId, (BigDecimal) bill.get("amount"));
    billRepository.markPaid(billId);
    settlePaidBill(bill);
}
```

- [ ] **Step 3: 新增 public 方法**

```java
public void settlePaidBill(Map<String, Object> bill) {
    String bizType = (String) bill.get("bizType");
    long bizId = ((Number) bill.get("bizId")).longValue();

    if (BillBizType.REGISTER.equals(bizType)) {
        registerRepository.updateVisitState(bizId, VisitState.REGISTERED);
    } else if (BillBizType.MEDICAL_BOOK.equals(bizType)) {
        long patientId = ((Number) bill.get("patientId")).longValue();
        patientRepository.updateNeedMedicalBook(patientId, true);
    } else if (BillBizType.INSPECTION.equals(bizType)) {
        inspectionRequestRepository.updateStatus(bizId, InspectionRequestStatus.PAID);
    } else if (BillBizType.CHECK.equals(bizType)) {
        checkRequestRepository.updateStatus(bizId, InspectionRequestStatus.PAID);
    } else if (BillBizType.PRESCRIPTION.equals(bizType)) {
        prescriptionRepository.updateStatus(bizId, PrescriptionStatus.PAID);
    } else if (BillBizType.DISPOSAL.equals(bizType)) {
        disposalRequestRepository.updateStatus(bizId, InspectionRequestStatus.PAID);
    }
}
```

- [ ] **Step 4: 添加 import**

```java
import com.hospital.his.repository.PatientRepository;
```

- [ ] **Step 5: 编译**

Run: `cd hospital-backend; mvn -q -pl hospital-his -am compile -DskipTests`  
Expected: `BUILD SUCCESS`

---

### Task 4: 回改 `RegistrarRegisterService`（仅挂号）

**Files:**
- Modify: `hospital-backend/hospital-his/src/main/java/com/hospital/his/service/RegistrarRegisterService.java`
- Modify: `hospital-backend/hospital-his/src/main/java/com/hospital/his/dto/registrar/WindowRegisterRequest.java`

- [ ] **Step 1: `WindowRegisterRequest` 删除支付字段**

删除 `payChannel`、`receivedAmount`、`tradeNo` 及 `import java.math.BigDecimal;`（若不再使用）。

- [ ] **Step 2: 替换 `RegistrarRegisterService` 全文**

```java
package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.RegisterChannel;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.registrar.WindowRegisterRequest;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.repository.SchedulingRepository;
import com.hospital.his.security.AuthContext;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrarRegisterService {

    private static final BigDecimal RECORD_BOOK_FEE = new BigDecimal("1.00");

    private final PatientLoginPersistence patientLoginPersistence;
    private final PatientRepository patientRepository;
    private final SchedulingRepository schedulingRepository;
    private final RegisterRepository registerRepository;
    private final BillRepository billRepository;

    @Transactional
    public Map<String, Object> windowRegister(WindowRegisterRequest request) {
        Long registrarId = requireRegistrar();

        Long settleCategoryId = request.getSettleCategoryId() != null ? request.getSettleCategoryId() : 1L;
        PatientLoginPersistence.UpsertResult upsert = patientLoginPersistence.upsertForWindow(
                request.getPatientName(),
                request.getGender(),
                request.getBirthDate(),
                request.getPhone(),
                request.getIdCard(),
                request.getAddress(),
                settleCategoryId);
        Long patientId = upsert.patientId();

        Map<String, Object> scheduling = schedulingRepository.findByIdForUpdate(request.getSchedulingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班不存在或已停诊"));

        int remain = toInt(scheduling.get("totalQuota")) - toInt(scheduling.get("usedQuota"));
        if (remain <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "号源已满");
        }

        if (!equalsLong(scheduling.get("deptId"), request.getDeptId())
                || !equalsLong(scheduling.get("employeeId"), request.getEmployeeId())
                || !equalsLong(scheduling.get("registLevelId"), request.getRegistLevelId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排班信息与请求不一致");
        }

        BigDecimal registFee = (BigDecimal) scheduling.get("registFee");
        LocalDate visitDate = (LocalDate) scheduling.get("workDate");
        int noonType = toInt(scheduling.get("noonType"));
        boolean needRecordBook = Boolean.TRUE.equals(request.getNeedRecordBook());

        long registerId = registerRepository.insertRegister(
                patientId,
                request.getSchedulingId(),
                request.getDeptId(),
                request.getEmployeeId(),
                request.getRegistLevelId(),
                settleCategoryId,
                visitDate,
                noonType,
                VisitState.PENDING_PAYMENT,
                registFee,
                RegisterChannel.WINDOW,
                registrarId);

        schedulingRepository.incrementUsedQuota(request.getSchedulingId());

        List<Long> billIds = new ArrayList<>();
        billIds.add(billRepository.insertBill(
                patientId, registerId, BillBizType.REGISTER, registerId, "挂号费", registFee));

        BigDecimal totalAmount = registFee;
        if (needRecordBook) {
            billIds.add(billRepository.insertBill(
                    patientId, registerId, BillBizType.MEDICAL_BOOK, registerId, "病历本", RECORD_BOOK_FEE));
            totalAmount = totalAmount.add(RECORD_BOOK_FEE);
        }

        String medicalRecordNo = patientRepository.findMedicalRecordNo(patientId);

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", registerId);
        result.put("patientId", patientId);
        result.put("medicalRecordNo", medicalRecordNo);
        result.put("billIds", billIds);
        result.put("amount", totalAmount);
        result.put("visitState", VisitState.PENDING_PAYMENT);
        result.put("deptName", scheduling.get("deptName"));
        result.put("doctorName", scheduling.get("doctorName"));
        result.put("workDate", visitDate);
        result.put("noonLabel", noonType == 1 ? "上午" : "下午");
        result.put("registLevelName", scheduling.get("levelName"));
        result.put("message", "挂号成功，请至收费窗口缴纳挂号费后进入「已挂号」状态");
        return result;
    }

    private Long requireRegistrar() {
        AuthContext context = AuthContextHolder.require();
        if (!context.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要收费员身份");
        }
        List<String> roles = context.getRoles();
        if (roles == null || (!roles.contains("REGISTRAR") && !roles.contains("ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要 REGISTRAR 角色");
        }
        return context.getEmployeeId();
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private boolean equalsLong(Object left, Long right) {
        if (left == null || right == null) {
            return false;
        }
        return ((Number) left).longValue() == right;
    }
}
```

- [ ] **Step 3: 编译**

Run: `cd hospital-backend; mvn -q -pl hospital-his -am compile -DskipTests`  
Expected: `BUILD SUCCESS`

---

### Task 5: 新增 `WindowChargeRequest` + `RegistrarChargeService`

**Files:**
- Create: `hospital-backend/hospital-his/src/main/java/com/hospital/his/dto/registrar/WindowChargeRequest.java`
- Create: `hospital-backend/hospital-his/src/main/java/com/hospital/his/service/RegistrarChargeService.java`

- [ ] **Step 1: 创建 DTO**

```java
package com.hospital.his.dto.registrar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WindowChargeRequest {

    @NotEmpty(message = "billIds 不能为空")
    private List<Long> billIds;

    @NotBlank(message = "payChannel 不能为空")
    private String payChannel;
}
```

- [ ] **Step 2: 创建 `RegistrarChargeService`**

```java
package com.hospital.his.service;

import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.PaymentChannel;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.registrar.WindowChargeRequest;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.PaymentRepository;
import com.hospital.his.security.AuthContext;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistrarChargeService {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Transactional
    public Map<String, Object> windowCharge(WindowChargeRequest request) {
        Long operatorId = requireRegistrar();
        List<Long> billIds = request.getBillIds();
        if (billIds == null || billIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "billIds 不能为空");
        }

        String payChannel = normalizePayChannel(request.getPayChannel());
        List<Map<String, Object>> bills = billRepository.findByIds(billIds);
        if (bills.size() != billIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账单不存在");
        }

        Set<Long> patientIds = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map<String, Object> bill : bills) {
            if (((Number) bill.get("status")).intValue() != BillStatus.PENDING) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在非待支付账单");
            }
            patientIds.add(((Number) bill.get("patientId")).longValue());
            totalAmount = totalAmount.add((BigDecimal) bill.get("amount"));
        }
        if (patientIds.size() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不可跨患者合并收费");
        }
        Long patientId = patientIds.iterator().next();

        long paymentId = paymentRepository.insertPayment(patientId, totalAmount, payChannel, operatorId, null);

        for (Map<String, Object> bill : bills) {
            long billId = ((Number) bill.get("id")).longValue();
            paymentRepository.linkBill(paymentId, billId, (BigDecimal) bill.get("amount"));
            billRepository.markPaid(billId);
            paymentService.settlePaidBill(bill);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", paymentId);
        result.put("paidAmount", totalAmount);
        result.put("message", String.format("收费成功，实收 ¥%.2f", totalAmount));
        return result;
    }

    private String normalizePayChannel(String payChannel) {
        if (!StringUtils.hasText(payChannel)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "payChannel 不能为空");
        }
        String channel = payChannel.trim().toUpperCase(Locale.ROOT);
        if (!PaymentChannel.isRegistrarChargeAllowed(channel)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的支付方式: " + payChannel);
        }
        return channel;
    }

    private Long requireRegistrar() {
        AuthContext context = AuthContextHolder.require();
        if (!context.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要收费员身份");
        }
        List<String> roles = context.getRoles();
        if (roles == null || (!roles.contains("REGISTRAR") && !roles.contains("ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要 REGISTRAR 角色");
        }
        return context.getEmployeeId();
    }
}
```

- [ ] **Step 3: 编译**

Run: `cd hospital-backend; mvn -q -pl hospital-his -am compile -DskipTests`  
Expected: `BUILD SUCCESS`

---

### Task 6: Controller 暴露 `POST /charges`

**Files:**
- Modify: `hospital-backend/hospital-his/src/main/java/com/hospital/his/controller/registrar/RegistrarController.java`

- [ ] **Step 1: 注入 `RegistrarChargeService` 并添加端点**

```java
import com.hospital.his.dto.registrar.WindowChargeRequest;
import com.hospital.his.service.RegistrarChargeService;

// 字段
private final RegistrarChargeService registrarChargeService;

// 方法
@PostMapping("/charges")
public Result<Map<String, Object>> windowCharge(@Valid @RequestBody WindowChargeRequest request) {
    return Result.success(registrarChargeService.windowCharge(request));
}
```

- [ ] **Step 2: 编译**

Run: `cd hospital-backend; mvn -q -pl hospital-his -am compile -DskipTests`  
Expected: `BUILD SUCCESS`

---

### Task 7: 待支付退号 `RegisterCancelService`

**Files:**
- Modify: `hospital-backend/hospital-his/src/main/java/com/hospital/his/service/RegisterCancelService.java`

- [ ] **Step 1: 注入依赖**

```java
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.SchedulingRepository;

private final BillRepository billRepository;
private final SchedulingRepository schedulingRepository;
```

- [ ] **Step 2: 修改 `cancelByPatient` 分支**

将 `visit_state != REGISTERED` 的校验改为：

```java
int visitState = ((Number) register.get("visitState")).intValue();
if (visitState == VisitState.PENDING_PAYMENT) {
    return cancelPendingRegister(registerId, reason);
}
if (visitState != VisitState.REGISTERED) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号未接诊状态可退号");
}
return doCancel(registerId, reason, null);
```

- [ ] **Step 3: 修改 `cancelByRegistrar` 分支**

```java
int visitState = ((Number) register.get("visitState")).intValue();
if (visitState == VisitState.PENDING_PAYMENT) {
    return cancelPendingRegister(registerId, reason);
}
if (visitState != VisitState.REGISTERED) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号未接诊状态可退号");
}
return doCancel(registerId, reason, AuthContextHolder.require().getEmployeeId());
```

- [ ] **Step 4: 新增私有方法**

```java
private Map<String, Object> cancelPendingRegister(Long registerId, String reason) {
    Map<String, Object> register = registerRepository.findByIdForUpdate(registerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));

    for (Map<String, Object> bill : billRepository.findPendingByRegisterId(registerId)) {
        billRepository.markVoid(((Number) bill.get("id")).longValue());
    }

    registerRepository.updateVisitState(registerId, VisitState.CANCELLED);

    Object schedulingId = register.get("schedulingId");
    if (schedulingId != null) {
        schedulingRepository.decrementUsedQuota(((Number) schedulingId).longValue());
    }

    Map<String, Object> result = new HashMap<>();
    result.put("registerId", registerId);
    result.put("visitState", VisitState.CANCELLED);
    result.put("message", reason != null ? reason : "待支付挂号已取消");
    return result;
}
```

- [ ] **Step 5: 编译**

Run: `cd hospital-backend; mvn -q -pl hospital-his -am compile -DskipTests`  
Expected: `BUILD SUCCESS`

---

### Task 8: 验收脚本 v2

**Files:**
- Modify: `scripts/r-registrar-register.ps1`

- [ ] **Step 1: 重写脚本为 register + charge 两步**

核心场景（替换原 R2–R7）：

| # | 场景 | 断言 |
|---|------|------|
| R1 | registrar 登录 | token + REGISTRAR |
| R2 | 窗口挂号（无支付字段） | `visitState=0`；无 `paymentId` |
| R3 | 按病历号查待缴 | `GET .../bills?status=0` 含 REGISTER bill |
| R4 | `POST /charges` CASH | `visitState` 经 charge 后变 1；`paymentId` 存在 |
| R5 | `needRecordBook=true` | 2 条 bill；批量 charge 后 `amount=registFee+1` |
| R6 | 无 phone/idCard | 400 |
| R7 | 医生队列 | R4 的 registerId 出现在 `visitState=1` 队列 |
| R8 | 待支付退号 | 新挂号 `visitState=0` → cancel → `visitState=4` |

R2 body 示例（**无** payChannel）：

```powershell
$r2Body = @{
    patientName   = '窗口测试'
    phone         = $randPhone
    gender        = 1
    schedulingId  = $sched.schedulingId
    deptId        = $sched.deptId
    employeeId    = $sched.employeeId
    registLevelId = $sched.registLevelId
} | ConvertTo-Json
$r2 = Invoke-RestMethod -Uri "$base/registrar/registers" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body $r2Body
Test-Step 'R2 window register pending' ($r2.code -eq 200 -and $r2.data.visitState -eq 0 -and -not $r2.data.paymentId) ($r2 | ConvertTo-Json -Compress)
$mrn = $r2.data.medicalRecordNo
$billIds = @($r2.data.billIds)
```

R4 charge：

```powershell
$r4 = Invoke-RestMethod -Uri "$base/registrar/charges" -Method POST -Headers $registrarHeaders -ContentType 'application/json' -Body (@{ billIds = $billIds; payChannel = 'CASH' } | ConvertTo-Json)
Test-Step 'R4 window charge CASH' ($r4.code -eq 200 -and $r4.data.paymentId) ($r4 | ConvertTo-Json -Compress)
```

- [ ] **Step 2: 运行脚本（需 Gateway + PG + his 已启）**

Run: `.\scripts\r-registrar-register.ps1`  
Expected: 全部 `[PASS]`

---

### Task 9: 文档更新

**Files:**
- Modify: `docs/API.md`
- Modify: `docs/PROGRESS.md`

- [ ] **Step 1: API.md §八**

- 总表：`POST /registrar/registers` 说明改为「仅挂号，`visit_state=0`」  
- 总表：`POST /registrar/charges` ⬜ → ✅  
- 删除 registers Request 示例中的 `payChannel`/`receivedAmount`/`tradeNo`  
- Response 改为 `visitState=0`，无 `paymentId`/`changeAmount`  
- 新增 charges Request/Response 示例（`billIds` + `payChannel`）

- [ ] **Step 2: PROGRESS.md**

更新 hospital-his 行：`窗口挂号（待支付）` + `窗口收费 POST /registrar/charges`

- [ ] **Step 3: Commit**

```bash
git add hospital-backend docs scripts
git commit -m "feat(his): split window register and charge flow (v2)"
```

---

## Spec Coverage Checklist

| Spec 要求 | Task |
|-----------|------|
| registers visit_state=0 | Task 4 |
| 去掉 registers 支付字段 | Task 4 |
| POST /charges 新 API | Task 5, 6 |
| 多渠道 payChannel | Task 1, 5 |
| MEDICAL_BOOK 两条 bill | Task 4 |
| need_medical_book 付清时更新 | Task 3 |
| settlePaidBill 复用 | Task 3, 5 |
| 待支付退号 | Task 2, 7 |
| 验收 R1–R8 | Task 8 |
| API/PROGRESS 文档 | Task 9 |

---

## 执行选项

Plan 已保存至 `docs/superpowers/plans/2026-06-09-window-register.md`。

**1. Subagent-Driven（推荐）** — 每个 Task 派生子 agent，任务间 review，迭代快  

**2. Inline Execution** — 本会话按 Task 顺序直接实现，检查点 review  

你想用哪种方式开始实现？
