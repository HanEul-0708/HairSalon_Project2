package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.MonthlyReviewStatResponse;
import com.hairsalonproject2.review.dto.ReviewCreateRequest;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewLikeToggleResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.dto.ReviewUpdateRequest;
import com.hairsalonproject2.review.dto.SalonRankingResponse;
import com.hairsalonproject2.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ReviewResponse createReview(@AuthenticationPrincipal CustomUserDetails userDetails,
                                       @Valid @RequestBody ReviewCreateRequest request) {
        return reviewService.createReview(userDetails.getMember().getMemberId(), request);
    }

    @GetMapping("/{reviewId}")
    public ReviewResponse getReview(@PathVariable Integer reviewId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        return reviewService.getReview(reviewId, getLoginMemberId(userDetails));
    }

    @GetMapping
    public List<ReviewResponse> getAllReviews(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              @RequestParam(required = false) Integer designerId,
                                              @RequestParam(defaultValue = "latest") String sortBy) {
        return reviewService.getAllReviews(getLoginMemberId(userDetails), designerId, sortBy);
    }

    @GetMapping("/member/{memberId}")
    public List<ReviewResponse> getReviewsByMember(@PathVariable String memberId,
                                                   @AuthenticationPrincipal CustomUserDetails userDetails) {
        return reviewService.getReviewsByMember(memberId, getLoginMemberId(userDetails));
    }

    @PutMapping("/{reviewId}")
    public ReviewResponse updateReview(@AuthenticationPrincipal CustomUserDetails userDetails,
                                       @PathVariable Integer reviewId,
                                       @Valid @RequestBody ReviewUpdateRequest request) {
        return reviewService.updateReview(
                userDetails.getMember().getMemberId(),
                isAdmin(userDetails),
                reviewId,
                request
        );
    }

    @DeleteMapping("/{reviewId}")
    public void deleteReview(@AuthenticationPrincipal CustomUserDetails userDetails,
                             @PathVariable Integer reviewId) {
        reviewService.deleteReview(
                userDetails.getMember().getMemberId(),
                isAdmin(userDetails),
                reviewId
        );
    }

    @PostMapping("/{reviewId}/likes")
    public ReviewLikeToggleResponse toggleLike(@PathVariable Integer reviewId,
                                               @AuthenticationPrincipal CustomUserDetails userDetails) {
        return reviewService.toggleLike(reviewId, userDetails.getMember().getMemberId());
    }

    @GetMapping("/designer/{designerId}/average-rating")
    public Double getAverageRatingByDesigner(@PathVariable Integer designerId) {
        return reviewService.getAverageRatingByDesigner(designerId);
    }

    @GetMapping("/designer/top3")
    public List<DesignerRankingResponse> getTop3Designers() {
        return reviewService.getTop3Designers();
    }

    @GetMapping("/stats/monthly")
    public List<MonthlyReviewStatResponse> getMonthlyReviewStats() {
        return reviewService.getMonthlyReviewStats();
    }

    @GetMapping("/stats/monthly/period")
    public List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(@RequestParam LocalDate startDate,
                                                                         @RequestParam LocalDate endDate) {
        return reviewService.getMonthlyReviewStatsByPeriod(startDate, endDate);
    }

    @GetMapping("/salon/top3")
    public List<SalonRankingResponse> getTopSalons() {
        return reviewService.getTopSalons();
    }

    @GetMapping("/{reviewId}/detail")
    public ReviewDetailResponse getReviewDetail(@PathVariable Integer reviewId,
                                                @AuthenticationPrincipal CustomUserDetails userDetails) {
        return reviewService.getReviewDetail(reviewId, getLoginMemberId(userDetails));
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails.getMember().getRole().name().equals("ADMIN");
    }

    private String getLoginMemberId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getMember().getMemberId();
    }
}
