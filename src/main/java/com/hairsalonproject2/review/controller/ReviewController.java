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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private static final String REVIEW_VISITOR_COOKIE = "review_visitor_token";

    private final ReviewService reviewService;

    @PostMapping
    public ReviewResponse createReview(@AuthenticationPrincipal CustomUserDetails userDetails,
                                       @Valid @RequestBody ReviewCreateRequest request) {
        return reviewService.createReview(userDetails.getMember().getMemberId(), request);
    }

    @GetMapping("/{reviewId}")
    public ReviewResponse getReview(@PathVariable Integer reviewId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    HttpServletRequest request) {
        return reviewService.getReview(reviewId, getLoginMemberId(userDetails), getVisitorToken(request));
    }

    @GetMapping
    public List<ReviewResponse> getAllReviews(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              HttpServletRequest request,
                                              @RequestParam(required = false) Integer designerId,
                                              @RequestParam(defaultValue = "latest") String sortBy) {
        return reviewService.getAllReviews(getLoginMemberId(userDetails), getVisitorToken(request), designerId, sortBy);
    }

    @GetMapping("/member/{memberId}")
    public List<ReviewResponse> getReviewsByMember(@PathVariable String memberId,
                                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                                   HttpServletRequest request) {
        return reviewService.getReviewsByMember(memberId, getLoginMemberId(userDetails), getVisitorToken(request));
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
                                               @AuthenticationPrincipal CustomUserDetails userDetails,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        String loginMemberId = getLoginMemberId(userDetails);
        String visitorToken = loginMemberId == null ? ensureVisitorToken(request, response) : null;
        return reviewService.toggleLike(reviewId, loginMemberId, visitorToken);
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
                                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                                HttpServletRequest request) {
        return reviewService.getReviewDetail(reviewId, getLoginMemberId(userDetails), getVisitorToken(request));
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails.getMember().getRole().name().equals("ADMIN");
    }

    private String getLoginMemberId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getMember().getMemberId();
    }

    private String getVisitorToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (REVIEW_VISITOR_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private String ensureVisitorToken(HttpServletRequest request, HttpServletResponse response) {
        String token = getVisitorToken(request);
        if (token != null && !token.isBlank()) {
            return token;
        }

        String generatedToken = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(REVIEW_VISITOR_COOKIE, generatedToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        response.addCookie(cookie);
        return generatedToken;
    }
}
