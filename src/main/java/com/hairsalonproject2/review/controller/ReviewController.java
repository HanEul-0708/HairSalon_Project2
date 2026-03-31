package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.MonthlyReviewStatResponse;
import com.hairsalonproject2.review.dto.ReviewCreateRequest;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.dto.ReviewUpdateRequest;
import com.hairsalonproject2.review.dto.SalonRankingResponse;
import com.hairsalonproject2.review.service.ReviewService;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * ReviewController
 *
 * 리뷰 관련 API 요청을 처리하는 컨트롤러
 */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 리뷰 작성
     */
    @PostMapping
    public ReviewResponse createReview(@Valid @RequestBody ReviewCreateRequest request) {
        return reviewService.createReview(request);
    }

    /**
     * 리뷰 1건 조회
     */
    @GetMapping("/{reviewId}")
    public ReviewResponse getReview(@PathVariable Integer reviewId) {
        return reviewService.getReview(reviewId);
    }

    /**
     * 전체 리뷰 목록 조회
     */
    @GetMapping
    public List<ReviewResponse> getAllReviews() {
        return reviewService.getAllReviews();
    }

    /**
     * 회원별 리뷰 목록 조회
     */
    @GetMapping("/member/{memberId}")
    public List<ReviewResponse> getReviewsByMember(@PathVariable String memberId) {
        return reviewService.getReviewsByMember(memberId);
    }

    /**
     * 리뷰 수정
     */
    @PutMapping("/{reviewId}")
    public ReviewResponse updateReview(@PathVariable Integer reviewId,
                                       @Valid @RequestBody ReviewUpdateRequest request) {
        return reviewService.updateReview(reviewId, request);
    }

    /**
     * 리뷰 삭제
     */
    @DeleteMapping("/{reviewId}")
    public void deleteReview(@PathVariable Integer reviewId) {
        reviewService.deleteReview(reviewId);
    }

    /**
     * 특정 디자이너 평균 평점 조회
     */
    @GetMapping("/designer/{designerId}/average-rating")
    public Double getAverageRatingByDesigner(@PathVariable Integer designerId) {
        return reviewService.getAverageRatingByDesigner(designerId);
    }

    /**
     * 평균 평점 기준 디자이너 TOP3 조회
     */
    @GetMapping("/designer/top3")
    public List<DesignerRankingResponse> getTop3Designers() {
        return reviewService.getTop3Designers();
    }

    /**
     * 월별 리뷰 수 / 평균 평점 집계 조회
     */
    @GetMapping("/stats/monthly")
    public List<MonthlyReviewStatResponse> getMonthlyReviewStats() {
        return reviewService.getMonthlyReviewStats();
    }

    /**
     * 특정 기간 월별 리뷰 수 / 평균 평점 집계 조회
     */
    @GetMapping("/stats/monthly/period")
    public List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {

        return reviewService.getMonthlyReviewStatsByPeriod(startDate, endDate);
    }

    /**
     * 미용실 TOP3 랭킹 조회
     */
    @GetMapping("/salon/top3")
    public List<SalonRankingResponse> getTopSalons() {
        return reviewService.getTopSalons();
    }

    /**
     * 리뷰 상세 조회
     *
     * 리뷰 기본 정보 + 이미지 목록 함께 반환
     */
    @GetMapping("/{reviewId}/detail")
    public ReviewDetailResponse getReviewDetail(@PathVariable Integer reviewId) {
        return reviewService.getReviewDetail(reviewId);
    }
}