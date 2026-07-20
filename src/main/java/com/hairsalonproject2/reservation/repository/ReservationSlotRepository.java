package com.hairsalonproject2.reservation.repository;

import com.hairsalonproject2.reservation.entity.ReservationSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, Integer> {

 boolean existsByDesigner_DesignerIdAndReservationDateAndSlotTime(Integer designerId, LocalDate reservationDate, LocalTime slotTime);

 boolean existsByDesigner_DesignerIdAndReservationDateAndSlotTimeAndReservation_ReservationIdNot(Integer designerId, LocalDate reservationDate, LocalTime slotTime, Integer reservationId);

 // 디자이너 + 날짜 기준 예약 슬롯 조회
 List<ReservationSlot> findByDesigner_DesignerIdAndReservationDate(Integer designerId, LocalDate reservationDate);

 // 특정 예약에 연결된 슬롯 삭제
 void deleteByReservation_ReservationId(Integer reservationId);
}
