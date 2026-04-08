package com.hairsalonproject2.review.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.review.dto.DesignerRankingResponse;
import com.hairsalonproject2.review.dto.MonthlyReviewStatResponse;
import com.hairsalonproject2.review.dto.ReviewCreateRequest;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewImageResponse;
import com.hairsalonproject2.review.dto.ReviewLikeToggleResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.dto.ReviewUpdateRequest;
import com.hairsalonproject2.review.dto.SalonRankingResponse;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.review.entity.ReviewLike;
import com.hairsalonproject2.review.repository.ReviewImageRepository;
import com.hairsalonproject2.review.repository.ReviewLikeRepository;
import com.hairsalonproject2.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (!reservation.getMember().getMemberId().equals(loginMemberId)) {
            throw new IllegalArgumentException("Only the reservation owner can write a review.");
        }

        if (!isReviewableReservation(reservation)) {
            throw new IllegalArgumentException("Only completed or same-day reservations can be reviewed.");
        }

        if (reviewRepository.findByReservation_ReservationId(request.getReservationId()).isPresent()) {
            throw new IllegalArgumentException("Only one review can be written per reservation.");
        }

        Review review = Review.builder()
                .reservation(reservation)
                .member(reservation.getMember())
                .designer(reservation.getDesigner())
                .rating(request.getRating())
                .content(request.getContent())
                .build();

        return toResponse(reviewRepository.save(review), loginMemberId);
    }

    @Override
    public ReviewResponse getReview(Integer reviewId, String loginMemberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        return toResponse(review, loginMemberId);
    }

    @Override
    public ReviewDetailResponse getReviewDetail(Integer reviewId, String loginMemberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));

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
                review.getReservation().getReservationDate(),
                review.getReservation().getReservationTime(),
                review.getMember().getMemberId(),
                review.getMember().getName(),
                review.getDesigner().getDesignerId(),
                review.getDesigner().getName(),
                review.getReservation().getSalonService().getName(),
                review.getRating(),
                review.getContent(),
                review.getReplyContent(),
                review.getReplyCreatedAt(),
                review.getCreatedAt(),
                safeLikeCount(review),
                isLikedByCurrentUser(review.getReviewId(), loginMemberId),
                images
        );
    }

    @Override
    public List<ReviewResponse> getAllReviews(String loginMemberId, Integer designerId, String sortBy) {
        List<Review> reviews = designerId == null
                ? reviewRepository.findAll()
                : reviewRepository.findByDesigner_DesignerId(designerId);

        return reviews.stream()
                .map(review -> toResponse(review, loginMemberId))
                .sorted(resolveComparator(sortBy))
                .toList();
    }

    @Override
    public List<ReviewResponse> getReviewsByMember(String memberId, String loginMemberId) {
        return reviewRepository.findByMember_MemberId(memberId).stream()
                .map(review -> toResponse(review, loginMemberId))
                .sorted(Comparator.comparing(ReviewResponse::getCreatedAt).reversed())
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(String loginMemberId, boolean isAdmin, Integer reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));

        validateReviewAccess(review, loginMemberId, isAdmin);
        review.updateReview(request.getRating(), request.getContent());

        return toResponse(reviewRepository.save(review), loginMemberId);
    }

    @Override
    @Transactional
    public void deleteReview(String loginMemberId, boolean isAdmin, Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));

        validateReviewAccess(review, loginMemberId, isAdmin);
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
        return reviewRepository.findTopSalonsByAverageRating(1L);
    }

    @Override
    public List<ReviewResponse> getRecentReviews() {
        return reviewRepository.findAll().stream()
                .map(review -> toResponse(review, null))
                .sorted(Comparator.comparing(ReviewResponse::getCreatedAt).reversed())
                .limit(3)
                .toList();
    }

    @Override
    @Transactional
    public ReviewLikeToggleResponse toggleLike(Integer reviewId, String loginMemberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        Member member = memberRepository.findById(loginMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found."));

        ReviewLike existingLike = reviewLikeRepository.findByReview_ReviewIdAndMember_MemberId(reviewId, loginMemberId)
                .orElse(null);

        boolean liked;
        if (existingLike != null) {
            reviewLikeRepository.delete(existingLike);
            review.decreaseLikeCount();
            liked = false;
        } else {
            reviewLikeRepository.save(ReviewLike.builder()
                    .review(review)
                    .member(member)
                    .build());
            review.increaseLikeCount();
            liked = true;
        }

        Review savedReview = reviewRepository.save(review);
        return new ReviewLikeToggleResponse(savedReview.getReviewId(), safeLikeCount(savedReview), liked);
    }

    public void validateReviewAccess(Integer reviewId, String loginMemberId, boolean isAdmin) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        validateReviewAccess(review, loginMemberId, isAdmin);
    }

    private void validateReviewAccess(Review review, String loginMemberId, boolean isAdmin) {
        if (!isAdmin && !review.getMember().getMemberId().equals(loginMemberId)) {
            throw new AccessDeniedException("You can only manage your own review.");
        }
    }

    private boolean isReviewableReservation(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return false;
        }

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            return true;
        }

        return !reservation.getReservationDate().isAfter(LocalDateTime.now().toLocalDate());
    }

    private ReviewResponse toResponse(Review review, String loginMemberId) {
        String thumbnailImageUrl = review.getReviewImages().stream()
                .sorted(Comparator.comparing(
                        image -> image.getSortOrder() == null ? Integer.MAX_VALUE : image.getSortOrder()
                ))
                .map(image -> image.getImageUrl())
                .findFirst()
                .orElse(null);

        return new ReviewResponse(
                review.getReviewId(),
                review.getReservation().getReservationId(),
                review.getReservation().getReservationDate(),
                review.getReservation().getReservationTime(),
                review.getMember().getMemberId(),
                review.getMember().getName(),
                review.getDesigner().getDesignerId(),
                review.getDesigner().getName(),
                review.getDesigner().getSalon().getName(),
                review.getReservation().getSalonService().getName(),
                review.getRating(),
                review.getContent(),
                review.getReplyContent(),
                review.getReplyCreatedAt(),
                review.getCreatedAt(),
                thumbnailImageUrl,
                safeLikeCount(review),
                isLikedByCurrentUser(review.getReviewId(), loginMemberId)
        );
    }

    private Comparator<ReviewResponse> resolveComparator(String sortBy) {
        if ("rating".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(ReviewResponse::getRating).reversed()
                    .thenComparing(ReviewResponse::getCreatedAt, Comparator.reverseOrder());
        }

        if ("likes".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(ReviewResponse::getLikeCount).reversed()
                    .thenComparing(ReviewResponse::getCreatedAt, Comparator.reverseOrder());
        }

        return Comparator.comparing(ReviewResponse::getCreatedAt).reversed();
    }

    private boolean isLikedByCurrentUser(Integer reviewId, String loginMemberId) {
        if (loginMemberId == null || loginMemberId.isBlank()) {
            return false;
        }
        return reviewLikeRepository.existsByReview_ReviewIdAndMember_MemberId(reviewId, loginMemberId);
    }

    private Integer safeLikeCount(Review review) {
        return review.getLikeCount() == null ? 0 : review.getLikeCount();
    }
}
