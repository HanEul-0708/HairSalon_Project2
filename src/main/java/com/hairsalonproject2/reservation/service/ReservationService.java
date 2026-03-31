package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.reservation.dto.ReservationCreateRequest;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.dto.ReservationStatusUpdateRequest;

import java.util.List;

/**
 * ReservationService
 *
 * 예약 관련 비즈니스 로직 인터페이스
 *
 * Controller는 이 인터페이스를 통해
 * 예약 서비스를 호출하게 된다.
 */
public interface ReservationService {

    /**
     * 예약 생성
     *
     * 기존 방식:
     * Reservation 엔티티를 직접 받았음
     *
     * 수정 방식:
     * ReservationCreateRequest DTO를 받아서
     * Reservation 엔티티를 내부에서 생성
     */
    ReservationResponse createReservation(ReservationCreateRequest request);

    /**
     * 예약 1건 조회
     *
     * reservationId로 예약 조회 후
     * ReservationResponse DTO로 반환
     */
    ReservationResponse getReservation(Integer reservationId);

    /**
     * 전체 예약 목록 조회
     *
     * Reservation 엔티티 리스트가 아니라
     * ReservationResponse DTO 리스트로 반환
     */
    List<ReservationResponse> getAllReservations();

    /**
     * 특정 회원 예약 목록 조회
     */
    List<ReservationResponse> getReservationsByMember(String memberId);

    /**
     * 예약 취소
     */
    void cancelReservation(Integer reservationId);

    /**
     * 예약 상태 변경
     *
     * reservationId로 예약을 찾은 뒤
     * 요청으로 받은 상태값으로 변경한다.
     */
    ReservationResponse updateReservationStatus(Integer reservationId, ReservationStatusUpdateRequest request);
}