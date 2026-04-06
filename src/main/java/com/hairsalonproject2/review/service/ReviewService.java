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

/**
 * ReviewService
 *
 * 리뷰 관련 비즈니스 로직 인터페이스
 */
public interface ReviewService {

    /**
     * 리뷰 작성
     *
     * 변경 포인트
     * - request만 받던 구조에서
     *   로그인한 회원 ID(loginMemberId)를 함께 받도록 수정
     *
     * 이유
     * - 서비스 계층에서 "본인 예약인지"를 직접 검증하기 위해
     */
    ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request);

    /**
     * 리뷰 1건 조회
     */
    ReviewResponse getReview(Integer reviewId);

    /**
     * 전체 리뷰 목록 조회
     */
    List<ReviewResponse> getAllReviews();

    /**
     * 특정 회원의 리뷰 목록 조회
     */
    List<ReviewResponse> getReviewsByMember(String memberId);

    /**
     * 리뷰 수정
     */
    ReviewResponse updateReview(String loginMemberId, boolean isAdmin, Integer reviewId, ReviewUpdateRequest request);

    /**
     * 리뷰 삭제
     */
    void deleteReview(String loginMemberId, boolean isAdmin, Integer reviewId);

    /**
     * 특정 디자이너의 평균 평점 조회
     */
    Double getAverageRatingByDesigner(Integer designerId);

    /**
     * 상위 디자이너 랭킹 조회
     */
    List<DesignerRankingResponse> getTop3Designers();

    /**
     * 전체 월별 리뷰 통계 조회
     */
    List<MonthlyReviewStatResponse> getMonthlyReviewStats();

    /**
     * 특정 기간의 월별 리뷰 통계 조회
     */
    List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(LocalDate startDate, LocalDate endDate);

    /**
     * 상위 미용실 랭킹 조회
     */
    List<SalonRankingResponse> getTopSalons();

    /**
     * 리뷰 상세 조회
     */
    ReviewDetailResponse getReviewDetail(Integer reviewId);
}
