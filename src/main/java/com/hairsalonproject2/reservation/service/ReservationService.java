package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.reservation.dto.ReservationCreateRequest;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.dto.ReservationStatusUpdateRequest;
import com.hairsalonproject2.reservation.dto.ReservationUpdateRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ReservationService {

 ReservationResponse createReservation(String loginMemberId, ReservationCreateRequest request);

 ReservationResponse getReservation(Integer reservationId);

 List<ReservationResponse> getAllReservations();

 List<ReservationResponse> getReservationsByMember(String memberId);

 List<ReservationResponse> getMyReservations(String memberId);

 ReservationResponse updateReservation(Integer reservationId, ReservationUpdateRequest request);

 void cancelReservation(Integer reservationId);

 ReservationResponse updateReservationStatus(Integer reservationId, ReservationStatusUpdateRequest request);

 boolean isReservationAvailable(Integer designerId, LocalDate reservationDate, LocalTime reservationTime, Integer reservationId);

 void validateReservationAccess(Integer reservationId, String loginMemberId, boolean isAdmin);

 void validateMemberAccess(String targetMemberId, String loginMemberId, boolean isAdmin);
}
