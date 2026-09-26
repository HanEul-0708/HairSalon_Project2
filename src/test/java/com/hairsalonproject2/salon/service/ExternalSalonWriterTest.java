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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalSalonWriterTest {

 @Test
 void syncSkipsMissingIdsAndSavesEachNormalizedExternalIdOnce() {
  Salon salon = Salon.builder().salonId(20).reservable(false).build();
  var place = KakaoPlaceSearchResult.builder().externalId(" 20 ").placeName("Hair").addressName("Seoul").build();
  when(salonRepository.findBySourceTypeAndExternalId("KAKAO", "20")).thenReturn(Optional.of(salon));
  when(salonRepository.save(salon)).thenReturn(salon);
  var ids = externalSalonWriter.saveKakaoPlaces(List.of(place, place,
          KakaoPlaceSearchResult.builder().externalId(" ").build(),
          KakaoPlaceSearchResult.builder().build()));
  assertThat(ids).containsExactly(20);
  assertThat(salon.getReservable()).isFalse();
  verify(salonRepository, times(1)).save(salon);
  verifyNoInteractions(salonSeedDataFactory);
 }

 @Mock
 private SalonRepository salonRepository;

 @Mock
 private DesignerRepository designerRepository;

 @Mock
 private SalonServiceRepository salonServiceRepository;

 @Mock
 private SalonSeedDataFactory salonSeedDataFactory;

 @Mock
 private MemberRepository memberRepository;

 @Mock
 private DummyTimelineService dummyTimelineService;

 @Mock
 private CatalogIntegrityService catalogIntegrityService;

 @InjectMocks
 private ExternalSalonWriter externalSalonWriter;

 @Test
 void syncFromKakaoStoresPhoneReturnedByApi() {
  KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder().externalId("kakao-1").placeName("Test Hair").addressName("Seoul Gangnam").roadAddressName("1 Teheran-ro").phone("02-123-4567").placeUrl("https://place.map.kakao.com/1").build();
  when(salonRepository.findBySourceTypeAndExternalId("KAKAO", "kakao-1")).thenReturn(Optional.empty());
  when(designerRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
  when(salonServiceRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
  when(salonSeedDataFactory.createDesigners(any(Salon.class))).thenReturn(List.of());
  when(salonSeedDataFactory.createServices(any(Salon.class))).thenReturn(List.of());
  when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
   Salon salon = invocation.getArgument(0);
   salon.setSalonId(1);
   return salon;
  });
  when(designerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
  List<Integer> savedIds = externalSalonWriter.saveKakaoPlaces(List.of(result));
  ArgumentCaptor<Salon> salonCaptor = ArgumentCaptor.forClass(Salon.class);
  verify(salonRepository).save(salonCaptor.capture());
  assertThat(savedIds).containsExactly(1);
  assertThat(salonCaptor.getValue().getReservable()).isFalse();
  assertThat(salonCaptor.getValue().getPhone()).isEqualTo("02-123-4567");
  assertThat(salonCaptor.getValue().getPlaceUrl()).isEqualTo("https://place.map.kakao.com/1");
  verify(designerRepository).saveAll(any());
  verify(salonServiceRepository).saveAll(any());
 }

 @Test
 void syncFromKakaoStoresNullWhenApiFieldsAreBlank() {
  KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder().externalId("kakao-2").placeName("Test Hair").addressName("Seoul Gangnam").roadAddressName(" ").phone(" ").placeUrl("").build();
  when(salonRepository.findBySourceTypeAndExternalId("KAKAO", "kakao-2")).thenReturn(Optional.empty());
  when(designerRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
  when(salonServiceRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
  when(salonSeedDataFactory.createDesigners(any(Salon.class))).thenReturn(List.of());
  when(salonSeedDataFactory.createServices(any(Salon.class))).thenReturn(List.of());
  when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
   Salon salon = invocation.getArgument(0);
   salon.setSalonId(2);
   return salon;
  });
  when(designerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
  externalSalonWriter.saveKakaoPlaces(List.of(result));
  ArgumentCaptor<Salon> salonCaptor = ArgumentCaptor.forClass(Salon.class);
  verify(salonRepository).save(salonCaptor.capture());
  assertThat(salonCaptor.getValue().getRoadAddress()).isNull();
  assertThat(salonCaptor.getValue().getPhone()).isNull();
  assertThat(salonCaptor.getValue().getPlaceUrl()).isNull();
 }

 @Test
 void syncFromKakaoDoesNotCreateDefaultsForExistingSalon() {
  KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder().externalId("kakao-3").placeName("Test Hair").addressName("Seoul Gangnam").build();
  Salon existingSalon = new Salon();
  existingSalon.setSalonId(3);
  existingSalon.setExternalId("kakao-3");
  existingSalon.setReservable(true);
  existingSalon.setDescription("운영자가 설정한 소개");
  when(salonRepository.findBySourceTypeAndExternalId("KAKAO", "kakao-3")).thenReturn(Optional.of(existingSalon));
  when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> invocation.getArgument(0));
  externalSalonWriter.saveKakaoPlaces(List.of(result));
  verify(salonRepository).save(existingSalon);
  assertThat(existingSalon.getReservable()).isTrue();
  assertThat(existingSalon.getDescription()).isEqualTo("운영자가 설정한 소개");
  verify(salonSeedDataFactory, never()).createDesigners(any());
  verify(salonSeedDataFactory, never()).createServices(any());
 }

 @Test
 void syncFromKakaoUsesFactoryForNewSalon() {
  KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder().externalId("kakao-4").placeName("Rule Hair").addressName("Seoul Gangnam").build();
  List<Designer> designers = List.of(Designer.builder().name("Rule Hair Stylist").careerYears(5).build());
  List<SalonService> services = List.of(SalonService.builder().name("Perm").price(120000).duration(150).build());
  when(salonRepository.findBySourceTypeAndExternalId("KAKAO", "kakao-4")).thenReturn(Optional.empty());
  when(designerRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
  when(salonServiceRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
  when(salonSeedDataFactory.createDesigners(any(Salon.class))).thenReturn(designers);
  when(salonSeedDataFactory.createServices(any(Salon.class))).thenReturn(services);
  when(memberRepository.findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(MemberRole.DESIGNER, MemberStatus.ACTIVE)).thenReturn(Optional.empty());
  when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
   Salon salon = invocation.getArgument(0);
   salon.setSalonId(4);
   return salon;
  });
  when(designerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
  externalSalonWriter.saveKakaoPlaces(List.of(result));
  verify(salonSeedDataFactory, times(1)).createDesigners(any(Salon.class));
  verify(salonSeedDataFactory, times(1)).createServices(any(Salon.class));
  verify(designerRepository).saveAll(designers);
  verify(salonServiceRepository).saveAll(services);
 }

 @Test
 void syncFromKakaoLinksHighestCareerDesignerToAvailableDesignerMember() {
  KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder().externalId("kakao-5").placeName("Link Hair").addressName("Seoul Gangnam").build();
  Designer junior = Designer.builder().designerId(10).name("Link Hair Stylist").careerYears(2).build();
  Designer senior = Designer.builder().designerId(11).name("Link Hair Director").careerYears(9).build();
  Designer mid = Designer.builder().designerId(12).name("Link Hair Senior Stylist").careerYears(5).build();
  Designer unknownCareer = Designer.builder().designerId(13).name("Unknown Career").careerYears(null).build();
  Member designerMember = Member.builder().memberId("designer999").password("encoded").name("Old Name").phone("010-9999-9999").email("designer999@example.com").role(MemberRole.DESIGNER).status(MemberStatus.ACTIVE).build();
  when(salonRepository.findBySourceTypeAndExternalId("KAKAO", "kakao-5")).thenReturn(Optional.empty());
  when(designerRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
  when(salonServiceRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
  when(salonSeedDataFactory.createDesigners(any(Salon.class))).thenReturn(List.of(junior, senior, mid, unknownCareer));
  when(salonSeedDataFactory.createServices(any(Salon.class))).thenReturn(List.of());
  when(memberRepository.findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(MemberRole.DESIGNER, MemberStatus.ACTIVE)).thenReturn(Optional.of(designerMember));
  when(catalogIntegrityService.validateDesignerMember("designer999", 11)).thenReturn(designerMember);
  when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
   Salon salon = invocation.getArgument(0);
   salon.setSalonId(5);
   return salon;
  });
  when(designerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
  externalSalonWriter.saveKakaoPlaces(List.of(result));
  assertThat(senior.getMember()).isEqualTo(designerMember);
  assertThat(designerMember.getName()).isEqualTo("Link Hair Director");
  assertThat(junior.getMember()).isNull();
  assertThat(mid.getMember()).isNull();
  assertThat(unknownCareer.getMember()).isNull();
 }

 @Test
 void staleAutoLinkCandidateAbortsWholeWriteForFreshTransactionRetry() {
  Designer target = Designer.builder().designerId(11).name("Director").careerYears(9).build();
  Member candidate = Member.builder().memberId("designer999").role(MemberRole.DESIGNER)
    .status(MemberStatus.ACTIVE).build();
  prepareNewSalonWithDesigner(target, candidate);
  when(catalogIntegrityService.validateDesignerMember("designer999", 11))
    .thenThrow(new CatalogConflictException("memberId", "Already linked"));

  assertThatThrownBy(() -> externalSalonWriter.saveKakaoPlaces(List.of(newPlace())))
    .isInstanceOf(ExternalSalonWriteConflictException.class)
    .hasCauseInstanceOf(CatalogConflictException.class);
  assertThat(target.getMember()).isNull();
  verify(salonSeedDataFactory, never()).createServices(any());
 }

 @Test
 void autoLinkUsesStatusReadUnderMemberLock() {
  Designer target = Designer.builder().designerId(11).name("Director").careerYears(9).build();
  Member candidate = Member.builder().memberId("designer999").role(MemberRole.DESIGNER)
    .status(MemberStatus.ACTIVE).build();
  Member locked = Member.builder().memberId("designer999").role(MemberRole.DESIGNER)
    .status(MemberStatus.DELETED).build();
  prepareNewSalonWithDesigner(target, candidate);
  when(catalogIntegrityService.validateDesignerMember("designer999", 11)).thenReturn(locked);

  assertThatThrownBy(() -> externalSalonWriter.saveKakaoPlaces(List.of(newPlace())))
    .isInstanceOf(ExternalSalonWriteConflictException.class);
  assertThat(target.getMember()).isNull();
 }

 private void prepareNewSalonWithDesigner(Designer target, Member candidate) {
  when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
   Salon salon = invocation.getArgument(0);
   salon.setSalonId(5);
   return salon;
  });
  when(salonSeedDataFactory.createDesigners(any())).thenReturn(List.of(target));
  when(designerRepository.saveAll(any())).thenReturn(List.of(target));
  when(memberRepository.findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(
    MemberRole.DESIGNER, MemberStatus.ACTIVE)).thenReturn(Optional.of(candidate));
 }

 private KakaoPlaceSearchResult newPlace() {
  return KakaoPlaceSearchResult.builder().externalId("kakao-new").placeName("Hair").addressName("Seoul").build();
 }
}
