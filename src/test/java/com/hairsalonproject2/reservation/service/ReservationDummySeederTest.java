package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.entity.ReservationSlot;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.reservation.repository.ReservationSlotRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationDummySeederTest {

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
 @Mock
 private DummyTimelineService dummyTimelineService;

 @InjectMocks
 private ReservationDummySeeder reservationDummySeeder;

 @Test
 void seedReservationsCreatesReservationsUpToTargetCount() {
  ReflectionTestUtils.setField(reservationDummySeeder, "targetReservationCount", 4);
  Salon salon = Salon.builder().salonId(1).name("테스트살롱").build();
  Designer designer = Designer.builder().designerId(1).salon(salon).name("김하늘 점장").build();
  SalonService service = SalonService.builder().serviceId(1).salon(salon).name("여성 커트").price(30000).duration(60).build();
  Member user = Member.builder().memberId("user1").name("회원1").role(MemberRole.USER).status(MemberStatus.ACTIVE).build();
  when(reservationRepository.count()).thenReturn(0L);
  when(memberRepository.findByRoleAndStatusOrderByCreatedAtAsc(MemberRole.USER, MemberStatus.ACTIVE)).thenReturn(List.of(user));
  when(designerRepository.findAll()).thenReturn(List.of(designer));
  when(salonServiceRepository.findAll()).thenReturn(List.of(service));
  when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
  when(reservationSlotRepository.save(any(ReservationSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
  int created = reservationDummySeeder.seedReservations();
  assertThat(created).isEqualTo(4);
  ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
  verify(reservationRepository, times(4)).save(reservationCaptor.capture());
  assertThat(reservationCaptor.getAllValues()).extracting(Reservation::getStatus).containsExactly(ReservationStatus.COMPLETED, ReservationStatus.COMPLETED, ReservationStatus.COMPLETED, ReservationStatus.COMPLETED);
  assertThat(reservationCaptor.getAllValues()).extracting(Reservation::getTotalPrice).allSatisfy(price -> assertThat((Integer) price).isGreaterThanOrEqualTo(30000));
  ArgumentCaptor<ReservationSlot> slotCaptor = ArgumentCaptor.forClass(ReservationSlot.class);
  verify(reservationSlotRepository, times(4)).save(slotCaptor.capture());
 }
}
