package com.hairsalonproject2.home.controller;

import com.hairsalonproject2.review.service.ReviewService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 화면 요청 처리 컨트롤러
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ReviewService reviewService;
    private final SalonQueryService salonQueryService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("topSalons", salonQueryService.recommendedTop3());
        model.addAttribute("topDesigners", reviewService.getTop3Designers());
        model.addAttribute("recentReviews", reviewService.getRecentReviews());
        return "index";
    }
}
