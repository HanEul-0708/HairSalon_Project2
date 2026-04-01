package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.reservation.entity.Reservation;
import java.util.List;

public interface ReservationService {

    // 예약 생성
    Reservation createReservation(Reservation reservation);

    // 예약 1건 조회
    Reservation getReservation(Integer reservationId);

    // 전체 예약 목록 조회
    List<Reservation> getAllReservations();

    // 회원 예약 목록 조회
    List<Reservation> getReservationsByMember(String memberId);

    // 예약 취소
    void cancelReservation(Integer reservationId);
}