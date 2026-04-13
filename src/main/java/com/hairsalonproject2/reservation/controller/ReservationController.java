package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.common.constant.MemberRole;
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

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ReservationResponse createReservation(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @Valid @RequestBody ReservationCreateRequest request) {
        return reservationService.createReservation(userDetails.getMember().getMemberId(), request);
    }

    @PutMapping("/{id}")
    public ReservationResponse updateReservation(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                 @PathVariable Integer id,
                                                 @Valid @RequestBody ReservationUpdateRequest request) {
        validateReservationAccess(userDetails, id);
        return reservationService.updateReservation(id, request);
    }

    @GetMapping("/{id}")
    public ReservationResponse getReservation(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              @PathVariable Integer id) {
        validateReservationAccess(userDetails, id);
        return reservationService.getReservation(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReservationResponse> getAllReservations() {
        return reservationService.getAllReservations();
    }

    @GetMapping("/member/{memberId}")
    public List<ReservationResponse> getReservationsByMember(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                             @PathVariable String memberId) {
        validateMemberAccess(userDetails, memberId);
        return reservationService.getReservationsByMember(memberId);
    }

    @GetMapping("/my")
    public List<ReservationResponse> getMyReservations(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return reservationService.getMyReservations(userDetails.getMember().getMemberId());
    }

    @DeleteMapping("/{id}")
    public String cancelReservation(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @PathVariable Integer id) {
        validateReservationAccess(userDetails, id);
        reservationService.cancelReservation(id);
        return "예약이 취소되었습니다.";
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ReservationResponse updateReservationStatus(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @PathVariable Integer id,
                                                       @Valid @RequestBody ReservationStatusUpdateRequest request) {
        return reservationService.updateReservationStatus(id, request);
    }

    private void validateReservationAccess(CustomUserDetails userDetails, Integer reservationId) {
        ReservationResponse reservation = reservationService.getReservation(reservationId);
        if (!isAdmin(userDetails) && !Objects.equals(reservation.getMemberId(), memberId(userDetails))) {
            throw new org.springframework.security.access.AccessDeniedException("You can only access your own reservation.");
        }
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails.getMember().getRole() == MemberRole.ADMIN;
    }

    private void validateMemberAccess(CustomUserDetails userDetails, String memberId) {
        if (!isAdmin(userDetails) && !Objects.equals(memberId, memberId(userDetails))) {
            throw new org.springframework.security.access.AccessDeniedException("You can only access your own reservations.");
        }
    }

    private String memberId(CustomUserDetails userDetails) {
        return userDetails.getMember().getMemberId();
    }
}
