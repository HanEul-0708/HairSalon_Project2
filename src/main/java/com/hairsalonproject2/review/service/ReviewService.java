package com.hairsalonproject2.review.service;

import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.MonthlyReviewStatResponse;
import com.hairsalonproject2.review.dto.ReviewCreateRequest;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.dto.ReviewUpdateRequest;
import com.hairsalonproject2.review.dto.SalonRankingResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReviewService {

    ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request);

    ReviewResponse getReview(Integer reviewId);

    List<ReviewResponse> getAllReviews();

    List<ReviewResponse> getRecentReviews();

    List<ReviewResponse> getReviewsByMember(String memberId);

    ReviewResponse updateReview(Integer reviewId, ReviewUpdateRequest request);

    void deleteReview(Integer reviewId);

    Double getAverageRatingByDesigner(Integer designerId);

    List<DesignerRankingResponse> getTop3Designers();

    List<MonthlyReviewStatResponse> getMonthlyReviewStats();

    List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(LocalDate startDate, LocalDate endDate);

    List<SalonRankingResponse> getTopSalons();

    ReviewDetailResponse getReviewDetail(Integer reviewId);
}
