package com.hairsalonproject2.review.service;

import com.hairsalonproject2.review.dto.*;

import java.time.LocalDate;
import java.util.List;

public interface ReviewService {

    ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request);

    ReviewResponse getReview(Integer reviewId, String loginMemberId, String visitorToken);

    ReviewDetailResponse getReviewDetail(Integer reviewId, String loginMemberId, String visitorToken);

    List<ReviewResponse> getAllReviews(String loginMemberId, String visitorToken, Integer designerId, String sortBy);

    List<ReviewResponse> getRecentReviews();

    List<ReviewResponse> getReviewsByMember(String memberId, String loginMemberId, String visitorToken);

    ReviewResponse updateReview(String loginMemberId, boolean isAdmin, Integer reviewId, ReviewUpdateRequest request);

    void deleteReview(String loginMemberId, boolean isAdmin, Integer reviewId);

    Double getAverageRatingByDesigner(Integer designerId);

    List<DesignerRankingResponse> getTop3Designers();

    List<MonthlyReviewStatResponse> getMonthlyReviewStats();

    List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(LocalDate startDate, LocalDate endDate);

    List<SalonRankingResponse> getTopSalons();

    ReviewLikeToggleResponse toggleLike(Integer reviewId, String loginMemberId, String visitorToken);
}
