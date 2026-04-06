package com.hairsalonproject2.review.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.MonthlyReviewStatResponse;
import com.hairsalonproject2.review.dto.ReviewCreateRequest;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewImageResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.dto.ReviewUpdateRequest;
import com.hairsalonproject2.review.dto.SalonRankingResponse;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.repository.ReviewImageRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ReviewService 구현체
 *
 * 설명:
 * - 리뷰 작성 / 조회 / 수정 / 삭제
 * - 리뷰 상세 조회(이미지 포함)
 * - 디자이너 평점 평균 조회
 * - 월별 리뷰 통계 조회
 * - 디자이너 / 미용실 랭킹 조회
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    /**
     * 리뷰 Repository
     */
    private final ReviewRepository reviewRepository;

    /**
     * 예약 Repository
     */
    private final ReservationRepository reservationRepository;

    /**
     * 리뷰 이미지 Repository
     */
    private final ReviewImageRepository reviewImageRepository;

    /**
     * 리뷰 작성
     *
     * 처리 순서
     * 1. 예약 존재 여부 확인
     * 2. 로그인 회원이 예약 주인인지 확인
     * 3. 시술 완료(COMPLETED) 상태인지 확인
     * 4. 이미 리뷰가 작성된 예약인지 확인
     * 5. 리뷰 엔티티 생성 후 저장
     * 6. 응답 DTO 반환
     */
    @Override
    @Transactional
    public ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request) {

        // 1. 예약 조회
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException("예약이 존재하지 않습니다."));

        // 2. 로그인한 회원이 실제 예약자인지 확인
        if (!reservation.getMember().getMemberId().equals(loginMemberId)) {
            throw new IllegalArgumentException("본인 예약만 리뷰를 작성할 수 있습니다.");
        }

        // 3. 시술 완료된 예약인지 확인
        if (reservation.getStatus() != ReservationStatus.COMPLETED) {
            throw new IllegalArgumentException("시술 완료된 예약만 리뷰를 작성할 수 있습니다.");
        }

        // 4. 이미 리뷰가 작성되었는지 확인
        if (reviewRepository.findByReservation_ReservationId(request.getReservationId()).isPresent()) {
            throw new IllegalArgumentException("이미 리뷰가 작성된 예약입니다.");
        }

        // 5. 리뷰 엔티티 생성
        Review review = Review.builder()
                .reservation(reservation)
                .member(reservation.getMember())
                .designer(reservation.getDesigner())
                .rating(request.getRating())
                .content(request.getContent())
                .build();

        // 6. 저장
        Review savedReview = reviewRepository.save(review);

        // 7. 응답 반환
        return toResponse(savedReview);
    }

    /**
     * 리뷰 단건 조회
     */
    @Override
    public ReviewResponse getReview(Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        return toResponse(review);
    }

    /**
     * 리뷰 상세 조회
     *
     * 리뷰 기본 정보와 연결된 이미지 목록을 함께 반환
     */
    @Override
    public ReviewDetailResponse getReviewDetail(Integer reviewId) {

        // 1. 리뷰 조회
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        // 2. 리뷰 이미지 목록 조회
        List<ReviewImageResponse> images = reviewImageRepository.findByReview_ReviewId(reviewId).stream()
                .map(image -> new ReviewImageResponse(
                        image.getImageId(),
                        image.getImageUrl(),
                        image.getSortOrder()
                ))
                .toList();

        // 3. 상세 응답 DTO 반환
        return new ReviewDetailResponse(
                review.getReviewId(),
                review.getReservation().getReservationId(),
                review.getMember().getMemberId(),
                review.getMember().getName(),
                review.getDesigner().getDesignerId(),
                review.getDesigner().getName(),
                review.getRating(),
                review.getContent(),
                review.getReplyContent(),
                review.getReplyCreatedAt(),
                review.getCreatedAt(),
                images
        );
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
     * 회원별 리뷰 목록 조회
     */
    @Override
    public List<ReviewResponse> getReviewsByMember(String memberId) {
        return reviewRepository.findByMember_MemberId(memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 리뷰 수정
     *
     * 현재는 리뷰 내용 / 평점만 수정
     */
    @Override
    @Transactional
    public ReviewResponse updateReview(String loginMemberId, boolean isAdmin, Integer reviewId, ReviewUpdateRequest request) {

        // 1. 리뷰 조회
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        validateReviewAccess(review, loginMemberId, isAdmin);

        // 2. 리뷰 수정
        review.updateReview(request.getRating(), request.getContent());

        // 3. 저장 후 응답 반환
        Review updatedReview = reviewRepository.save(review);
        return toResponse(updatedReview);
    }

    /**
     * 리뷰 삭제
     */
    @Override
    @Transactional
    public void deleteReview(String loginMemberId, boolean isAdmin, Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        validateReviewAccess(review, loginMemberId, isAdmin);

        reviewRepository.delete(review);
    }

    /**
     * 특정 디자이너 평균 평점 조회
     */
    @Override
    public Double getAverageRatingByDesigner(Integer designerId) {
        return reviewRepository.findAverageRatingByDesignerId(designerId);
    }

    /**
     * 상위 디자이너 3명 조회
     */
    @Override
    public List<DesignerRankingResponse> getTop3Designers() {
        return reviewRepository.findTopDesignersByAverageRatingAndReviewCount(1L)
                .stream()
                .limit(3)
                .toList();
    }

    /**
     * 전체 월별 리뷰 통계 조회
     */
    @Override
    public List<MonthlyReviewStatResponse> getMonthlyReviewStats() {
        return reviewRepository.findMonthlyReviewStats();
    }

    /**
     * 특정 기간의 월별 리뷰 통계 조회
     */
    @Override
    public List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(LocalDate startDate, LocalDate endDate) {
        return reviewRepository.findMonthlyReviewStatsByPeriod(
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59)
        );
    }

    /**
     * 상위 미용실 랭킹 조회
     */
    @Override
    public List<SalonRankingResponse> getTopSalons() {
        return reviewRepository.findTopSalonsByAverageRating(1L);
    }

    /**
     * Review 엔티티 -> ReviewResponse DTO 변환
     */
    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getReservation().getReservationId(),
                review.getMember().getMemberId(),
                review.getMember().getName(),
                review.getDesigner().getDesignerId(),
                review.getDesigner().getName(),
                review.getRating(),
                review.getContent(),
                review.getReplyContent(),
                review.getReplyCreatedAt(),
                review.getCreatedAt()
        );
    }

    public void validateReviewAccess(Integer reviewId, String loginMemberId, boolean isAdmin) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));
        validateReviewAccess(review, loginMemberId, isAdmin);
    }

    private void validateReviewAccess(Review review, String loginMemberId, boolean isAdmin) {
        if (!isAdmin && !review.getMember().getMemberId().equals(loginMemberId)) {
            throw new AccessDeniedException("본인 리뷰만 수정하거나 삭제할 수 있습니다.");
        }
    }
}
