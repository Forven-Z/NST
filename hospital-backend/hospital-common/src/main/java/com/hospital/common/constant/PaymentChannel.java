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
