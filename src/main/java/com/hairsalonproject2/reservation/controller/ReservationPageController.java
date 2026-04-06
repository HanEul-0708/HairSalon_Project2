package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.salonservice.service.SalonServiceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 예약 페이지 전용 컨트롤러
 */
@Controller
@RequiredArgsConstructor
public class ReservationPageController {

    private final DesignerQueryService designerQueryService;
    private final SalonServiceQueryService salonServiceQueryService;

    /**
     * 예약 목록 페이지
     * GET /reservations
     */
    @GetMapping("/reservations")
    public String reservationList() {
        return "reservation/list";
    }

    /**
     * 예약 생성 페이지
     * GET /reservations/new
     *
     * 화면에서 실제 예약 가능한 디자이너 / 시술 목록을 선택할 수 있도록
     * Model에 데이터를 내려준다.
     */
    @GetMapping("/reservations/new")
    public String reservationCreate(Model model) {
        model.addAttribute("designers", designerQueryService.search(new DesignerSearchRequest()));
        model.addAttribute("services", salonServiceQueryService.list(null));
        return "reservation/create";
    }
}