package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.common.support.PageUtils;
import com.hairsalonproject2.common.util.AddressRegionUtils;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
                                 @RequestParam(required = false) String city,
                                 @RequestParam(required = false) String district,
                                 @RequestParam(required = false) String neighborhood,
                                 @RequestParam(required = false) Integer salonId,
                                 @RequestParam(required = false) Integer designerId,
                                 @RequestParam(defaultValue = "latest") String sortBy,
                                 Model model) {
        String loginMemberId = userDetails == null ? null : userDetails.getMember().getMemberId();
        String selectedCity = normalizeText(city);
        String selectedDistrict = normalizeText(district);
        String selectedNeighborhood = normalizeText(neighborhood);

        List<ReviewResponse> latestReviews = reviewService.getAllReviews(loginMemberId, null, "latest");
        List<String> addresses = latestReviews.stream()
                .map(ReviewResponse::getSalonAddress)
                .filter(address -> address != null && !address.isBlank())
                .toList();

        List<SalonFilterOption> salonOptions = latestReviews.stream()
                .filter(review -> review.getSalonId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        ReviewResponse::getSalonId,
                        review -> {
                            AddressRegionUtils.RegionParts parts = AddressRegionUtils.parse(review.getSalonAddress());
                            return new SalonFilterOption(
                                    review.getSalonId(),
                                    review.getSalonName(),
                                    parts.city(),
                                    parts.district(),
                                    parts.neighborhood()
                            );
                        },
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(SalonFilterOption::getCity, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SalonFilterOption::getDistrict, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SalonFilterOption::getNeighborhood, String.CASE_INSENSITIVE_ORDER)
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
                selectedCity,
                selectedDistrict,
                selectedNeighborhood,
                salonId,
                designerId
        )
                : Collections.emptyList();

        Page<ReviewResponse> reviewPage = PageUtils.sliceOneBased(allReviews, page, REVIEWS_PER_PAGE);
        int totalReviews = allReviews.size();
        int totalPages = reviewPage.getTotalPages();
        int currentPage = PageUtils.currentPage(reviewPage);
        List<ReviewResponse> pagedReviews = reviewPage.getContent();

        model.addAttribute("reviews", pagedReviews);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageNumbers", PageUtils.pageNumbers(reviewPage));
        model.addAttribute("currentSort", sortBy);
        model.addAttribute("selectedCity", selectedCity);
        model.addAttribute("selectedDistrict", selectedDistrict);
        model.addAttribute("selectedNeighborhood", selectedNeighborhood);
        model.addAttribute("selectedSalonId", salonId);
        model.addAttribute("selectedDesignerId", designerId);
        model.addAttribute("cityOptions", AddressRegionUtils.cityOptions(addresses));
        model.addAttribute("districtOptions", AddressRegionUtils.districtOptions(addresses, selectedCity));
        model.addAttribute("neighborhoodOptions", AddressRegionUtils.neighborhoodOptions(addresses, selectedCity, selectedDistrict));
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
        if (!Objects.equals(reservation.getMemberId(), userDetails.getMember().getMemberId())) {
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

        return userDetails.getMember().getRole() == MemberRole.ADMIN
                || Objects.equals(review.getMemberId(), userDetails.getMember().getMemberId());
    }

    private String getLoginMemberId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getMember().getMemberId();
    }

    private boolean isReviewableReservation(ReservationResponse reservation) {
        return reservation.getStatus() == ReservationStatus.COMPLETED;
    }

    private List<ReviewResponse> filterReviews(List<ReviewResponse> reviews,
                                               String city,
                                               String district,
                                               String neighborhood,
                                               Integer salonId,
                                               Integer designerId) {
        return reviews.stream()
                .filter(review -> AddressRegionUtils.matches(review.getSalonAddress(), city, district, neighborhood))
                .filter(review -> salonId == null || Objects.equals(review.getSalonId(), salonId))
                .filter(review -> designerId == null || Objects.equals(review.getDesignerId(), designerId))
                .toList();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class SalonFilterOption {
        private final Integer salonId;
        private final String salonName;
        private final String city;
        private final String district;
        private final String neighborhood;

        public SalonFilterOption(Integer salonId, String salonName, String city, String district, String neighborhood) {
            this.salonId = salonId;
            this.salonName = normalizeOptionText(salonName);
            this.city = city == null ? "" : city;
            this.district = district == null ? "" : district;
            this.neighborhood = neighborhood == null ? "" : neighborhood;
        }

        public Integer getSalonId() {
            return salonId;
        }

        public String getSalonName() {
            return salonName;
        }

        public String getCity() {
            return city;
        }

        public String getDistrict() {
            return district;
        }

        public String getNeighborhood() {
            return neighborhood;
        }
    }

    public static final class DesignerFilterOption {
        private final Integer designerId;
        private final String designerName;
        private final Integer salonId;

        public DesignerFilterOption(Integer designerId, String designerName, Integer salonId) {
            this.designerId = designerId;
            this.designerName = normalizeOptionText(designerName);
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

    private static String normalizeOptionText(String value) {
        return value == null ? "" : value;
    }
}
