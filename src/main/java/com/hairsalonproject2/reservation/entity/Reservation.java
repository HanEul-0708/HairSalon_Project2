package com.hairsalonproject2.reservation.entity;

import com.hairsalonproject2.common.constant.PaymentMethod;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reservation")
public class Reservation extends BaseTimeEntity {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 @Column(name = "reservation_id")
 private Integer reservationId;

 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "member_id", nullable = false)
 private Member member;

 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "designer_id", nullable = false)
 private Designer designer;

 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "service_id", nullable = false)
 private SalonService salonService;

 @Column(name = "reservation_date", nullable = false)
 private LocalDate reservationDate;

 @Column(name = "reservation_time", nullable = false)
 private LocalTime reservationTime;

 @Enumerated(EnumType.STRING)
 @Column(name = "status")
 private ReservationStatus status;

 @Column(name = "total_price", nullable = false)
 private Integer totalPrice;

 @Enumerated(EnumType.STRING)
 @JdbcTypeCode(SqlTypes.VARCHAR)
 @Column(name = "payment_method", nullable = false, length = 20)
 private PaymentMethod paymentMethod;

 @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
 private List<ReservationSlot> reservationSlots = new ArrayList<>();

 @OneToOne(mappedBy = "reservation")
 private Review review;

 @Builder
 public Reservation(Member member, Designer designer, SalonService salonService, LocalDate reservationDate, LocalTime reservationTime, ReservationStatus status, Integer totalPrice, PaymentMethod paymentMethod) {
  this.member = member;
  this.designer = designer;
  this.salonService = salonService;
  this.reservationDate = reservationDate;
  this.reservationTime = reservationTime;
  this.status = status;
  this.totalPrice = totalPrice;
  this.paymentMethod = paymentMethod;
 }

 public void changeStatus(ReservationStatus status) {
  this.status = status;
 }

 public void updateReservation(Designer designer, SalonService salonService, LocalDate reservationDate, LocalTime reservationTime, Integer totalPrice, PaymentMethod paymentMethod) {
  this.designer = designer;
  this.salonService = salonService;
  this.reservationDate = reservationDate;
  this.reservationTime = reservationTime;
  this.totalPrice = totalPrice;
  this.paymentMethod = paymentMethod;
 }

 public void addReservationSlot(ReservationSlot slot) {
  this.reservationSlots.add(slot);
  slot.changeReservation(this);
 }
}
