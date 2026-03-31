package com.hairsalonproject2.global.advice;

import com.hairsalonproject2.exception.BusinessException;
import com.hairsalonproject2.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 전역 예외 처리기
 *
 * 역할
 * 1. 비즈니스 예외 공통 처리
 * 2. 일반 예외 공통 처리
 * 3. 에러 페이지로 메시지 전달
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 우리가 직접 정의한 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException e, Model model, HttpServletRequest request) {
        model.addAttribute("errorMessage", e.getErrorCode().getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/common-error";
    }

    /**
     * 그 외 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public String handleException(Exception e, Model model, HttpServletRequest request) {
        model.addAttribute("errorMessage", ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/common-error";
    }
}