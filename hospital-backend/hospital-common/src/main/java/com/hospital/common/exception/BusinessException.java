package com.hospital.common.exception;

import lombok.Getter;

/**
 * 可预期的业务异常，由全局处理器转换为统一 {@link com.hospital.common.Result}。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
