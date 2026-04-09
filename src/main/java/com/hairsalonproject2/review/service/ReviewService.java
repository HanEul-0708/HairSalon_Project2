package com.hairsalonproject2.review.service;

import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.MonthlyReviewStatResponse;
import com.hairsalonproject2.review.dto.ReviewCreateRequest;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewLikeToggleResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.dto.ReviewUpdateRequest;
import com.hairsalonproject2.review.dto.SalonRankingResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReviewService {

    ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request);

    ReviewResponse getReview(Integer reviewId, String loginMemberId);

    ReviewDetailResponse getReviewDetail(Integer reviewId, String loginMemberId);

    List<ReviewResponse> getAllReviews(String loginMemberId, Integer designerId, String sortBy);

    List<ReviewResponse> getReviewsByMember(String memberId, String loginMemberId);

    ReviewResponse updateReview(String loginMemberId, boolean isAdmin, Integer reviewId, ReviewUpdateRequest request);

    void deleteReview(String loginMemberId, boolean isAdmin, Integer reviewId);

    Double getAverageRatingByDesigner(Integer designerId);

    List<DesignerRankingResponse> getTop3Designers();

    List<MonthlyReviewStatResponse> getMonthlyReviewStats();

    List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(LocalDate startDate, LocalDate endDate);

    List<SalonRankingResponse> getTopSalons();

    ReviewLikeToggleResponse toggleLike(Integer reviewId, String loginMemberId);

    List<ReviewResponse> getRecentReviews();
}
