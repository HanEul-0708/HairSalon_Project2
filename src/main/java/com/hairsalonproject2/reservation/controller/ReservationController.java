package com.hairsalonproject2.reservation.controller;

import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    // 예약 생성
    @PostMapping
    public Reservation createReservation(@RequestBody Reservation reservation) {
        return reservationService.createReservation(reservation);
    }

    // 예약 조회
    @GetMapping("/{id}")
    public Reservation getReservation(@PathVariable Integer id) {
        return reservationService.getReservation(id);
    }

    // 전체 예약 목록 조회
    @GetMapping
    public List<Reservation> getAllReservations() {
        return reservationService.getAllReservations();
    }

    // 회원 예약 목록
    @GetMapping("/member/{memberId}")
    public List<Reservation> getReservationsByMember(@PathVariable String memberId) {
        return reservationService.getReservationsByMember(memberId);
    }

    // 예약 취소
    @DeleteMapping("/{id}")
    public String cancelReservation(@PathVariable Integer id) {
        reservationService.cancelReservation(id);
        return "예약이 취소되었습니다.";
    }
}