package com.hairsalonproject2.review.controller;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.dto.ReviewDetailResponse;
import com.hairsalonproject2.review.dto.ReviewResponse;
import com.hairsalonproject2.review.repository.ReviewRepository;
import com.hairsalonproject2.review.service.ReviewService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ReviewPageController {

 private static final int REVIEWS_PER_PAGE = 6;
 private static final String REVIEW_VISITOR_COOKIE = "review_visitor_token";

 private final ReviewService reviewService;
 private final ReservationService reservationService;
 private final ReviewRepository reviewRepository;

 @GetMapping("/reviews")
 public String reviewListPage(@AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest request, @RequestParam(defaultValue = "1") int page, @RequestParam(required = false) Integer designerId, @RequestParam(defaultValue = "latest") String sortBy, Model model) {
  String loginMemberId = userDetails == null ? null : userDetails.getMember().getMemberId();
  String visitorToken = getVisitorToken(request);
  List<ReviewResponse> allReviews = reviewService.getAllReviews(loginMemberId, visitorToken, designerId, sortBy);
  int totalReviews = allReviews.size();
  int totalPages = Math.max(1, (int) Math.ceil((double) totalReviews / REVIEWS_PER_PAGE));
  int currentPage = Math.min(Math.max(page, 1), totalPages);
  int fromIndex = (currentPage - 1) * REVIEWS_PER_PAGE;
  int toIndex = Math.min(fromIndex + REVIEWS_PER_PAGE, totalReviews);
  List<ReviewResponse> pagedReviews = totalReviews == 0 ? Collections.emptyList() : allReviews.subList(fromIndex, toIndex);
  Map<Integer, String> designerOptions = reviewService.getAllReviews(loginMemberId, visitorToken, null, "latest").stream().collect(Collectors.toMap(ReviewResponse::getDesignerId, ReviewResponse::getDesignerName, (left, right) -> left));
  Double selectedDesignerAverage = designerId == null ? null : reviewService.getAverageRatingByDesigner(designerId);
  model.addAttribute("reviews", pagedReviews);
  model.addAttribute("currentPage", currentPage);
  model.addAttribute("totalPages", totalPages);
  model.addAttribute("pageNumbers", java.util.stream.IntStream.rangeClosed(1, totalPages).boxed().toList());
  model.addAttribute("currentSort", sortBy);
  model.addAttribute("selectedDesignerId", designerId);
  model.addAttribute("designerOptions", designerOptions);
  model.addAttribute("totalReviewCount", totalReviews);
  model.addAttribute("selectedDesignerAverage", selectedDesignerAverage);
  model.addAttribute("isLoggedIn", userDetails != null);
  return "review/list";
 }

 @GetMapping("/reviews/{reviewId}")
 public String reviewDetailPage(@PathVariable Integer reviewId, @AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest request, Model model) {
  ReviewDetailResponse review = reviewService.getReviewDetail(reviewId, getLoginMemberId(userDetails), getVisitorToken(request));
  model.addAttribute("review", review);
  model.addAttribute("canManageReview", canManageReview(userDetails, review));
  model.addAttribute("isLoggedIn", userDetails != null);
  return "review/detail";
 }

 @GetMapping("/reviews/{reviewId}/edit")
 public String reviewEditPage(@PathVariable Integer reviewId, @AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest request, Model model) {
  ReviewDetailResponse review = reviewService.getReviewDetail(reviewId, getLoginMemberId(userDetails), getVisitorToken(request));
  if (!canManageReview(userDetails, review)) {
   throw new AccessDeniedException("You can only edit your own review.");
  }
  model.addAttribute("review", review);
  return "review/edit";
 }

 @GetMapping("/reviews/write")
 public String reviewWritePage(@RequestParam Integer reservationId, @AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
  if (userDetails == null) {
   return "redirect:/members/login";
  }
  if (userDetails.getMember().getRole().name().equals("ADMIN")) {
   throw new AccessDeniedException("Admins cannot write reviews for member reservations.");
  }
  reservationService.validateReservationAccess(reservationId, userDetails.getMember().getMemberId(), false);
  ReservationResponse reservation = reservationService.getReservation(reservationId);
  if (!isReviewableReservation(reservation)) {
   throw new IllegalArgumentException("예약 시간 이후에만 리뷰를 작성할 수 있습니다.");
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
  return userDetails.getMember().getRole().name().equals("ADMIN") || review.getMemberId().equals(userDetails.getMember().getMemberId());
 }

 private String getLoginMemberId(CustomUserDetails userDetails) {
  return userDetails == null ? null : userDetails.getMember().getMemberId();
 }

 private boolean isReviewableReservation(ReservationResponse reservation) {
  if (reservation.getStatus() == ReservationStatus.CANCELLED) {
   return false;
  }
  return !LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime()).isAfter(LocalDateTime.now());
 }

 private String getVisitorToken(HttpServletRequest request) {
  if (request.getCookies() == null) {
   return null;
  }
  for (Cookie cookie : request.getCookies()) {
   if (REVIEW_VISITOR_COOKIE.equals(cookie.getName())) {
	return cookie.getValue();
   }
  }
  return null;
 }
}
