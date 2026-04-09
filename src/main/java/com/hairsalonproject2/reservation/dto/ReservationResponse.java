package com.hairsalonproject2.reservation.dto;

import com.hairsalonproject2.common.constant.PaymentMethod;
import com.hairsalonproject2.common.constant.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Builder
public class ReservationResponse {

    private Integer reservationId;
    private String memberId;
    private Integer designerId;
    private String designerName;
    private Integer salonServiceId;
    private String serviceName;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private ReservationStatus status;
    private Integer totalPrice;
    private PaymentMethod paymentMethod;
    private String paymentMethodLabel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
