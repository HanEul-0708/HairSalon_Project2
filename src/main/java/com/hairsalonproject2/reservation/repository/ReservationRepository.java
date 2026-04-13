package com.hairsalonproject2.reservation.repository;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
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

    @Query("""
            select (count(r) > 0)
            from Reservation r
            where r.designer.designerId = :designerId
              and r.reservationDate = :reservationDate
              and r.reservationTime = :reservationTime
              and r.status <> :cancelledStatus
            """)
    boolean existsActiveConflict(@Param("designerId") Integer designerId,
                                 @Param("reservationDate") LocalDate reservationDate,
                                 @Param("reservationTime") LocalTime reservationTime,
                                 @Param("cancelledStatus") ReservationStatus cancelledStatus);

    @Query("""
            select (count(r) > 0)
            from Reservation r
            where r.designer.designerId = :designerId
              and r.reservationDate = :reservationDate
              and r.reservationTime = :reservationTime
              and r.status <> :cancelledStatus
              and r.reservationId <> :reservationId
            """)
    boolean existsActiveConflictExcludingReservation(@Param("designerId") Integer designerId,
                                                     @Param("reservationDate") LocalDate reservationDate,
                                                     @Param("reservationTime") LocalTime reservationTime,
                                                     @Param("cancelledStatus") ReservationStatus cancelledStatus,
                                                     @Param("reservationId") Integer reservationId);
}
