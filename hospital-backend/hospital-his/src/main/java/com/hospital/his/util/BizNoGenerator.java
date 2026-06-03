package com.hospital.his.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class BizNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private BizNoGenerator() {
    }

    public static String medicalRecordNo() {
        return "MR" + DATE_FMT.format(LocalDate.now())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    public static String billNo() {
        return "B" + DATE_FMT.format(LocalDate.now())
                + System.currentTimeMillis() % 100000;
    }

    public static String paymentNo() {
        return "P" + DATE_FMT.format(LocalDate.now())
                + System.currentTimeMillis() % 100000;
    }

    public static String refundNo() {
        return "R" + DATE_FMT.format(LocalDate.now())
                + System.currentTimeMillis() % 100000;
    }

    public static String prescriptionNo() {
        return "RX" + DATE_FMT.format(LocalDate.now())
                + String.format("%05d", ThreadLocalRandom.current().nextInt(100000));
    }
}
