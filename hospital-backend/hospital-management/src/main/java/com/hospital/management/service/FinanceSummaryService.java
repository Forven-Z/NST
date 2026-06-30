package com.hospital.management.service;

import com.hospital.common.constant.PaymentChannel;
import com.hospital.management.repository.FinanceSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FinanceSummaryService {

    private final FinanceSummaryRepository financeSummaryRepository;

    public Map<String, Object> dailySummary(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate from = dateFrom != null ? dateFrom : LocalDate.now();
        LocalDate to = dateTo != null ? dateTo : from;
        if (to.isBefore(from)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.ofHours(8));
        OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.ofHours(8));

        List<Map<String, Object>> paymentRows = financeSummaryRepository.summarizePayments(start, end);
        List<Map<String, Object>> refundRows = financeSummaryRepository.summarizeRefunds(start, end);

        BigDecimal paymentTotal = financeSummaryRepository.sumPaymentAmount(start, end);
        BigDecimal refundTotal = financeSummaryRepository.sumRefundAmount(start, end);

        Map<String, Object> result = new HashMap<>();
        result.put("dateFrom", from.toString());
        result.put("dateTo", to.toString());
        result.put("paymentCount", financeSummaryRepository.countPayments(start, end));
        result.put("paymentTotal", paymentTotal);
        result.put("refundCount", financeSummaryRepository.countRefunds(start, end));
        result.put("refundTotal", refundTotal);
        result.put("netTotal", paymentTotal.subtract(refundTotal));
        result.put("paymentsByChannel", enrichChannelSummary(paymentRows));
        result.put("refundsByChannel", enrichChannelSummary(refundRows));
        return result;
    }

    private List<Map<String, Object>> enrichChannelSummary(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> item = new HashMap<>(row);
            item.put("channelLabel", channelLabel((String) row.get("channel")));
            return item;
        }).toList();
    }

    private String channelLabel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "-";
        }
        return switch (channel.trim().toUpperCase()) {
            case PaymentChannel.CASH -> "现金";
            case PaymentChannel.WECHAT -> "微信";
            case PaymentChannel.ALIPAY -> "支付宝";
            case PaymentChannel.INSURANCE -> "医保";
            case PaymentChannel.SCAN -> "扫码";
            default -> channel;
        };
    }
}
