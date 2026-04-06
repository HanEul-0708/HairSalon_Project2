package com.hairsalonproject2.common.exception;

import lombok.Getter;

/**
 * 서비스 계층 비즈니스 예외
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
