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

    private static final String REVIEW_NOT_FOUND = "Review not found.";
    private static final String RESERVATION_NOT_FOUND = "Reservation not found.";
    private static final String MEMBER_NOT_FOUND = "Member not found.";
    private static final String ONLY_OWNER_CAN_WRITE = "Only the reservation owner can write a review.";
    private static final String REVIEW_AFTER_VISIT_ONLY = "You can write a review only after the reserved time has passed.";
    private static final String REVIEW_ALREADY_EXISTS = "Only one review can be written per reservation.";
    private static final String VISITOR_TOKEN_REQUIRED = "Visitor token is required.";

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException(RESERVATION_NOT_FOUND));

        if (!reservation.getMember().getMemberId().equals(loginMemberId)) {
            throw new IllegalArgumentException(ONLY_OWNER_CAN_WRITE);
        }

        if (!isReviewableReservation(reservation)) {
            throw new IllegalArgumentException(REVIEW_AFTER_VISIT_ONLY);
        }

        if (reviewRepository.findByReservation_ReservationId(request.getReservationId()).isPresent()) {
            throw new IllegalArgumentException(REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.builder()
                .reservation(reservation)
                .member(reservation.getMember())
                .designer(reservation.getDesigner())
                .rating(request.getRating())
                .content(request.getContent())
                .build();

        return toResponse(reviewRepository.save(review), loginMemberId, null);
    }

    @Override
    public ReviewResponse getReview(Integer reviewId, String loginMemberId, String visitorToken) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(REVIEW_NOT_FOUND));
        return toResponse(review, loginMemberId, visitorToken);
    }

    @Override
    public ReviewDetailResponse getReviewDetail(Integer reviewId, String loginMemberId, String visitorToken) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(REVIEW_NOT_FOUND));

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
                isLikedByCurrentUser(review.getReviewId(), loginMemberId, visitorToken),
                images
        );
    }

    @Override
    public List<ReviewResponse> getAllReviews(String loginMemberId, String visitorToken, Integer designerId, String sortBy) {
        List<Review> reviews = designerId == null
                ? reviewRepository.findAll()
                : reviewRepository.findByDesigner_DesignerId(designerId);

        return reviews.stream()
                .map(review -> toResponse(review, loginMemberId, visitorToken))
                .sorted(resolveComparator(sortBy))
                .toList();
    }

    @Override
    public List<ReviewResponse> getReviewsByMember(String memberId, String loginMemberId, String visitorToken) {
        return reviewRepository.findByMember_MemberId(memberId).stream()
                .map(review -> toResponse(review, loginMemberId, visitorToken))
                .sorted(Comparator.comparing(ReviewResponse::getCreatedAt).reversed())
                .toList();
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(String loginMemberId, boolean isAdmin, Integer reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(REVIEW_NOT_FOUND));

        validateReviewAccess(review, loginMemberId, isAdmin);
        review.updateReview(request.getRating(), request.getContent());

        return toResponse(reviewRepository.save(review), loginMemberId, null);
    }

    @Override
    @Transactional
    public void deleteReview(String loginMemberId, boolean isAdmin, Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(REVIEW_NOT_FOUND));

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
    public ReviewLikeToggleResponse toggleLike(Integer reviewId, String loginMemberId, String visitorToken) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(REVIEW_NOT_FOUND));

        ReviewLike existingLike = findExistingLike(reviewId, loginMemberId, visitorToken);
        if (existingLike != null) {
            reviewLikeRepository.delete(existingLike);
            review.decreaseLikeCount();
            Review savedReview = reviewRepository.save(review);
            return new ReviewLikeToggleResponse(savedReview.getReviewId(), safeLikeCount(savedReview), false);
        }

        reviewLikeRepository.save(createReviewLike(review, loginMemberId, visitorToken));
        review.increaseLikeCount();
        Review savedReview = reviewRepository.save(review);
        return new ReviewLikeToggleResponse(savedReview.getReviewId(), safeLikeCount(savedReview), true);
    }

    public void validateReviewAccess(Integer reviewId, String loginMemberId, boolean isAdmin) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(REVIEW_NOT_FOUND));
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

        return !LocalDateTime.of(
                reservation.getReservationDate(),
                reservation.getReservationTime()
        ).isAfter(LocalDateTime.now());
    }

    private ReviewLike findExistingLike(Integer reviewId, String loginMemberId, String visitorToken) {
        if (loginMemberId != null && !loginMemberId.isBlank()) {
            return reviewLikeRepository.findByReview_ReviewIdAndMember_MemberId(reviewId, loginMemberId)
                    .orElse(null);
        }

        if (visitorToken != null && !visitorToken.isBlank()) {
            return reviewLikeRepository.findByReview_ReviewIdAndVisitorToken(reviewId, visitorToken)
                    .orElse(null);
        }

        return null;
    }

    private ReviewLike createReviewLike(Review review, String loginMemberId, String visitorToken) {
        if (loginMemberId != null && !loginMemberId.isBlank()) {
            Member member = memberRepository.findById(loginMemberId)
                    .orElseThrow(() -> new IllegalArgumentException(MEMBER_NOT_FOUND));

            return ReviewLike.builder()
                    .review(review)
                    .member(member)
                    .build();
        }

        if (visitorToken == null || visitorToken.isBlank()) {
            throw new IllegalArgumentException(VISITOR_TOKEN_REQUIRED);
        }

        return ReviewLike.builder()
                .review(review)
                .visitorToken(visitorToken)
                .build();
    }

    private ReviewResponse toResponse(Review review, String loginMemberId, String visitorToken) {
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
                isLikedByCurrentUser(review.getReviewId(), loginMemberId, visitorToken)
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

    private boolean isLikedByCurrentUser(Integer reviewId, String loginMemberId, String visitorToken) {
        if (loginMemberId != null && !loginMemberId.isBlank()) {
            return reviewLikeRepository.existsByReview_ReviewIdAndMember_MemberId(reviewId, loginMemberId);
        }

        if (visitorToken != null && !visitorToken.isBlank()) {
            return reviewLikeRepository.existsByReview_ReviewIdAndVisitorToken(reviewId, visitorToken);
        }

        return false;
    }

    private Integer safeLikeCount(Review review) {
        return review.getLikeCount() == null ? 0 : review.getLikeCount();
    }
}
