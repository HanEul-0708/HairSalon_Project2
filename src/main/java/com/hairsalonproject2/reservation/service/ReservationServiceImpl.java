package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.entity.ReservationSlot;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.reservation.repository.ReservationSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository reservationSlotRepository;

    @Override
    public Reservation createReservation(Reservation reservation) {
        Integer designerId = reservation.getDesigner().getDesignerId();

        boolean exists = reservationSlotRepository.existsByDesigner_DesignerIdAndReservationDateAndSlotTime(
                designerId,
                reservation.getReservationDate(),
                reservation.getReservationTime()
        );

        if (exists) {
            throw new IllegalArgumentException("이미 예약된 시간입니다.");
        }

        if (reservation.getStatus() == null) {
            reservation.changeStatus(ReservationStatus.RESERVED);
        }

        Reservation savedReservation = reservationRepository.save(reservation);

        ReservationSlot slot = ReservationSlot.builder()
                .reservation(savedReservation)
                .designer(savedReservation.getDesigner())
                .reservationDate(savedReservation.getReservationDate())
                .slotTime(savedReservation.getReservationTime())
                .build();

        savedReservation.addReservationSlot(slot);

        reservationSlotRepository.save(slot);

        return savedReservation;
    }

    @Override
    public Reservation getReservation(Integer reservationId) {
        return reservationRepository.findById(reservationId).orElse(null);
    }

    // 전체 예약 목록 조회
    @Override
    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    @Override
    public List<Reservation> getReservationsByMember(String memberId) {
        return reservationRepository.findByMember_MemberId(memberId);
    }

    @Override
    public void cancelReservation(Integer reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);

        if (reservation == null) {
            throw new IllegalArgumentException("해당 예약이 존재하지 않습니다.");
        }

        reservation.changeStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
    }
}