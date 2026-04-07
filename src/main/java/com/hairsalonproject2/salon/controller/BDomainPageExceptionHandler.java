package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.admin.controller.AdminDesignerController;
import com.hairsalonproject2.admin.controller.AdminSalonController;
import com.hairsalonproject2.designer.controller.DesignerController;
import com.hairsalonproject2.salonservice.controller.ServiceController;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(assignableTypes = {
        SalonController.class,
        DesignerController.class,
        ServiceController.class,
        AdminSalonController.class,
        AdminDesignerController.class
})
public class BDomainPageExceptionHandler {

    @ExceptionHandler({
            EntityNotFoundException.class,
            MethodArgumentTypeMismatchException.class,
            MissingPathVariableException.class
    })
    public ModelAndView handleNotFound(Exception exception,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());

        ModelAndView modelAndView = new ModelAndView("error/common-error");
        modelAndView.addObject("errorMessage", "요청한 B 영역 데이터 또는 화면을 찾을 수 없습니다.");
        modelAndView.addObject("requestUri", request.getRequestURI());
        return modelAndView;
    }
}
