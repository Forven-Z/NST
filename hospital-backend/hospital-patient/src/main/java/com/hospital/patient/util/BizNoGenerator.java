package com.hospital.patient.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 院内业务编号生成（v1.14：bill/payment/refund/prescription 业务标识统一用表 id，此处仅保留病历号等档案编号）。
 */
public final class BizNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BizNoGenerator() {
    }

    public static String medicalRecordNo() {
        return "MR" + DATE_FMT.format(LocalDate.now())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
