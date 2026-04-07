package com.hairsalonproject2.home.controller;

import com.hairsalonproject2.designer.dto.request.DesignerSearchRequest;
import com.hairsalonproject2.designer.service.DesignerQueryService;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;

/**
 * 홈 화면 요청 처리 컨트롤러
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final SalonQueryService salonQueryService;
    private final DesignerQueryService designerQueryService;

    /**
     * 메인 홈 화면 요청
     */
    @GetMapping("/")
    public String home(Model model) {
        SalonSearchRequest salonSearchRequest = new SalonSearchRequest();
        salonSearchRequest.setMinRating(BigDecimal.valueOf(4.0));
        salonSearchRequest.setSort("rating");

        DesignerSearchRequest designerSearchRequest = new DesignerSearchRequest();
        designerSearchRequest.setMinRating(BigDecimal.valueOf(4.0));

        model.addAttribute("topSalons", salonQueryService.search(salonSearchRequest).stream().limit(3).toList());
        model.addAttribute("topDesigners", designerQueryService.search(designerSearchRequest).stream().limit(4).toList());
        return "index";
    }
}
