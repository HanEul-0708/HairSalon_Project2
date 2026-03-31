package com.hairsalonproject2.reservation.dto;

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
@Getter // getter 자동 생성
@NoArgsConstructor // 기본 생성자 자동 생성
public class ReservationCreateRequest {

    /**
     * 예약한 회원 ID
     */
    private String memberId;

    /**
     * 예약할 디자이너 ID
     */
    private Integer designerId;

    /**
     * 예약할 시술 ID
     */
    private Integer salonServiceId;

    /**
     * 예약 날짜
     * 예: 2026-04-01
     */
    private LocalDate reservationDate;

    /**
     * 예약 시간
     * 예: 14:00
     */
    private LocalTime reservationTime;

    /**
     * 결제 금액
     */
    private Integer totalPrice;
}