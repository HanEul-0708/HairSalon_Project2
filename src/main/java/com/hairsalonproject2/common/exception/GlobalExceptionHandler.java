package com.hairsalonproject2.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler
 *
 * 프로젝트 전체에서 발생하는 예외를 한 곳에서 처리하는 클래스
 *
 * 목적:
 * 1. 서비스/컨트롤러마다 try-catch를 반복하지 않기 위해
 * 2. 에러 응답 형식을 통일하기 위해
 * 3. 프론트/테스트에서 보기 쉽게 만들기 위해
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * IllegalArgumentException 처리
     *
     * 주로 잘못된 요청 값, 없는 데이터, 비즈니스 검증 실패 등에 사용
     *
     * 예:
     * - 예약이 존재하지 않습니다.
     * - 이미 리뷰가 작성된 예약입니다.
     * - 시술 완료된 예약만 리뷰를 작성할 수 있습니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("message", e.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * DTO 검증 예외 처리
     *
     * @Valid가 붙은 요청 DTO에서 검증 실패 시 발생
     *
     * 예:
     * - 회원 ID는 필수입니다.
     * - 예약 날짜는 필수입니다.
     * - 결제 금액은 0 이상이어야 합니다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());

        // 첫 번째 검증 에러 메시지만 꺼내서 응답
        String message = e.getBindingResult()
                .getFieldError()
                .getDefaultMessage();

        response.put("message", message);

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 그 외 모든 예외 처리
     *
     * 예상하지 못한 서버 내부 오류를 잡음
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("message", "서버 내부 오류가 발생했습니다.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}