package com.hairsalonproject2.reservation.dto;

import com.hairsalonproject2.common.constant.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * ReservationResponse
 *
 * 예약 조회 응답 DTO
 *
 * 서버가 예약 정보를 클라이언트에게
 * 응답으로 보내줄 때 사용하는 객체
 *
 * 예시 응답 JSON
 *
 * {
 *   "reservationId": 1,
 *   "memberId": "user1",
 *   "designerId": 2,
 *   "salonServiceId": 3,
 *   "reservationDate": "2026-04-01",
 *   "reservationTime": "14:00",
 *   "status": "RESERVED",
 *   "totalPrice": 30000
 * }
 */
@Getter // getter 자동 생성
@Builder // builder 패턴 생성
public class ReservationResponse {

    /**
     * 예약 번호 (PK)
     */
    private Integer reservationId;

    /**
     * 회원 ID
     */
    private String memberId;

    /**
     * 디자이너 ID
     */
    private Integer designerId;

    /**
     * 시술 ID
     */
    private Integer salonServiceId;

    /**
     * 예약 날짜
     */
    private LocalDate reservationDate;

    /**
     * 예약 시간
     */
    private LocalTime reservationTime;

    /**
     * 예약 상태
     * RESERVED / COMPLETED / CANCELLED
     */
    private ReservationStatus status;

    /**
     * 결제 금액
     */
    private Integer totalPrice;

    /**
     * 예약 생성 시간
     */
    private LocalDateTime createdAt;

    /**
     * 예약 수정 시간
     */
    private LocalDateTime updatedAt;
}