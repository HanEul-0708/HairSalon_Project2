package com.hairsalonproject2.admin.controller;

import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminReservationReviewController {

 private final ReservationService reservationService;
 private final ReviewService reviewService;

 @GetMapping("/reservations")
 public String reservations(Model model) {
  model.addAttribute("currentMenu", "reservations");
  model.addAttribute("reservations", reservationService.getAllReservations());
  return "admin/reservations";
 }

 @GetMapping("/reviews")
 public String reviews(Model model) {
  model.addAttribute("currentMenu", "reviews");
  model.addAttribute("reviews", reviewService.getAllReviews(null, null, null, "latest"));
  return "admin/reviews";
 }
}
