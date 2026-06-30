package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.PaymentChannel;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.PaymentRepository;
import com.hospital.his.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialQueryService {

    private static final Set<String> EXAM_BIZ_TYPES = Set.of(
            BillBizType.CHECK, BillBizType.INSPECTION, BillBizType.DISPOSAL);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public Map<String, Object> listPayments(Long patientId, Long registerId, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        List<Map<String, Object>> raw = paymentRepository.findByPatientId(patientId, registerId, offset, pageSize);
        List<Map<String, Object>> list = raw.stream().map(this::enrichPaymentSummary).toList();
        return Map.of("list", list, "page", page, "pageSize", pageSize);
    }

    public Map<String, Object> getPaymentDetail(Long patientId, Long paymentId) {
        Map<String, Object> payment = paymentRepository.findByIdForPatient(paymentId, patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "支付记录不存在"));
        List<Map<String, Object>> bills = paymentRepository.findBillsByPaymentId(paymentId).stream()
                .map(this::enrichBillRow)
                .toList();
        Map<String, Object> result = enrichPaymentSummary(payment);
        result.put("bills", bills);
        return result;
    }

    public Map<String, Object> listRefunds(Long patientId, Long registerId, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        List<Map<String, Object>> list = refundRepository.findByPatientId(patientId, registerId, offset, pageSize)
                .stream()
                .map(this::enrichRefundRow)
                .toList();
        return Map.of("list", list, "page", page, "pageSize", pageSize);
    }

    public Map<String, Object> shiftSummary(Long operatorId, LocalDate workDate) {
        LocalDate date = workDate != null ? workDate : LocalDate.now();
        OffsetDateTime start = date.atStartOfDay().atOffset(ZoneOffset.ofHours(8));
        OffsetDateTime end = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.ofHours(8));

        List<Map<String, Object>> paymentRows = paymentRepository.summarizeByOperator(operatorId, start, end);
        List<Map<String, Object>> refundRows = refundRepository.summarizeByOperator(operatorId, start, end);

        BigDecimal paymentTotal = sumAmount(paymentRows, "totalAmount");
        BigDecimal refundTotal = sumAmount(refundRows, "totalAmount");
        int paymentCount = sumCount(paymentRows);
        int refundCount = sumCount(refundRows);

        Map<String, Object> result = new HashMap<>();
        result.put("workDate", date.toString());
        result.put("operatorId", operatorId);
        result.put("paymentCount", paymentCount);
        result.put("paymentTotal", paymentTotal);
        result.put("refundCount", refundCount);
        result.put("refundTotal", refundTotal);
        result.put("netTotal", paymentTotal.subtract(refundTotal));
        result.put("paymentsByChannel", enrichChannelSummary(paymentRows));
        result.put("refundsByChannel", enrichChannelSummary(refundRows));
        return result;
    }

    public static boolean matchesBillScope(String bizType, String scope) {
        if (!StringUtils.hasText(scope) || "all".equalsIgnoreCase(scope)) {
            return true;
        }
        if ("outpatient".equalsIgnoreCase(scope)) {
            return BillBizType.REGISTER.equals(bizType) || "REGIST".equals(bizType)
                    || BillBizType.MEDICAL_BOOK.equals(bizType) || BillBizType.PRESCRIPTION.equals(bizType);
        }
        if ("exam".equalsIgnoreCase(scope)) {
            return bizType != null && EXAM_BIZ_TYPES.contains(bizType);
        }
        return true;
    }

    private Map<String, Object> enrichPaymentSummary(Map<String, Object> row) {
        Map<String, Object> item = new HashMap<>(row);
        String channel = (String) row.get("channel");
        item.put("channelLabel", PaymentChannel.labelOf(channel));
        Object payTime = row.get("payTime");
        if (payTime instanceof OffsetDateTime odt) {
            item.put("paidAt", odt.toString());
        }
        Object total = row.get("totalAmount");
        if (total != null) {
            item.put("amount", total);
        }
        Long paymentId = ((Number) row.get("paymentId")).longValue();
        List<String> titles = paymentRepository.findBillsByPaymentId(paymentId).stream()
                .map(b -> (String) b.get("billTitle"))
                .filter(StringUtils::hasText)
                .toList();
        item.put("summary", titles.isEmpty() ? "窗口缴费" : String.join("、", titles));
        return item;
    }

    private Map<String, Object> enrichBillRow(Map<String, Object> row) {
        Map<String, Object> item = new HashMap<>(row);
        item.put("bizTypeLabel", bizTypeLabel((String) row.get("bizType")));
        return item;
    }

    private Map<String, Object> enrichRefundRow(Map<String, Object> row) {
        Map<String, Object> item = new HashMap<>(row);
        item.put("channelLabel", PaymentChannel.labelOf((String) row.get("channel")));
        return item;
    }

    private List<Map<String, Object>> enrichChannelSummary(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> item = new HashMap<>(row);
            item.put("channelLabel", PaymentChannel.labelOf((String) row.get("channel")));
            return item;
        }).toList();
    }

    private BigDecimal sumAmount(List<Map<String, Object>> rows, String key) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            Object val = row.get(key);
            if (val instanceof BigDecimal bd) {
                sum = sum.add(bd);
            }
        }
        return sum;
    }

    private int sumCount(List<Map<String, Object>> rows) {
        int sum = 0;
        for (Map<String, Object> row : rows) {
            Object val = row.get("count");
            if (val instanceof Number n) {
                sum += n.intValue();
            }
        }
        return sum;
    }

    private static String bizTypeLabel(String bizType) {
        if (bizType == null) {
            return "—";
        }
        return switch (bizType) {
            case BillBizType.REGISTER, "REGIST" -> "挂号";
            case BillBizType.INSPECTION -> "检验";
            case BillBizType.CHECK -> "检查";
            case BillBizType.PRESCRIPTION -> "处方";
            case BillBizType.DISPOSAL -> "处置";
            case BillBizType.MEDICAL_BOOK -> "病历本";
            default -> bizType;
        };
    }
}
