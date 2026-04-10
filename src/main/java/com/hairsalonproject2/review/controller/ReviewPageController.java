package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class ReviewPageController {

    private static final int REVIEWS_PER_PAGE = 6;

    private final ReviewService reviewService;
    private final ReservationService reservationService;
    private final ReviewRepository reviewRepository;

    @GetMapping("/reviews")
    public String reviewListPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "false") boolean searched,
                                 @RequestParam(required = false) String region,
                                 @RequestParam(required = false) Integer salonId,
                                 @RequestParam(required = false) Integer designerId,
                                 @RequestParam(defaultValue = "latest") String sortBy,
                                 Model model) {
        String loginMemberId = userDetails == null ? null : userDetails.getMember().getMemberId();
        String selectedRegion = normalizeText(region);
        List<ReviewResponse> latestReviews = reviewService.getAllReviews(loginMemberId, null, "latest");
        List<String> regionOptions = latestReviews.stream()
                .map(review -> extractRegion(review.getSalonAddress()))
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        List<SalonFilterOption> salonOptions = latestReviews.stream()
                .filter(review -> review.getSalonId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        ReviewResponse::getSalonId,
                        review -> new SalonFilterOption(
                                review.getSalonId(),
                                review.getSalonName(),
                                extractRegion(review.getSalonAddress())
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(SalonFilterOption::getRegion)
                        .thenComparing(SalonFilterOption::getSalonName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<DesignerFilterOption> designerOptions = latestReviews.stream()
                .filter(review -> review.getDesignerId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        ReviewResponse::getDesignerId,
                        review -> new DesignerFilterOption(
                                review.getDesignerId(),
                                review.getDesignerName(),
                                review.getSalonId()
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(DesignerFilterOption::getDesignerName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Double selectedDesignerAverage = designerId == null
                ? null
                : reviewService.getAverageRatingByDesigner(designerId);

        List<ReviewResponse> allReviews = searched
                ? filterReviews(
                        reviewService.getAllReviews(loginMemberId, null, sortBy),
                        selectedRegion,
                        salonId,
                        designerId
                )
                : Collections.emptyList();
        int totalReviews = allReviews.size();
        int totalPages = searched && totalReviews > 0
                ? (int) Math.ceil((double) totalReviews / REVIEWS_PER_PAGE)
                : 0;
        int currentPage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
        int fromIndex = totalReviews == 0 ? 0 : (currentPage - 1) * REVIEWS_PER_PAGE;
        int toIndex = totalReviews == 0 ? 0 : Math.min(fromIndex + REVIEWS_PER_PAGE, totalReviews);

        List<ReviewResponse> pagedReviews = totalReviews == 0
                ? Collections.emptyList()
                : allReviews.subList(fromIndex, toIndex);

        model.addAttribute("reviews", pagedReviews);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", totalPages == 0
                ? Collections.emptyList()
                : java.util.stream.IntStream.rangeClosed(1, totalPages).boxed().toList());
        model.addAttribute("currentSort", sortBy);
        model.addAttribute("selectedRegion", selectedRegion);
        model.addAttribute("selectedSalonId", salonId);
        model.addAttribute("selectedDesignerId", designerId);
        model.addAttribute("regionOptions", regionOptions);
        model.addAttribute("designerOptions", designerOptions);
        model.addAttribute("salonOptions", salonOptions);
        model.addAttribute("totalReviewCount", totalReviews);
        model.addAttribute("selectedDesignerAverage", selectedDesignerAverage);
        model.addAttribute("isLoggedIn", userDetails != null);
        model.addAttribute("searched", searched);
        return "review/list";
    }

    @GetMapping("/reviews/{reviewId}")
    public String reviewDetailPage(@PathVariable Integer reviewId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   Model model) {
        ReviewDetailResponse review = reviewService.getReviewDetail(reviewId, getLoginMemberId(userDetails));
        model.addAttribute("review", review);
        model.addAttribute("canManageReview", canManageReview(userDetails, review));
        model.addAttribute("isLoggedIn", userDetails != null);
        return "review/detail";
    }

    @GetMapping("/reviews/{reviewId}/edit")
    public String reviewEditPage(@PathVariable Integer reviewId,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 Model model) {
        ReviewDetailResponse review = reviewService.getReviewDetail(reviewId, getLoginMemberId(userDetails));
        if (!canManageReview(userDetails, review)) {
            throw new AccessDeniedException("You can only edit your own review.");
        }

        model.addAttribute("review", review);
        return "review/edit";
    }

    @GetMapping("/reviews/write")
    public String reviewWritePage(@RequestParam Integer reservationId,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  Model model) {
        if (userDetails == null) {
            return "redirect:/members/login";
        }

        ReservationResponse reservation = reservationService.getReservation(reservationId);
        if (!reservation.getMemberId().equals(userDetails.getMember().getMemberId())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "You can only write a review for your own reservation.");
        }
        if (!isReviewableReservation(reservation)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Only completed reservations can be reviewed.");
        }

        if (reviewRepository.findByReservation_ReservationId(reservationId).isPresent()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "A review has already been written for this reservation.");
        }

        model.addAttribute("reservationId", reservationId);
        model.addAttribute("reservation", reservation);
        return "review/write";
    }

    private boolean canManageReview(CustomUserDetails userDetails, ReviewDetailResponse review) {
        if (userDetails == null) {
            return false;
        }

        return userDetails.getMember().getRole().name().equals("ADMIN")
                || review.getMemberId().equals(userDetails.getMember().getMemberId());
    }

    private String getLoginMemberId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getMember().getMemberId();
    }

    private boolean isReviewableReservation(ReservationResponse reservation) {
        return reservation.getStatus() == ReservationStatus.COMPLETED;
    }

    private List<ReviewResponse> filterReviews(List<ReviewResponse> reviews,
                                               String selectedRegion,
                                               Integer salonId,
                                               Integer designerId) {
        return reviews.stream()
                .filter(review -> selectedRegion.isBlank()
                        || Objects.equals(extractRegion(review.getSalonAddress()), selectedRegion))
                .filter(review -> salonId == null || Objects.equals(review.getSalonId(), salonId))
                .filter(review -> designerId == null || Objects.equals(review.getDesignerId(), designerId))
                .toList();
    }

    private String extractRegion(String address) {
        String normalizedAddress = normalizeText(address);
        if (normalizedAddress.isBlank()) {
            return "";
        }

        String token = normalizedAddress.split("\\s+")[0];
        return normalizeRegionName(token);
    }

    private String normalizeRegionName(String rawRegion) {
        String normalized = normalizeText(rawRegion);
        if (normalized.isBlank()) {
            return "";
        }

        String[] suffixes = {"특별자치도", "특별자치시", "광역시", "특별시", "자치도", "자치시", "도", "시"};
        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }

        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class SalonFilterOption {
        private final Integer salonId;
        private final String salonName;
        private final String region;

        public SalonFilterOption(Integer salonId, String salonName, String region) {
            this.salonId = salonId;
            this.salonName = salonName;
            this.region = region == null ? "" : region;
        }

        public Integer getSalonId() {
            return salonId;
        }

        public String getSalonName() {
            return salonName;
        }

        public String getRegion() {
            return region;
        }
    }

    public static final class DesignerFilterOption {
        private final Integer designerId;
        private final String designerName;
        private final Integer salonId;

        public DesignerFilterOption(Integer designerId, String designerName, Integer salonId) {
            this.designerId = designerId;
            this.designerName = designerName;
            this.salonId = salonId;
        }

        public Integer getDesignerId() {
            return designerId;
        }

        public String getDesignerName() {
            return designerName;
        }

        public Integer getSalonId() {
            return salonId;
        }
    }
}
