package com.hairsalonproject2.reservation.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ReservationCreateRequest
 *
 * 예약 생성 요청 DTO
 *
 * 클라이언트(프론트 / 테스트 API)에서
 * 예약을 만들 때 보내는 데이터를 담는 객체
 *
 * 예시 요청 JSON
 *
 * {
 *   "memberId": "user1",
 *   "designerId": 2,
 *   "salonServiceId": 3,
 *   "reservationDate": "2026-04-01",
 *   "reservationTime": "14:00",
 *   "totalPrice": 30000
 * }
 */
@Getter
@NoArgsConstructor
public class ReservationCreateRequest {

    /**
     * 예약한 회원 ID
     */
    @NotBlank(message = "회원 ID는 필수입니다.")
    private String memberId;

    /**
     * 예약할 디자이너 ID
     */
    @NotNull(message = "디자이너 ID는 필수입니다.")
    private Integer designerId;

    /**
     * 예약할 시술 ID
     */
    @NotNull(message = "시술 ID는 필수입니다.")
    private Integer salonServiceId;

    /**
     * 예약 날짜
     * 예: 2026-04-01
     */
    @NotNull(message = "예약 날짜는 필수입니다.")
    @FutureOrPresent(message = "예약 날짜는 오늘 이후여야 합니다.")
    private LocalDate reservationDate;

    /**
     * 예약 시간
     * 예: 14:00
     */
    @NotNull(message = "예약 시간은 필수입니다.")
    private LocalTime reservationTime;

    /**
     * 결제 금액
     */
    @NotNull(message = "결제 금액은 필수입니다.")
    @Min(value = 0, message = "결제 금액은 0 이상이어야 합니다.")
    private Integer totalPrice;
}