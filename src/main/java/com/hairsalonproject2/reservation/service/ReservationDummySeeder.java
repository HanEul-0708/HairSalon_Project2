package com.hairsalonproject2.reservation.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.constant.PaymentMethod;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.designer.constant.DesignerSpecialty;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.reservation.entity.ReservationSlot;
import com.hairsalonproject2.reservation.repository.ReservationRepository;
import com.hairsalonproject2.reservation.repository.ReservationSlotRepository;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationDummySeeder {

 private static final List<LocalTime> DIRECTOR_TIMES = List.of(LocalTime.of(11, 0), LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(17, 0));

 private static final List<LocalTime> STAFF_TIMES = List.of(LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0), LocalTime.of(14, 0), LocalTime.of(15, 0), LocalTime.of(16, 0), LocalTime.of(18, 0));

 private final ReservationRepository reservationRepository;
 private final ReservationSlotRepository reservationSlotRepository;
 private final MemberRepository memberRepository;
 private final DesignerRepository designerRepository;
 private final SalonServiceRepository salonServiceRepository;
 private final DummyTimelineService dummyTimelineService;

 @Value("${app.seed.reservations.target-count:60}")
 private int targetReservationCount;

 @Transactional
 public int seedReservations() {
  long existingCount = reservationRepository.count();
  if (existingCount >= targetReservationCount) {
   return 0;
  }
  List<Member> members = memberRepository.findByRoleAndStatusOrderByCreatedAtAsc(MemberRole.USER, MemberStatus.ACTIVE);
  List<Designer> designers = designerRepository.findAll().stream().sorted(Comparator.comparing(Designer::getDesignerId)).toList();
  Map<Integer, List<SalonService>> servicesBySalonId = salonServiceRepository.findAll().stream().sorted(Comparator.comparing(SalonService::getServiceId)).collect(Collectors.groupingBy(service -> service.getSalon().getSalonId()));
  if (members.isEmpty() || designers.isEmpty() || servicesBySalonId.isEmpty()) {
   return 0;
  }
  int toCreate = (int) (targetReservationCount - existingCount);
  int created = 0;
  for (int index = 0; index < toCreate; index++) {
   Designer designer = designers.get(index % designers.size());
   List<SalonService> salonServices = servicesBySalonId.get(designer.getSalon().getSalonId());
   if (salonServices == null || salonServices.isEmpty()) {
	continue;
   }
   Member member = members.get((index * 2) % members.size());
   SalonService salonService = chooseService(salonServices, designer, index);
   ReservationStatus status = determineStatus(index);
   LocalDate reservationDate = determineDate(index, status, designer);
   LocalTime reservationTime = determineTime(index, designer);
   Reservation reservation = Reservation.builder().member(member).designer(designer).salonService(salonService).reservationDate(reservationDate).reservationTime(reservationTime).status(status).totalPrice(calculatePrice(salonService, designer, status)).paymentMethod(determinePaymentMethod(index, status)).build();
   Reservation savedReservation = reservationRepository.save(reservation);
   Integer slotId = null;
   if (status != ReservationStatus.CANCELLED) {
	ReservationSlot slot = ReservationSlot.builder().reservation(savedReservation).designer(designer).reservationDate(reservationDate).slotTime(reservationTime).build();
	savedReservation.addReservationSlot(slot);
	ReservationSlot savedSlot = reservationSlotRepository.save(slot);
	slotId = savedSlot.getSlotId();
   }
   dummyTimelineService.alignReservationTimeline(savedReservation.getReservationId(), slotId, reservationDate, reservationTime, latestDependencyCreatedAt(member, designer, salonService));
   created++;
  }
  return created;
 }

 private SalonService chooseService(List<SalonService> salonServices, Designer designer, int index) {
  List<SalonService> preferredServices = salonServices.stream().filter(service -> matchesSpecialty(service, designer.getSpecialty())).toList();
  List<SalonService> pool = preferredServices.isEmpty() ? salonServices : preferredServices;
  int weightedIndex = Math.floorMod(index + safeCareerYears(designer), pool.size());
  return pool.get(weightedIndex);
 }

 private boolean matchesSpecialty(SalonService service, DesignerSpecialty specialty) {
  String name = service.getName() == null ? "" : service.getName().toLowerCase();
  return switch (specialty) {
   case CUT -> name.contains("커트");
   case PERM -> name.contains("펌");
   case COLOR -> name.contains("염색");
   case STYLING -> name.contains("스타일");
  };
 }

 private ReservationStatus determineStatus(int index) {
  int mod = index % 10;
  if (mod <= 4) {
   return ReservationStatus.COMPLETED;
  }
  if (mod <= 7) {
   return ReservationStatus.RESERVED;
  }
  return ReservationStatus.CANCELLED;
 }

 private LocalDate determineDate(int index, ReservationStatus status, Designer designer) {
  int cycle = index / 7;
  int designerOffset = designer.getDesignerId() == null ? 0 : designer.getDesignerId() % 5;
  if (status == ReservationStatus.RESERVED) {
   return LocalDate.now().plusDays(1L + ((cycle + designerOffset) % 14));
  }
  return LocalDate.now().minusDays(1L + ((cycle + designerOffset) % 30));
 }

 private LocalTime determineTime(int index, Designer designer) {
  List<LocalTime> timePool = isDirectorLevel(designer) ? DIRECTOR_TIMES : STAFF_TIMES;
  int weightedIndex = Math.floorMod(index + safeCareerYears(designer), timePool.size());
  return timePool.get(weightedIndex);
 }

 private int calculatePrice(SalonService salonService, Designer designer, ReservationStatus status) {
  int basePrice = salonService.getPrice() == null ? 0 : salonService.getPrice();
  int careerBonus = safeCareerYears(designer) * 1000;
  int titleBonus = isDirectorLevel(designer) ? 8000 : isSubDirectorLevel(designer) ? 4000 : 0;
  int completedBonus = status == ReservationStatus.COMPLETED ? 2000 : 0;
  return basePrice + careerBonus + titleBonus + completedBonus;
 }

 private PaymentMethod determinePaymentMethod(int index, ReservationStatus status) {
  if (status == ReservationStatus.CANCELLED) {
   return index % 2 == 0 ? PaymentMethod.CARD : PaymentMethod.KAKAO_PAY;
  }
  PaymentMethod[] methods = PaymentMethod.values();
  return methods[index % methods.length];
 }

 private boolean isDirectorLevel(Designer designer) {
  String name = designer.getName() == null ? "" : designer.getName();
  return name.endsWith("점장");
 }

 private boolean isSubDirectorLevel(Designer designer) {
  String name = designer.getName() == null ? "" : designer.getName();
  return name.endsWith("부점장");
 }

 private int safeCareerYears(Designer designer) {
  return designer.getCareerYears() == null ? 0 : designer.getCareerYears();
 }

 private java.time.LocalDateTime latestDependencyCreatedAt(Member member, Designer designer, SalonService salonService) {
  java.time.LocalDateTime memberCreatedAt = member.getCreatedAt();
  java.time.LocalDateTime designerCreatedAt = designer.getCreatedAt();
  java.time.LocalDateTime serviceCreatedAt = salonService.getCreatedAt();
  java.time.LocalDateTime latest = memberCreatedAt;
  if (latest == null || (designerCreatedAt != null && designerCreatedAt.isAfter(latest))) {
   latest = designerCreatedAt;
  }
  if (latest == null || (serviceCreatedAt != null && serviceCreatedAt.isAfter(latest))) {
   latest = serviceCreatedAt;
  }
  return latest == null ? java.time.LocalDateTime.now().minusDays(10) : latest;
 }
}
