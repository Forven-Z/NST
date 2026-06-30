package com.hospital.his.order.handler;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MedicalOrderHandlerRegistry {

    private final Map<String, MedicalOrderHandler> byBizType;

    public MedicalOrderHandlerRegistry(List<MedicalOrderHandler> handlers) {
        this.byBizType = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(MedicalOrderHandler::bizType, Function.identity()));
    }

    public MedicalOrderHandler handler(String bizType) {
        MedicalOrderHandler handler = byBizType.get(bizType);
        if (handler == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的业务类型: " + bizType);
        }
        return handler;
    }

    public boolean handles(String bizType) {
        return byBizType.containsKey(bizType);
    }
}
