package com.hairsalonproject2.review.service;

import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.review.dto.ReviewCreateRequest;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.MonthlyReviewStatResponse;
import com.hairsalonproject2.review.dto.SalonRankingResponse;
import com.hairsalonproject2.review.dto.ReviewUpdateRequest;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.dto.ReviewUpdateRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final DesignerRepository designerRepository;

    /**
     * 리뷰 작성
     */
    @Override
    public ReviewResponse createReview(ReviewCreateRequest request) {

        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException("예약이 존재하지 않습니다."));

        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        Designer designer = designerRepository.findById(request.getDesignerId())
                .orElseThrow(() -> new IllegalArgumentException("디자이너가 존재하지 않습니다."));

        if (reviewRepository.findByReservation_ReservationId(request.getReservationId()).isPresent()) {
            throw new IllegalArgumentException("이미 리뷰가 작성된 예약입니다.");
        }

        Review review = Review.builder()
                .reservation(reservation)
                .member(member)
                .designer(designer)
                .rating(request.getRating())
                .content(request.getContent())
                .build();

        Review savedReview = reviewRepository.save(review);

        return toResponse(savedReview);
    }

    /**
     * 리뷰 1건 조회
     */
    @Override
    public ReviewResponse getReview(Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        return toResponse(review);
    }

    /**
     * 전체 리뷰 목록 조회
     */
    @Override
    public List<ReviewResponse> getAllReviews() {
        return reviewRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 회원별 리뷰 조회
     */
    @Override
    public List<ReviewResponse> getReviewsByMember(String memberId) {
        return reviewRepository.findByMember_MemberId(memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 리뷰 수정
     */
    @Override
    public ReviewResponse updateReview(Integer reviewId, ReviewUpdateRequest request) {

        Review existingReview = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        existingReview.updateReview(request.getRating(), request.getContent());

        Review updatedReview = reviewRepository.save(existingReview);

        return toResponse(updatedReview);
    }

    @Override
    public void deleteReview(Integer reviewId) {
        Review review = reviewRepository.findById(reviewId).orElse(null);

        if (review == null) {
            throw new IllegalArgumentException("삭제할 리뷰가 없습니다.");
        }

        reviewRepository.delete(review);
    }
    /**
     * 디자이너 평균 평점 조회
     */
    @Override
    public Double getAverageRatingByDesigner(Integer designerId) {

        Double averageRating = reviewRepository.findAverageRatingByDesignerId(designerId);

        // 리뷰가 하나도 없으면 0.0 반환
        if (averageRating == null) {
            return 0.0;
        }

        // 소수점 1자리 반올림
        return Math.round(averageRating * 10) / 10.0;
    }
    /**
     * 평균 평점 기준 디자이너 TOP3 조회
     *
     * 조건
     * 1. 최소 리뷰 2개 이상
     * 2. 평균 평점 높은 순
     * 3. 리뷰 수 많은 순
     */
    @Override
    public List<DesignerRankingResponse> getTop3Designers() {

        List<DesignerRankingResponse> rankingList =
                reviewRepository.findTopDesignersByAverageRatingAndReviewCount(2L);

        return rankingList.stream()
                .limit(3)
                .toList();
    }

    /**
     * 월별 리뷰 수 / 평균 평점 집계 조회
     */
    @Override
    public List<MonthlyReviewStatResponse> getMonthlyReviewStats() {
        return reviewRepository.findMonthlyReviewStats();
    }

    /**
     * 특정 기간 월별 리뷰 수 / 평균 평점 집계 조회
     */
    @Override
    public List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(LocalDate startDate, LocalDate endDate) {

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        return reviewRepository.findMonthlyReviewStatsByPeriod(startDateTime, endDateTime);
    }

    /**
     * 미용실 랭킹 조회
     *
     * 조건
     * 1. 최소 리뷰 2개 이상
     * 2. 평균 평점 높은 순
     * 3. 리뷰 수 많은 순
     */
    @Override
    public List<SalonRankingResponse> getTopSalons() {

        return reviewRepository.findTopSalonsByAverageRating(2L).stream()
                .limit(3)
                .toList();
    }
    /**
     * Review 엔티티를 ReviewResponse DTO로 변환
     */
    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getReservation().getReservationId(),
                review.getMember().getMemberId(),
                review.getDesigner().getDesignerId(),
                review.getRating(),
                review.getContent(),
                review.getReplyContent(),
                review.getReplyCreatedAt(),
                review.getCreatedAt()
        );
    }
}