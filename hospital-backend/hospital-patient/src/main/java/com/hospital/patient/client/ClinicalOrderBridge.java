package com.hospital.patient.client;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.internal.OrderBizCommand;
import com.hospital.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClinicalOrderBridge {

    private final ClinicalOrderFeignClient clinicalOrderFeignClient;

    public boolean handles(String bizType) {
        return BillBizType.INSPECTION.equals(bizType)
                || BillBizType.CHECK.equals(bizType)
                || BillBizType.PRESCRIPTION.equals(bizType)
                || BillBizType.DISPOSAL.equals(bizType);
    }

    public void onBillPaid(String bizType, long bizId) {
        unwrap(clinicalOrderFeignClient.onBillPaid(new OrderBizCommand(bizType, bizId)));
    }

    public void onRefund(String bizType, long bizId) {
        unwrap(clinicalOrderFeignClient.onRefund(new OrderBizCommand(bizType, bizId)));
    }

    public void assertBillRefundable(String bizType, long bizId) {
        unwrap(clinicalOrderFeignClient.assertRefundable(new OrderBizCommand(bizType, bizId)));
    }

    private void unwrap(Result<Void> result) {
        if (result == null || !Boolean.TRUE.equals(result.getSuccess())) {
            String message = result != null ? result.getMessage() : "clinical 服务无响应";
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
