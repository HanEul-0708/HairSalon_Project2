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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                                 @RequestParam(required = false) Integer designerId,
                                 @RequestParam(defaultValue = "latest") String sortBy,
                                 Model model) {
        String loginMemberId = userDetails == null ? null : userDetails.getMember().getMemberId();
        Map<Integer, String> designerOptions = reviewService.getAllReviews(loginMemberId, null, "latest").stream()
                .collect(Collectors.toMap(
                        ReviewResponse::getDesignerId,
                        ReviewResponse::getDesignerName,
                        (left, right) -> left
                ));

        Double selectedDesignerAverage = designerId == null
                ? null
                : reviewService.getAverageRatingByDesigner(designerId);

        List<ReviewResponse> allReviews = searched
                ? reviewService.getAllReviews(loginMemberId, designerId, sortBy)
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
        model.addAttribute("selectedDesignerId", designerId);
        model.addAttribute("designerOptions", designerOptions);
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
            throw new IllegalArgumentException("Only completed or same-day reservations can be reviewed.");
        }

        if (reviewRepository.findByReservation_ReservationId(reservationId).isPresent()) {
            throw new IllegalArgumentException("A review has already been written for this reservation.");
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
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return false;
        }

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            return true;
        }

        return !reservation.getReservationDate().isAfter(LocalDateTime.now().toLocalDate());
    }
}
