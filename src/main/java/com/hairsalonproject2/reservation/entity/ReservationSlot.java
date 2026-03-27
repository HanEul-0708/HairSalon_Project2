package com.hairsalonproject2.reservation.entity;

import com.hairsalonproject2.common.entity.BaseCreatedEntity;
import com.hairsalonproject2.designer.entity.Designer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ReservationSlot
 *
 * 예약 시간 점유 관리 테이블 (핵심🔥)
 *
 * 역할
 * - 디자이너 시간 중복 예약 방지
 *
 * 예:
 * 10:00 예약 → 10:00, 10:30 슬롯 생성
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "reservation_slot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_reservation_slot",
                        columnNames = {
                                "designer_id",
                                "reservation_date",
                                "slot_time"
                        }
                )
        }
)
public class ReservationSlot extends BaseCreatedEntity {

    /**
     * 슬롯 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Integer slotId;

    /**
     * 어떤 예약인지
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    /**
     * 디자이너
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designer_id", nullable = false)
    private Designer designer;

    /**
     * 날짜
     */
    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    /**
     * 시간
     */
    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    // ==============================
    // 생성자
    // ==============================

    @Builder
    public ReservationSlot(Reservation reservation,
                           Designer designer,
                           LocalDate reservationDate,
                           LocalTime slotTime) {
        this.reservation = reservation;
        this.designer = designer;
        this.reservationDate = reservationDate;
        this.slotTime = slotTime;
    }

    // ==============================
    // 연관관계 메서드
    // ==============================

    public void changeReservation(Reservation reservation) {
        this.reservation = reservation;
    }
}