package com.hospital.his.order.handler;

import java.util.Map;

/**
 * 医嘱 Handler（步骤 ③）：开单 + 缴费/退费回调一体，按 {@link com.hospital.common.constant.BillBizType} 选型。
 */
public interface MedicalOrderHandler {

    String bizType();

    Map<String, Object> createOrder(OrderCreateContext ctx);

    void onBillPaid(long bizId);

    void onRefund(long bizId);

    void assertBillRefundable(long bizId);
}
