package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ReviewPageController {

    private final ReviewService reviewService;

    @GetMapping("/reviews")
    public String reviewListPage(Model model) {
        // 리뷰 목록 화면은 전체 리뷰를 읽어서 바로 렌더링한다.
        model.addAttribute("reviews", reviewService.getAllReviews());
        return "review/list";
    }

    @GetMapping("/reviews/write")
    public String reviewWritePage(@RequestParam(required = false) Integer reservationId,
                                  Model model) {
        // 어떤 예약에 대한 리뷰인지 알아야 하므로 reservationId를 함께 넘긴다.
        model.addAttribute("reservationId", reservationId);
        return "review/write";
    }
}
