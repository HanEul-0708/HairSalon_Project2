package com.hairsalonproject2.reservation.dto;

import com.hairsalonproject2.common.constant.PaymentMethod;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class ReservationCreateRequest {

 @NotBlank(message = "회원 ID는 필수입니다.")
 private String memberId;

 @NotNull(message = "디자이너 ID는 필수입니다.")
 private Integer designerId;

 @NotNull(message = "시술 ID는 필수입니다.")
 private Integer salonServiceId;

 @NotNull(message = "예약 날짜는 필수입니다.")
 @FutureOrPresent(message = "예약 날짜는 오늘 이후여야 합니다.")
 private LocalDate reservationDate;

 @NotNull(message = "예약 시간은 필수입니다.")
 private LocalTime reservationTime;

 @NotNull(message = "결제 금액은 필수입니다.")
 @Min(value = 0, message = "결제 금액은 0 이상이어야 합니다.")
 private Integer totalPrice;

 @NotNull(message = "결제 수단은 필수입니다.")
 private PaymentMethod paymentMethod;
}
