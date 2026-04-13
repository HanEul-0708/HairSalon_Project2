package com.hairsalonproject2.review.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.common.util.AddressRegionUtils;
import com.hairsalonproject2.designer.projection.DesignerRatingRow;
import com.hairsalonproject2.designer.repository.DesignerRepository;
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
import com.hairsalonproject2.salon.service.SalonRatingSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MemberRepository memberRepository;
    private final DesignerRepository designerRepository;
    private final SalonRatingSyncService salonRatingSyncService;

    @Override
    @Transactional
    public ReviewResponse createReview(String loginMemberId, ReviewCreateRequest request) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (!Objects.equals(reservation.getMember().getMemberId(), loginMemberId)) {
            throw new IllegalArgumentException("Only the reservation owner can write a review.");
        }

        if (!isReviewableReservation(reservation)) {
            throw new IllegalArgumentException("Only completed reservations can be reviewed.");
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

        Review savedReview = reviewRepository.save(review);
        salonRatingSyncService.syncSalonStats(savedReview.getDesigner().getSalon().getSalonId());
        return toResponse(savedReview, loginMemberId);
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
                .sorted(latestReviewFirst())
                .toList();
    }

    @Override
    public List<ReviewResponse> getReviewsByDesignerMember(String memberId, String loginMemberId) {
        return designerRepository.findByMember_MemberId(memberId)
                .map(designer -> reviewRepository.findByDesigner_Salon_SalonId(designer.getSalon().getSalonId()).stream()
                        .map(review -> toResponse(review, loginMemberId))
                        .sorted(latestReviewFirst())
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(String loginMemberId, boolean isAdmin, Integer reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));

        validateReviewAccess(review, loginMemberId, isAdmin);
        review.updateReview(request.getRating(), request.getContent());
        Review savedReview = reviewRepository.save(review);
        salonRatingSyncService.syncSalonStats(savedReview.getDesigner().getSalon().getSalonId());
        return toResponse(savedReview, loginMemberId);
    }

    @Override
    @Transactional
    public void deleteReview(String loginMemberId, boolean isAdmin, Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));

        validateReviewAccess(review, loginMemberId, isAdmin);
        Integer salonId = review.getDesigner().getSalon().getSalonId();
        reviewRepository.delete(review);
        salonRatingSyncService.syncSalonStats(salonId);
    }

    @Override
    public Double getAverageRatingByDesigner(Integer designerId) {
        return reviewRepository.findAverageRatingByDesignerId(designerId);
    }

    @Override
    public List<DesignerRankingResponse> getTop3Designers() {
        return getTop3Designers(null, null, null);
    }

    @Override
    public List<DesignerRankingResponse> getTop3Designers(String city, String district, String neighborhood) {
        Map<Integer, DesignerRatingRow> ratings = designerRepository.findDesignerRatingRows().stream()
                .collect(Collectors.toMap(
                        DesignerRatingRow::getDesignerId,
                        row -> row
                ));

        return designerRepository.findAll().stream()
                .filter(designer -> matchesRegion(
                        designer.getSalon().getAddress(),
                        designer.getSalon().getRoadAddress(),
                        city,
                        district,
                        neighborhood
                ))
                .map(designer -> {
                    DesignerRatingRow row = ratings.get(designer.getDesignerId());
                    return new DesignerRankingResponse(
                            designer.getDesignerId(),
                            designer.getName(),
                            designer.getSalon().getName(),
                            designer.getCareerYears(),
                            row == null ? 0D : row.getAverageRating(),
                            row == null ? 0L : row.getReviewCount()
                    );
                })
                .filter(row -> row.getReviewCount() >= 1L)
                .sorted(Comparator.comparing(DesignerRankingResponse::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DesignerRankingResponse::getReviewCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DesignerRankingResponse::getCareerYears, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(
                                DesignerRankingResponse::getDesignerName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                        ))
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
        return getRecentReviews(null, null, null);
    }

    @Override
    public List<ReviewResponse> getRecentReviews(String city, String district, String neighborhood) {
        return reviewRepository.findAll().stream()
                .filter(review -> matchesRegion(
                        review.getDesigner().getSalon().getAddress(),
                        review.getDesigner().getSalon().getRoadAddress(),
                        city,
                        district,
                        neighborhood
                ))
                .map(review -> toResponse(review, null))
                .sorted(latestReviewFirst())
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
        if (!isAdmin && !Objects.equals(review.getMember().getMemberId(), loginMemberId)) {
            throw new AccessDeniedException("You can only manage your own review.");
        }
    }

    private boolean isReviewableReservation(Reservation reservation) {
        return reservation.getStatus() == ReservationStatus.COMPLETED;
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
                review.getDesigner().getSalon().getSalonId(),
                review.getDesigner().getSalon().getName(),
                review.getDesigner().getSalon().getAddress(),
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
            return Comparator.comparing(
                            ReviewResponse::getRating,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(latestReviewFirst());
        }

        if ("likes".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(
                            ReviewResponse::getLikeCount,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(latestReviewFirst());
        }

        return latestReviewFirst();
    }

    private Comparator<ReviewResponse> latestReviewFirst() {
        return Comparator.comparing(
                ReviewResponse::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        );
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

    private boolean matchesRegion(String address, String roadAddress, String city, String district, String neighborhood) {
        return AddressRegionUtils.matches(address, city, district, neighborhood)
                || AddressRegionUtils.matches(roadAddress, city, district, neighborhood);
    }
}
