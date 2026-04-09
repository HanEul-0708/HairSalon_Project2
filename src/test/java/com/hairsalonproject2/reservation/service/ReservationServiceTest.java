package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.constant.PaymentMethod;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.reservation.dto.ReservationCreateRequest;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.reservation.dto.ReservationStatusUpdateRequest;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.reservation.repository.ReservationSlotRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationSlotRepository reservationSlotRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private DesignerRepository designerRepository;

    @Mock
    private SalonServiceRepository salonServiceRepository;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @Test
    void updateReservationStatusAllowsReservedToCompleted() throws Exception {
        Reservation reservation = reservationWithStatus(ReservationStatus.RESERVED);
        ReservationStatusUpdateRequest request = statusUpdateRequest(ReservationStatus.COMPLETED);

        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        reservationService.updateReservationStatus(1, request);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void updateReservationStatusRejectsCompletedToCancelled() throws Exception {
        Reservation reservation = reservationWithStatus(ReservationStatus.COMPLETED);
        ReservationStatusUpdateRequest request = statusUpdateRequest(ReservationStatus.CANCELLED);

        when(reservationRepository.findById(1)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.updateReservationStatus(1, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only reserved reservations can be changed to cancelled or completed.");
    }

    @Test
    void createReservationRejectsDesignerAndServiceFromDifferentSalon() throws Exception {
        Member member = Member.builder()
                .memberId("user01")
                .password("encoded-password")
                .name("Tester")
                .phone("010-1234-5678")
                .email("user01@example.com")
                .role(MemberRole.USER)
                .status(MemberStatus.ACTIVE)
                .build();
        Salon designerSalon = Salon.builder().salonId(1).name("Salon A").build();
        Salon serviceSalon = Salon.builder().salonId(2).name("Salon B").build();
        Designer designer = Designer.builder().designerId(1).name("Designer").salon(designerSalon).build();
        SalonService salonService = SalonService.builder().serviceId(1).name("Cut").price(30000).duration(60).salon(serviceSalon).build();
        ReservationCreateRequest request = reservationCreateRequest();

        when(memberRepository.findById("user01")).thenReturn(Optional.of(member));
        when(designerRepository.findById(1)).thenReturn(Optional.of(designer));
        when(salonServiceRepository.findById(1)).thenReturn(Optional.of(salonService));

        assertThatThrownBy(() -> reservationService.createReservation("user01", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Designer and service must belong to the same salon.");
    }

    private Reservation reservationWithStatus(ReservationStatus status) {
        return Reservation.builder()
                .member(Member.builder()
                        .memberId("user01")
                        .password("encoded-password")
                        .name("Tester")
                        .phone("010-1234-5678")
                        .email("user01@example.com")
                        .role(MemberRole.USER)
                        .status(MemberStatus.ACTIVE)
                        .build())
                .designer(Designer.builder()
                        .designerId(1)
                        .name("Designer")
                        .build())
                .salonService(com.hairsalonproject2.salonservice.entity.SalonService.builder()
                        .serviceId(1)
                        .name("Cut")
                        .price(30000)
                        .duration(60)
                        .build())
                .reservationDate(LocalDate.of(2026, 4, 8))
                .reservationTime(LocalTime.of(10, 0))
                .status(status)
                .totalPrice(30000)
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    private ReservationCreateRequest reservationCreateRequest() throws Exception {
        ReservationCreateRequest request = new ReservationCreateRequest();
        setField(request, "memberId", "user01");
        setField(request, "designerId", 1);
        setField(request, "salonServiceId", 1);
        setField(request, "reservationDate", LocalDate.of(2026, 4, 10));
        setField(request, "reservationTime", LocalTime.of(10, 0));
        setField(request, "totalPrice", 30000);
        setField(request, "paymentMethod", PaymentMethod.CARD);
        return request;
    }

    private ReservationStatusUpdateRequest statusUpdateRequest(ReservationStatus status) throws Exception {
        ReservationStatusUpdateRequest request = new ReservationStatusUpdateRequest();
        setField(request, "status", status);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
