package com.hairsalonproject2.common.advice;

import com.hairsalonproject2.board.exception.BoardException;
import com.hairsalonproject2.common.exception.BusinessException;
import com.hairsalonproject2.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * View controller exception handler.
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

        if (request.getHeader("X-Requested-With") != null) {
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
            model.addAttribute("requestUri", request.getRequestURI());
            return "error/common-error";
        }

        String referer = request.getHeader("Referer");
        if (referer != null) {
            return "redirect:" + referer;
        }

        model.addAttribute("errorMessage", e.getErrorCode().getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/common-error";
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public String handleAccessDeniedException(Exception e,
                                              HttpServletRequest request,
                                              HttpServletResponse response,
                                              Model model) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("errorMessage", ErrorCode.ACCESS_DENIED.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/access-denied";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String handleResponseStatusException(ResponseStatusException e,
                                                HttpServletRequest request,
                                                HttpServletResponse response,
                                                Model model) {
        response.setStatus(e.getStatusCode().value());
        model.addAttribute("errorMessage", e.getReason());
        model.addAttribute("requestUri", request.getRequestURI());

        if (e.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
            return "error/access-denied";
        }

        return "error/common-error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNoResourceFoundException(NoResourceFoundException e,
                                                 HttpServletRequest request,
                                                 HttpServletResponse response,
                                                 Model model) {
        return renderNotFound(request, response, model);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handleNoHandlerFoundException(NoHandlerFoundException e,
                                                HttpServletRequest request,
                                                HttpServletResponse response,
                                                Model model) {
        return renderNotFound(request, response, model);
    }

    private String renderNotFound(HttpServletRequest request,
                                  HttpServletResponse response,
                                  Model model) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("errorMessage", "요청한 페이지를 찾을 수 없습니다.");
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/common-error";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e,
                                  Model model,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("errorMessage", ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        model.addAttribute("requestUri", request.getRequestURI());
        return "error/common-error";
    }
}
