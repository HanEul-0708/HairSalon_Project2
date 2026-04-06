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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewImageRepository reviewImageRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException("예약이 존재하지 않습니다."));

        if (!reservation.getMember().getMemberId().equals(loginMemberId)) {
            throw new IllegalArgumentException("본인 예약만 리뷰를 작성할 수 있습니다.");
        }

        if (reservation.getStatus() != ReservationStatus.COMPLETED) {
            throw new IllegalArgumentException("시술 완료된 예약만 리뷰를 작성할 수 있습니다.");
        }

        if (reviewRepository.findByReservation_ReservationId(request.getReservationId()).isPresent()) {
            throw new IllegalArgumentException("이미 리뷰가 작성된 예약입니다.");
        }

        Review review = Review.builder()
                .reservation(reservation)
                .member(reservation.getMember())
                .designer(reservation.getDesigner())
                .rating(request.getRating())
                .content(request.getContent())
                .build();

        Review savedReview = reviewRepository.save(review);
        return toResponse(savedReview);
    }

    @Override
    public ReviewResponse getReview(Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        return toResponse(review);
    }

    @Override
    public ReviewDetailResponse getReviewDetail(Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        List<ReviewImageResponse> images = reviewImageRepository.findByReview_ReviewId(reviewId).stream()
                .map(image -> new ReviewImageResponse(
                        image.getImageId(),
                        image.getImageUrl(),
                        image.getSortOrder()
                ))
                .toList();

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

    @Override
    public List<ReviewResponse> getAllReviews() {
        return reviewRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ReviewResponse> getRecentReviews() {
        return reviewRepository.findTop3ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ReviewResponse> getReviewsByMember(String memberId) {
        return reviewRepository.findByMember_MemberId(memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Integer reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        review.updateReview(request.getRating(), request.getContent());
        Review updatedReview = reviewRepository.save(review);
        return toResponse(updatedReview);
    }

    @Override
    @Transactional
    public void deleteReview(Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰가 존재하지 않습니다."));

        reviewRepository.delete(review);
    }

    @Override
    public Double getAverageRatingByDesigner(Integer designerId) {
        return reviewRepository.findAverageRatingByDesignerId(designerId);
    }

    @Override
    public List<DesignerRankingResponse> getTop3Designers() {
        return reviewRepository.findTopDesignersByAverageRatingAndReviewCount(1L)
                .stream()
                .limit(3)
                .toList();
    }

    @Override
    public List<MonthlyReviewStatResponse> getMonthlyReviewStats() {
        return reviewRepository.findMonthlyReviewStats();
    }

    @Override
    public List<MonthlyReviewStatResponse> getMonthlyReviewStatsByPeriod(LocalDate startDate, LocalDate endDate) {
        return reviewRepository.findMonthlyReviewStatsByPeriod(
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59)
        );
    }

    @Override
    public List<SalonRankingResponse> getTopSalons() {
        return reviewRepository.findTopSalonsByAverageRating(1L)
                .stream()
                .limit(3)
                .toList();
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getReviewId(),
                review.getReservation().getReservationId(),
                review.getMember().getMemberId(),
                review.getMember().getName(),
                review.getDesigner().getDesignerId(),
                review.getDesigner().getName(),
                review.getReservation().getDesigner().getSalon().getName(),
                review.getReservation().getSalonService().getName(),
                review.getRating(),
                review.getContent(),
                review.getReplyContent(),
                review.getReplyCreatedAt(),
                review.getCreatedAt()
        );
    }
}
