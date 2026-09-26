package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.common.catalog.CatalogConflictException;
import com.hairsalonproject2.common.catalog.CatalogIntegrityService;
import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.entity.SalonService;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ExternalSalonWriter {
 private final SalonRepository salonRepository;
 private final DesignerRepository designerRepository;
 private final SalonServiceRepository salonServiceRepository;
 private final SalonSeedDataFactory salonSeedDataFactory;
 private final MemberRepository memberRepository;
 private final DummyTimelineService dummyTimelineService;
 private final CatalogIntegrityService catalogIntegrityService;

 private String normalize(String value) {
  if (isBlank(value)) return null;
  return value.trim();
 }

 private boolean isBlank(String value) {
  return value == null || value.isBlank();
 }

 public List<Integer> saveKakaoPlaces(List<KakaoPlaceSearchResult> results) {
  // Take salon locks in a consistent order when two searches overlap.
  var placesById = new TreeMap<String, KakaoPlaceSearchResult>();
  for (KakaoPlaceSearchResult place : results) {
   String externalId = place == null ? null : normalize(place.getExternalId());
   if (externalId != null) placesById.putIfAbsent(externalId, place);
  }
  List<Integer> savedIds = new ArrayList<>();
  for (var entry : placesById.entrySet()) {
   String externalId = entry.getKey();
   KakaoPlaceSearchResult place = entry.getValue();
   Salon salon = salonRepository.findBySourceTypeAndExternalId("KAKAO", externalId).orElseGet(Salon::new);
   boolean isNewSalon = salon.getSalonId() == null;
   salon.setExternalId(externalId);
   salon.setSourceType("KAKAO");
   salon.setName(normalize(place.getPlaceName()));
   salon.setAddress(normalize(place.getAddressName()));
   salon.setRoadAddress(normalize(place.getRoadAddressName()));
   salon.setPhone(normalize(place.getPhone()));
   salon.setPlaceUrl(normalize(place.getPlaceUrl()));
   if (isNewSalon) salon.setReservable(Boolean.FALSE);
   Salon savedSalon = salonRepository.save(salon);
   if (isNewSalon) {
    List<Designer> designers = createDefaultDesigners(savedSalon);
    List<SalonService> services = createDefaultServices(savedSalon);
    dummyTimelineService.alignSalonSeedTimeline(savedSalon.getSalonId(), designers.stream().map(Designer::getDesignerId).toList(), services.stream().map(SalonService::getServiceId).toList());
   }
   savedIds.add(savedSalon.getSalonId());
  }
  return savedIds;
 }

 private List<Designer> createDefaultDesigners(Salon salon) {
  if (salon.getSalonId() == null || designerRepository.existsBySalonSalonId(salon.getSalonId())) return List.of();
  List<Designer> designers = designerRepository.saveAll(salonSeedDataFactory.createDesigners(salon));
  linkTopCareerDesignerToAvailableMember(designers);
  return designers;
 }

 private List<SalonService> createDefaultServices(Salon salon) {
  if (salon.getSalonId() == null || salonServiceRepository.existsBySalonSalonId(salon.getSalonId())) return List.of();
  return salonServiceRepository.saveAll(salonSeedDataFactory.createServices(salon));
 }

 private void linkTopCareerDesignerToAvailableMember(List<Designer> designers) {
  if (designers == null || designers.isEmpty()) return;
  Member availableMember = memberRepository.findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(MemberRole.DESIGNER, MemberStatus.ACTIVE).orElse(null);
  if (availableMember == null) return;
  Designer targetDesigner = designers.stream().filter(designer -> designer.getMember() == null).max(Comparator.comparing(Designer::getCareerYears, Comparator.nullsFirst(Integer::compareTo)).thenComparing(Designer::getDesignerId, Comparator.nullsFirst(Integer::compareTo))).orElse(null);
  if (targetDesigner == null) return;
  try {
   Member lockedMember = catalogIntegrityService.validateDesignerMember(availableMember.getMemberId(), targetDesigner.getDesignerId());
   if (lockedMember.getStatus() != MemberStatus.ACTIVE) {
    throw new ExternalSalonWriteConflictException("자동 연결 후보 회원의 상태가 변경되었습니다.");
   }
   targetDesigner.setMember(lockedMember);
   lockedMember.updateProfile(targetDesigner.getName(), lockedMember.getPhone(), lockedMember.getEmail());
  } catch (CatalogConflictException ex) {
   // Abort the entire transaction; the orchestrator retries with a fresh snapshot.
   throw new ExternalSalonWriteConflictException("자동 연결 후보 회원의 연결 또는 권한이 변경되었습니다.", ex);
  }
 }
}
