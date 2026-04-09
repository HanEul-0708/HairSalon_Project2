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

    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository reservationSlotRepository;
    private final MemberRepository memberRepository;
    private final DesignerRepository designerRepository;
    private final SalonServiceRepository salonServiceRepository;

    @Override
    @Transactional
    public ReservationResponse createReservation(String loginMemberId, ReservationCreateRequest request) {
        Member member = memberRepository.findById(loginMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found."));
        Designer designer = designerRepository.findById(request.getDesignerId())
                .orElseThrow(() -> new IllegalArgumentException("Designer not found."));
        SalonService salonService = salonServiceRepository.findById(request.getSalonServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Salon service not found."));
        validateDesignerAndServiceMatch(designer, salonService);

        validateReservationSlot(
                designer.getDesignerId(),
                request.getReservationDate(),
                request.getReservationTime(),
                null
        );

        Reservation reservation = Reservation.builder()
                .member(member)
                .designer(designer)
                .salonService(salonService)
                .reservationDate(request.getReservationDate())
                .reservationTime(request.getReservationTime())
                .status(ReservationStatus.RESERVED)
                .totalPrice(request.getTotalPrice())
                .paymentMethod(request.getPaymentMethod())
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);
        saveReservationSlot(savedReservation);
        return toResponse(savedReservation);
    }

    @Override
    public ReservationResponse getReservation(Integer reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        return toResponse(reservation);
    }

    @Override
    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ReservationResponse> getReservationsByMember(String memberId) {
        return reservationRepository.findByMember_MemberId(memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ReservationResponse> getMyReservations(String memberId) {
        return reservationRepository.findByMember_MemberId(memberId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReservationResponse updateReservation(Integer reservationId, ReservationUpdateRequest request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new IllegalArgumentException("Only reserved reservations can be updated.");
        }

        Designer designer = designerRepository.findById(request.getDesignerId())
                .orElseThrow(() -> new IllegalArgumentException("Designer not found."));
        SalonService salonService = salonServiceRepository.findById(request.getSalonServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Salon service not found."));
        validateDesignerAndServiceMatch(designer, salonService);

        validateReservationSlot(
                designer.getDesignerId(),
                request.getReservationDate(),
                request.getReservationTime(),
                reservationId
        );

        reservation.updateReservation(
                designer,
                salonService,
                request.getReservationDate(),
                request.getReservationTime(),
                request.getTotalPrice(),
                request.getPaymentMethod()
        );

        reservationSlotRepository.deleteByReservation_ReservationId(reservationId);
        saveReservationSlot(reservation);

        return toResponse(reservationRepository.save(reservation));
    }

    @Override
    @Transactional
    public void cancelReservation(Integer reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        validateStatusTransition(reservation.getStatus(), ReservationStatus.CANCELLED);
        reservation.changeStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        reservationSlotRepository.deleteByReservation_ReservationId(reservationId);
    }

    @Override
    @Transactional
    public ReservationResponse updateReservationStatus(Integer reservationId, ReservationStatusUpdateRequest request) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        validateStatusTransition(reservation.getStatus(), request.getStatus());
        reservation.changeStatus(request.getStatus());
        return toResponse(reservationRepository.save(reservation));
    }

    private void validateStatusTransition(ReservationStatus currentStatus, ReservationStatus nextStatus) {
        if (currentStatus == nextStatus) {
            return;
        }

        boolean allowed = currentStatus == ReservationStatus.RESERVED
                && (nextStatus == ReservationStatus.CANCELLED || nextStatus == ReservationStatus.COMPLETED);

        if (!allowed) {
            throw new IllegalArgumentException("Only reserved reservations can be changed to cancelled or completed.");
        }
    }

    private void validateReservationSlot(Integer designerId,
                                         LocalDate reservationDate,
                                         LocalTime reservationTime,
                                         Integer reservationId) {
        boolean exists = reservationId == null
                ? reservationSlotRepository.existsByDesigner_DesignerIdAndReservationDateAndSlotTime(
                designerId, reservationDate, reservationTime
        )
                : reservationSlotRepository.existsByDesigner_DesignerIdAndReservationDateAndSlotTimeAndReservation_ReservationIdNot(
                designerId, reservationDate, reservationTime, reservationId
        );

        if (exists) {
            throw new IllegalArgumentException("This time slot is already reserved.");
        }
    }

    private void saveReservationSlot(Reservation reservation) {
        ReservationSlot slot = ReservationSlot.builder()
                .reservation(reservation)
                .designer(reservation.getDesigner())
                .reservationDate(reservation.getReservationDate())
                .slotTime(reservation.getReservationTime())
                .build();
        reservation.addReservationSlot(slot);
        reservationSlotRepository.save(slot);
    }

    private void validateDesignerAndServiceMatch(Designer designer, SalonService salonService) {
        if (designer.getSalon() == null
                || salonService.getSalon() == null
                || !designer.getSalon().getSalonId().equals(salonService.getSalon().getSalonId())) {
            throw new IllegalArgumentException("Designer and service must belong to the same salon.");
        }
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .reservationId(reservation.getReservationId())
                .memberId(reservation.getMember().getMemberId())
                .designerId(reservation.getDesigner().getDesignerId())
                .designerName(reservation.getDesigner().getName())
                .salonServiceId(reservation.getSalonService().getServiceId())
                .serviceName(reservation.getSalonService().getName())
                .reservationDate(reservation.getReservationDate())
                .reservationTime(reservation.getReservationTime())
                .status(reservation.getStatus())
                .totalPrice(reservation.getTotalPrice())
                .paymentMethod(reservation.getPaymentMethod())
                .paymentMethodLabel(getPaymentMethodLabel(reservation.getPaymentMethod()))
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }

    private String getPaymentMethodLabel(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return "-";
        }

        return switch (paymentMethod) {
            case CARD -> "Card";
            case CASH -> "Cash";
            case KAKAO_PAY -> "Kakao Pay";
            case NAVER_PAY -> "Naver Pay";
        };
    }

    public void validateReservationAccess(Integer reservationId, String loginMemberId, boolean isAdmin) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (!isAdmin && !reservation.getMember().getMemberId().equals(loginMemberId)) {
            throw new AccessDeniedException("You can only access your own reservation.");
        }
    }

    public void validateMemberAccess(String targetMemberId, String loginMemberId, boolean isAdmin) {
        if (!isAdmin && !targetMemberId.equals(loginMemberId)) {
            throw new AccessDeniedException("You can only access your own reservations.");
        }
    }
}
