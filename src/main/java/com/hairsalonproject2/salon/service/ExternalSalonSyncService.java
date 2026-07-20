package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.common.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.common.service.DummyTimelineService;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.designer.repository.DesignerRepository;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.member.repository.MemberRepository;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import com.hairsalonproject2.salonservice.repository.SalonServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExternalSalonSyncService {
 private final KakaoLocalSearchClient kakaoLocalSearchClient;
 private final SalonRepository salonRepository;
 private final DesignerRepository designerRepository;
 private final SalonServiceRepository salonServiceRepository;
 private final SalonSeedDataFactory salonSeedDataFactory;
 private final MemberRepository memberRepository;
 private final DummyTimelineService dummyTimelineService;

 private String normalize(String value) { if (isBlank(value)) return null; return value.trim(); }
 private boolean isBlank(String value) { return value == null || value.isBlank(); }
 public List<Integer> syncFromKakao(String keyword, String region) {
  List<KakaoPlaceSearchResult> results = kakaoLocalSearchClient.searchSalons(keyword, region, 1, 15);
  List<Integer> savedIds = new ArrayList<>(); for (KakaoPlaceSearchResult place : results) {
   Salon salon = salonRepository.findByExternalId(place.getExternalId()).orElseGet(Salon::new);
   boolean isNewSalon = salon.getSalonId() == null; salon.setExternalId(place.getExternalId());
   salon.setSourceType("KAKAO"); salon.setName(normalize(place.getPlaceName()));
   salon.setAddress(normalize(place.getAddressName())); salon.setRoadAddress(normalize(place.getRoadAddressName()));
   salon.setPhone(normalize(place.getPhone())); salon.setPlaceUrl(normalize(place.getPlaceUrl()));
   if (salon.getReservable() == null) salon.setReservable(Boolean.FALSE);
   Salon savedSalon = salonRepository.save(salon);
   if (isNewSalon) { List<Designer> designers = createDefaultDesigners(savedSalon);
	List<com.hairsalonproject2.salonservice.entity.SalonService> services = createDefaultServices(savedSalon);
	dummyTimelineService.alignSalonSeedTimeline(savedSalon.getSalonId(), designers.stream().map(Designer::getDesignerId).toList(), services.stream().map(com.hairsalonproject2.salonservice.entity.SalonService::getServiceId).toList()); } savedIds.add(savedSalon.getSalonId()); } return savedIds;
 }//KakaoLocalSearchClient를 호출하여 카카오 API로부터 1페이지 기준 최대 15개의 미용실 검색 결과를 수집합니다. 수집된 각 미용실의 외부 고유 ID(externalId)를 확인하여 기존 데이터베이스에 등록된 엔티티가 있는지 조회하고, 없다면 새로 생성합니다. 외부 데이터의 출처를 "KAKAO"로 명시하고 미용실 이름, 지번 주소, 도로명 주소, 전화번호, 상세 웹 페이지 주소 등을 가공하여 업데이트한 후 저장합니다. 새로 등록된 미용실인 경우, 하위의 기본 디자이너 데이터와 기본 시술 품목 데이터를 추가로 생성하며 더미 타임라인을 정렬하는 서비스를 연동합니다. 처리가 완료된 미용실들의 내부 식별자(salonId) 목록을 리스트 형태로 수집하여 반환합니다.

 private List<Designer> createDefaultDesigners(Salon salon) {
  if (salon.getSalonId() == null || designerRepository.existsBySalonSalonId(salon.getSalonId())) return List.of(); List<Designer> designers = designerRepository.saveAll(salonSeedDataFactory.createDesigners(salon)); linkTopCareerDesignerToAvailableMember(designers); return designers;
 }//전달받은 미용실 객체의 ID가 없거나 이미 해당 미용실에 등록된 디자이너 데이터가 존재할 경우 중복 방지를 위해 즉시 빈 리스트를 반환합니다. 신규 미용실인 경우 SalonSeedDataFactory를 통해 임의의 초기 디자이너 목록을 생성하고 데이터베이스에 일괄 저장합니다. 저장된 디자이너들을 회원 계정과 연결하기 위해 linkTopCareerDesignerToAvailableMember 메소드를 호출한 후 최종 리스트를 반환합니다.

 private List<com.hairsalonproject2.salonservice.entity.SalonService> createDefaultServices(Salon salon) {
  if (salon.getSalonId() == null || salonServiceRepository.existsBySalonSalonId(salon.getSalonId())) return List.of(); return salonServiceRepository.saveAll(salonSeedDataFactory.createServices(salon));
 }//전달받은 미용실 객체의 ID가 누락되었거나 이미 해당 미용실 소속의 시술 서비스가 등록되어 있다면 빈 리스트를 반환합니다. 중복이 없는 상태임이 확인되면 SalonSeedDataFactory를 사용하여 기본 시술 품목 리스트를 생성한 뒤 데이터베이스에 일괄 저장하고 반환합니다.

 private void linkTopCareerDesignerToAvailableMember(List<Designer> designers) {
  if (designers == null || designers.isEmpty()) return; Member availableMember = memberRepository.findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(MemberRole.DESIGNER, MemberStatus.ACTIVE).orElse(null); if (availableMember == null) return;
  Designer targetDesigner = designers.stream().filter(designer -> designer.getMember() == null).max(Comparator.comparing(Designer::getCareerYears, Comparator.nullsLast(Integer::compareTo)).thenComparing(Designer::getDesignerId, Comparator.nullsLast(Integer::compareTo))).orElse(null);
  if (targetDesigner == null) return; targetDesigner.setMember(availableMember);
  availableMember.updateProfile(targetDesigner.getName(), availableMember.getPhone(), availableMember.getEmail());
 }//생성된 디자이너 데이터 중 가장 역량이 높은 대상을 시스템 내 실제 회원 계정과 매핑하는 역할을 담당합니다. 회원 테이블에서 역할이 DESIGNER이고 상태가 ACTIVE이면서 현재 연결된 디자이너 프로필이 없는 사용자를 생성일 및 ID 순으로 정렬하여 가장 먼저 가입한 한 명을 조회합니다. 연결 가능한 회원이 존재하면, 전달받은 디자이너 목록 중 아직 회원 계정이 연결되지 않은 대상들을 필터링합니다. 그중 경력(careerYears)이 가장 높은 디자이너를 선별하며, 경력이 동일할 경우 식별자(designerId)가 더 큰 대상을 최종 타깃으로 결정합니다. 결정된 디자이너 엔티티에 회원 정보를 할당하고, 해당 회원 엔티티의 프로필 정보(이름, 전화번호, 이메일)를 디자이너의 상세 정보로 갱신합니다.
}
