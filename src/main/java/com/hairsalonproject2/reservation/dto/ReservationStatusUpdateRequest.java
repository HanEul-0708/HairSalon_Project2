package com.hairsalonproject2.reservation.dto;

import com.hairsalonproject2.common.constant.ReservationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ReservationStatusUpdateRequest
 *
 * 예약 상태 변경 요청 DTO
 *
 * 클라이언트가 예약 상태를 변경할 때
 * 어떤 상태로 바꿀지 담아서 보내는 객체
 *
 * 예시 요청 JSON
 * {
 *   "status": "COMPLETED"
 * }
 */
@Getter
@NoArgsConstructor
public class ReservationStatusUpdateRequest {

    /**
     * 변경할 예약 상태
     *
     * 예:
     * RESERVED
     * COMPLETED
     * CANCELLED
     */
    @NotNull(message = "예약 상태는 필수입니다.")
    private ReservationStatus status;
}