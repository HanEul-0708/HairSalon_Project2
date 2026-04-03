package com.hairsalonproject2.reservation.entity;

import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.common.entity.BaseTimeEntity;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.salonservice.entity.SalonService;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reservation
 *
 * 예약 핵심 엔티티 (프로젝트 중심🔥)
 *
 * 구성
 * - 회원
 * - 디자이너
 * - 시술
 * - 날짜
 * - 시간
 * - 상태
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reservation")
public class Reservation extends BaseTimeEntity {

    /**
     * 예약 PK
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Integer reservationId;

    /**
     * 예약한 회원
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 디자이너
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designer_id", nullable = false)
    private Designer designer;

    /**
     * 시술
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private SalonService salonService;

    /**
     * 예약 날짜
     */
    @Column(name = "reservation_date", nullable = false)
    private LocalDate reservationDate;

    /**
     * 예약 시간
     */
    @Column(name = "reservation_time", nullable = false)
    private LocalTime reservationTime;

    /**
     * 예약 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReservationStatus status;

    /**
     * 총 결제 금액
     */
    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    // ==============================
    // 연관관계
    // ==============================

    /**
     * 예약 슬롯 목록 (핵심🔥)
     *
     * cascade:
     * -> 예약 저장 시 슬롯도 같이 저장
     *
     * orphanRemoval:
     * -> 슬롯 제거 시 DB에서도 삭제
     */
    @OneToMany(mappedBy = "reservation",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<ReservationSlot> reservationSlots = new ArrayList<>();

    /**
     * 리뷰 (1:1)
     */
    @OneToOne(mappedBy = "reservation")
    private Review review;

    // ==============================
    // 생성자
    // ==============================

    @Builder
    public Reservation(Member member, Designer designer,
                       SalonService salonService,
                       LocalDate reservationDate,
                       LocalTime reservationTime,
                       ReservationStatus status,
                       Integer totalPrice) {
        this.member = member;
        this.designer = designer;
        this.salonService = salonService;
        this.reservationDate = reservationDate;
        this.reservationTime = reservationTime;
        this.status = status;
        this.totalPrice = totalPrice;
    }

    // ==============================
    // 비즈니스 메서드
    // ==============================

    /**
     * 예약 상태 변경
     */
    public void changeStatus(ReservationStatus status) {
        this.status = status;
    }

    /**
     * 슬롯 추가 (핵심🔥)
     *
     * 양방향 연관관계 유지
     */
    public void addReservationSlot(ReservationSlot slot) {
        this.reservationSlots.add(slot);
        slot.changeReservation(this);
    }
}