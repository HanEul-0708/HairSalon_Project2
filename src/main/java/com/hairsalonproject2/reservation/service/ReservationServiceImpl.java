package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.common.constant.PaymentMethod;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.reservation.dto.ReservationCreateRequest;
import com.hairsalonproject2.reservation.dto.ReservationResponse;
import com.hairsalonproject2.reservation.dto.ReservationStatusUpdateRequest;
import com.hairsalonproject2.reservation.dto.ReservationUpdateRequest;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.entity.ReservationSlot;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.reservation.repository.ReservationSlotRepository;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {

 private static final String MEMBER_NOT_FOUND = "회원이 존재하지 않습니다.";
 private static final String DESIGNER_NOT_FOUND = "디자이너가 존재하지 않습니다.";
 private static final String SERVICE_NOT_FOUND = "시술이 존재하지 않습니다.";
 private static final String RESERVATION_NOT_FOUND = "해당 예약이 존재하지 않습니다.";
 private static final String RESERVATION_CONFLICT = "이미 예약된 시간입니다.";
 private static final String ONLY_RESERVED_CAN_UPDATE = "예약 완료 상태에서만 예약 변경이 가능합니다.";
 private static final String ALREADY_CANCELLED = "이미 취소된 예약입니다.";
 private static final String COMPLETED_CANNOT_CANCEL = "방문 완료된 예약은 취소할 수 없습니다.";
 private static final String RESERVATION_ACCESS_DENIED = "회원 본인 예약만 조회하거나 변경할 수 있습니다.";
 private static final String MEMBER_ACCESS_DENIED = "회원 본인 예약만 조회할 수 있습니다.";

 private static final String DESIGNER_SERVICE_SALON_MISMATCH = "Designer and service must belong to the same salon.";
 private static final String ONLY_RESERVED_CAN_CHANGE_STATUS = "Only reserved reservations can be changed to cancelled or completed.";

 private final ReservationRepository reservationRepository;
 private final ReservationSlotRepository reservationSlotRepository;
 private final MemberRepository memberRepository;
 private final DesignerRepository designerRepository;
 private final SalonServiceRepository salonServiceRepository;

 @Override
 @Transactional
 public ReservationResponse createReservation(String loginMemberId, ReservationCreateRequest request) {
  Member member = memberRepository.findById(loginMemberId).orElseThrow(() -> new IllegalArgumentException(MEMBER_NOT_FOUND));
  Designer designer = designerRepository.findById(request.getDesignerId()).orElseThrow(() -> new IllegalArgumentException(DESIGNER_NOT_FOUND));
  SalonService salonService = salonServiceRepository.findById(request.getSalonServiceId()).orElseThrow(() -> new IllegalArgumentException(SERVICE_NOT_FOUND));
  validateDesignerAndServiceSalon(designer, salonService);
  validateReservationSlot(designer.getDesignerId(), request.getReservationDate(), request.getReservationTime(), null);
  Reservation reservation = Reservation.builder().member(member).designer(designer).salonService(salonService).reservationDate(request.getReservationDate()).reservationTime(request.getReservationTime()).status(ReservationStatus.RESERVED).totalPrice(request.getTotalPrice()).paymentMethod(request.getPaymentMethod()).build();
  Reservation savedReservation = reservationRepository.save(reservation);
  saveReservationSlot(savedReservation);
  return toResponse(savedReservation);
 }

 @Override
 public ReservationResponse getReservation(Integer reservationId) {
  Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(() -> new IllegalArgumentException(RESERVATION_NOT_FOUND));
  return toResponse(reservation);
 }

 @Override
 public List<ReservationResponse> getAllReservations() {
  return reservationRepository.findAll().stream().map(this::toResponse).toList();
 }

 @Override
 public List<ReservationResponse> getReservationsByMember(String memberId) {
  return reservationRepository.findByMember_MemberId(memberId).stream().map(this::toResponse).toList();
 }

 @Override
 public List<ReservationResponse> getMyReservations(String memberId) {
  return reservationRepository.findByMember_MemberId(memberId).stream().map(this::toResponse).toList();
 }

 @Override
 @Transactional
 public ReservationResponse updateReservation(Integer reservationId, ReservationUpdateRequest request) {
  Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(() -> new IllegalArgumentException(RESERVATION_NOT_FOUND));
  if (reservation.getStatus() != ReservationStatus.RESERVED) {
   throw new IllegalArgumentException(ONLY_RESERVED_CAN_UPDATE);
  }
  Designer designer = designerRepository.findById(request.getDesignerId()).orElseThrow(() -> new IllegalArgumentException(DESIGNER_NOT_FOUND));
  SalonService salonService = salonServiceRepository.findById(request.getSalonServiceId()).orElseThrow(() -> new IllegalArgumentException(SERVICE_NOT_FOUND));
  validateDesignerAndServiceSalon(designer, salonService);
  validateReservationSlot(designer.getDesignerId(), request.getReservationDate(), request.getReservationTime(), reservationId);
  reservation.updateReservation(designer, salonService, request.getReservationDate(), request.getReservationTime(), request.getTotalPrice(), request.getPaymentMethod());
  ensureReservationSlot(reservation);
  return toResponse(reservationRepository.save(reservation));
 }

 @Override
 @Transactional
 public void cancelReservation(Integer reservationId) {
  Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(() -> new IllegalArgumentException(RESERVATION_NOT_FOUND));
  if (reservation.getStatus() == ReservationStatus.CANCELLED) {
   throw new IllegalArgumentException(ALREADY_CANCELLED);
  }
  if (reservation.getStatus() == ReservationStatus.COMPLETED) {
   throw new IllegalArgumentException(COMPLETED_CANNOT_CANCEL);
  }
  reservation.changeStatus(ReservationStatus.CANCELLED);
  reservationRepository.save(reservation);
  reservationSlotRepository.deleteByReservation_ReservationId(reservationId);
 }

 @Override
 @Transactional
 public ReservationResponse updateReservationStatus(Integer reservationId, ReservationStatusUpdateRequest request) {
  Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(() -> new IllegalArgumentException(RESERVATION_NOT_FOUND));
  ReservationStatus targetStatus = request.getStatus();
  if (reservation.getStatus() != ReservationStatus.RESERVED && (targetStatus == ReservationStatus.CANCELLED || targetStatus == ReservationStatus.COMPLETED)) {
   throw new IllegalArgumentException(ONLY_RESERVED_CAN_CHANGE_STATUS);
  }
  if (targetStatus == ReservationStatus.CANCELLED) {
   reservationSlotRepository.deleteByReservation_ReservationId(reservationId);
  } else {
   validateReservationSlot(reservation.getDesigner().getDesignerId(), reservation.getReservationDate(), reservation.getReservationTime(), reservationId);
   ensureReservationSlot(reservation);
  }
  reservation.changeStatus(targetStatus);
  return toResponse(reservationRepository.save(reservation));
 }

 @Override
 public boolean isReservationAvailable(Integer designerId, LocalDate reservationDate, LocalTime reservationTime, Integer reservationId) {
  return !hasReservationConflict(designerId, reservationDate, reservationTime, reservationId);
 }

 private void validateReservationSlot(Integer designerId, LocalDate reservationDate, LocalTime reservationTime, Integer reservationId) {
  if (hasReservationConflict(designerId, reservationDate, reservationTime, reservationId)) {
   throw new IllegalArgumentException(RESERVATION_CONFLICT);
  }
 }

 private boolean hasReservationConflict(Integer designerId, LocalDate reservationDate, LocalTime reservationTime, Integer reservationId) {
  boolean existsInReservation = reservationId == null ? reservationRepository.existsActiveConflict(designerId, reservationDate, reservationTime, ReservationStatus.CANCELLED) : reservationRepository.existsActiveConflictExcludingReservation(designerId, reservationDate, reservationTime, ReservationStatus.CANCELLED, reservationId);
  boolean existsInSlot = reservationId == null ? reservationSlotRepository.existsByDesigner_DesignerIdAndReservationDateAndSlotTime(designerId, reservationDate, reservationTime) : reservationSlotRepository.existsByDesigner_DesignerIdAndReservationDateAndSlotTimeAndReservation_ReservationIdNot(designerId, reservationDate, reservationTime, reservationId);
  return existsInReservation || existsInSlot;
 }

 private void validateDesignerAndServiceSalon(Designer designer, SalonService salonService) {
  Integer designerSalonId = designer.getSalon() == null ? null : designer.getSalon().getSalonId();
  Integer serviceSalonId = salonService.getSalon() == null ? null : salonService.getSalon().getSalonId();
  if (designerSalonId == null || !designerSalonId.equals(serviceSalonId)) {
   throw new IllegalArgumentException(DESIGNER_SERVICE_SALON_MISMATCH);
  }
 }

 private void saveReservationSlot(Reservation reservation) {
  try {
   ReservationSlot slot = ReservationSlot.builder().reservation(reservation).designer(reservation.getDesigner()).reservationDate(reservation.getReservationDate()).slotTime(reservation.getReservationTime()).build();
   reservation.addReservationSlot(slot);
   reservationSlotRepository.save(slot);
  } catch (DataIntegrityViolationException e) {
   throw new IllegalArgumentException(RESERVATION_CONFLICT);
  }
 }

 private void ensureReservationSlot(Reservation reservation) {
  reservationSlotRepository.deleteByReservation_ReservationId(reservation.getReservationId());
  saveReservationSlot(reservation);
 }

 private ReservationResponse toResponse(Reservation reservation) {
  return ReservationResponse.builder().reservationId(reservation.getReservationId()).memberId(reservation.getMember().getMemberId()).designerId(reservation.getDesigner().getDesignerId()).designerName(reservation.getDesigner().getName()).salonServiceId(reservation.getSalonService().getServiceId()).serviceName(reservation.getSalonService().getName()).reservationDate(reservation.getReservationDate()).reservationTime(reservation.getReservationTime()).status(reservation.getStatus()).totalPrice(reservation.getTotalPrice()).paymentMethod(reservation.getPaymentMethod()).paymentMethodLabel(getPaymentMethodLabel(reservation.getPaymentMethod())).createdAt(reservation.getCreatedAt()).updatedAt(reservation.getUpdatedAt()).build();
 }

 private String getPaymentMethodLabel(PaymentMethod paymentMethod) {
  if (paymentMethod == null) {
   return "-";
  }
  return switch (paymentMethod) {
   case CARD -> "카드";
   case CASH -> "현금";
   case KAKAO_PAY -> "카카오페이";
   case NAVER_PAY -> "네이버페이";
  };
 }

 @Override
 public void validateReservationAccess(Integer reservationId, String loginMemberId, boolean isAdmin) {
  Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(() -> new IllegalArgumentException(RESERVATION_NOT_FOUND));
  if (!isAdmin && !reservation.getMember().getMemberId().equals(loginMemberId)) {
   throw new AccessDeniedException(RESERVATION_ACCESS_DENIED);
  }
 }

 @Override
 public void validateMemberAccess(String targetMemberId, String loginMemberId, boolean isAdmin) {
  if (!isAdmin && !targetMemberId.equals(loginMemberId)) {
   throw new AccessDeniedException(MEMBER_ACCESS_DENIED);
  }
 }
}
