package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.reservation.dto.ReservationCreateRequest;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.service.ReservationService;
import com.hairsalonproject2.reservation.dto.ReservationStatusUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ReservationController
 *
 * 예약 관련 API 처리 컨트롤러
 *
 * 클라이언트 요청 → Service 호출 → 결과 반환
 */
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    /**
     * ReservationService 주입
     */
    private final ReservationService reservationService;

    /**
     * 예약 생성 API
     *
     * 기존 방식:
     * Reservation 엔티티 직접 받음
     *
     * 수정 방식:
     * ReservationCreateRequest DTO로 받음
     */
    @PostMapping
    public ReservationResponse createReservation(
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        return reservationService.createReservation(request);
    }

    /**
     * 예약 1건 조회
     */
    @GetMapping("/{id}")
    public ReservationResponse getReservation(@PathVariable Integer id) {
        return reservationService.getReservation(id);
    }

    /**
     * 전체 예약 목록 조회
     */
    @GetMapping
    public List<ReservationResponse> getAllReservations() {
        return reservationService.getAllReservations();
    }

    /**
     * 특정 회원 예약 목록 조회
     */
    @GetMapping("/member/{memberId}")
    public List<ReservationResponse> getReservationsByMember(@PathVariable String memberId) {
        return reservationService.getReservationsByMember(memberId);
    }

    /**
     * 예약 취소
     */
    @DeleteMapping("/{id}")
    public String cancelReservation(@PathVariable Integer id) {

        reservationService.cancelReservation(id);

        return "예약이 취소되었습니다.";
    }

    /**
     * 예약 상태 변경 API
     *
     * 예시:
     * PATCH /reservations/1/status
     *
     * 요청 body:
     * {
     *   "status": "COMPLETED"
     * }
     */
    @PatchMapping("/{id}/status")
    public ReservationResponse updateReservationStatus(
            @PathVariable Integer id,
            @Valid @RequestBody ReservationStatusUpdateRequest request
    ) {
        return reservationService.updateReservationStatus(id, request);
    }
}