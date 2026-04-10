package com.hairsalonproject2.reservation.repository;

import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.common.constant.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * ReservationRepository
 *
 * 예약 관련 Repository
 */
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    /**
     * 회원별 예약 목록 조회
     */
    List<Reservation> findByMember_MemberId(String memberId);

    List<Reservation> findByDesigner_Member_MemberId(String memberId);

    /**
     * 디자이너별 예약 목록 조회
     */
    List<Reservation> findByDesigner_DesignerId(Integer designerId);

    /**
     * 디자이너 + 날짜 기준 예약 조회
     */
    List<Reservation> findByDesigner_DesignerIdAndReservationDate(
            Integer designerId,
            LocalDate reservationDate
    );

    List<Reservation> findByStatus(ReservationStatus status);

    long countByStatus(ReservationStatus status);
}
