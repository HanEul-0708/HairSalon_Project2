package com.hairsalonproject2.review.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 리뷰 페이지 전용 컨트롤러
 *
 * 역할
 * 1. 리뷰 목록 화면 반환
 * 2. 리뷰 작성 화면 반환
 */
@Controller
public class ReviewPageController {

    /**
     * 리뷰 목록 페이지
     * GET /reviews
     */
    @GetMapping("/reviews")
    public String reviewListPage() {
        return "review/list";
    }

    /**
     * 리뷰 작성 페이지
     * GET /reviews/write?reservationId=1
     *
     * 리뷰 작성 시 어떤 예약에 대한 리뷰인지 알아야 하므로
     * reservationId를 화면에 전달한다.
     */
    @GetMapping("/reviews/write")
    public String reviewWritePage(@RequestParam(required = false) Integer reservationId,
                                  Model model) {
        model.addAttribute("reservationId", reservationId);
        return "review/write";
    }
}