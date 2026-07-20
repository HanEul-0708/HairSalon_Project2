package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.member.service.CustomUserDetails;
import com.hairsalonproject2.reservation.dto.ReservationCreateRequest;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.dto.ReservationStatusUpdateRequest;
import com.hairsalonproject2.reservation.dto.ReservationUpdateRequest;
import com.hairsalonproject2.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

 private final ReservationService reservationService;

 @PostMapping
 public ReservationResponse createReservation(@AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody ReservationCreateRequest request) {
  return reservationService.createReservation(userDetails.getMember().getMemberId(), request);
 }

 @PutMapping("/{id}")
 public ReservationResponse updateReservation(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Integer id, @Valid @RequestBody ReservationUpdateRequest request) {
  validateReservationAccess(userDetails, id);
  return reservationService.updateReservation(id, request);
 }

 @GetMapping("/{id}")
 public ReservationResponse getReservation(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Integer id) {
  validateReservationAccess(userDetails, id);
  return reservationService.getReservation(id);
 }

 @GetMapping
 @PreAuthorize("hasRole('ADMIN')")
 public List<ReservationResponse> getAllReservations() {
  return reservationService.getAllReservations();
 }

 @GetMapping("/member/{memberId}")
 public List<ReservationResponse> getReservationsByMember(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable String memberId) {
  validateMemberAccess(userDetails, memberId);
  return reservationService.getReservationsByMember(memberId);
 }

 @GetMapping("/my")
 public List<ReservationResponse> getMyReservations(@AuthenticationPrincipal CustomUserDetails userDetails) {
  return reservationService.getMyReservations(userDetails.getMember().getMemberId());
 }

 @GetMapping("/availability")
 public Map<String, Object> checkAvailability(@RequestParam Integer designerId, @RequestParam LocalDate reservationDate, @RequestParam LocalTime reservationTime, @RequestParam(required = false) Integer reservationId) {
  boolean available = reservationService.isReservationAvailable(designerId, reservationDate, reservationTime, reservationId);
  return Map.of("available", available, "message", available ? "예약 가능한 시간입니다." : "선택하신 날짜와 시간에는 이미 해당 디자이너 예약이 있습니다. 다른 시간을 선택해 주세요.");
 }

 @DeleteMapping("/{id}")
 public String cancelReservation(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Integer id) {
  validateReservationAccess(userDetails, id);
  reservationService.cancelReservation(id);
  return "예약이 취소되었습니다.";
 }

 @PatchMapping("/{id}/status")
 @PreAuthorize("hasRole('ADMIN')")
 public ReservationResponse updateReservationStatus(@AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Integer id, @Valid @RequestBody ReservationStatusUpdateRequest request) {
  return reservationService.updateReservationStatus(id, request);
 }

 private void validateReservationAccess(CustomUserDetails userDetails, Integer reservationId) {
  ReservationResponse reservation = reservationService.getReservation(reservationId);
  if (!isAdmin(userDetails) && !reservation.getMemberId().equals(userDetails.getMember().getMemberId())) {
   throw new org.springframework.security.access.AccessDeniedException("You can only access your own reservation.");
  }
 }

 private boolean isAdmin(CustomUserDetails userDetails) {
  return userDetails.getMember().getRole().name().equals("ADMIN");
 }

 private void validateMemberAccess(CustomUserDetails userDetails, String memberId) {
  if (!isAdmin(userDetails) && !memberId.equals(userDetails.getMember().getMemberId())) {
   throw new org.springframework.security.access.AccessDeniedException("You can only access your own reservations.");
  }
 }
}
