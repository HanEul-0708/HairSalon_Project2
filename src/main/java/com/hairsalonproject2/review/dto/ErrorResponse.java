package com.hairsalonproject2.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * ErrorResponse
 *
 * 공통 에러 응답 DTO
 */
@Getter
@AllArgsConstructor
public class ErrorResponse {

    /**
     * 에러 발생 시각
     */
    private LocalDateTime timestamp;

    /**
     * HTTP 상태 코드
     */
    private int status;

    /**
     * 에러 메시지
     */
    private String message;
}