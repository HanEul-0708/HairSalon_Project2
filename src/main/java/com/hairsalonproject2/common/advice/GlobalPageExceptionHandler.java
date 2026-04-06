package com.hairsalonproject2.common.advice;

import com.hairsalonproject2.board.exception.BoardException;
import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.common.exception.ErrorCode;
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
public class GlobalPageExceptionHandler {

    @ExceptionHandler(BoardException.class)
    public String handleBoardException(BoardException e,
                                       HttpServletRequest request,
                                       Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/board-error";
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException e,
                                          HttpServletRequest request,
                                          Model model) {

        // Ajax / API 대응 (추후 확장 가능)
        if (request.getHeader("X-Requested-With") != null) {
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
            model.addAttribute("requestUri", request.getRequestURI());
            return "error/common-error";
        }

        // referer로 이전 페이지로 돌려보내기
        String referer = request.getHeader("Referer");

        if (referer != null) {
            return "redirect:" + referer;
        }

        // fallback
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
