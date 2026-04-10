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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalSalonSyncServiceTest {

    @Mock
    private KakaoLocalSearchClient kakaoLocalSearchClient;

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

    @InjectMocks
    private ExternalSalonSyncService externalSalonSyncService;

    @Test
    void syncFromKakaoStoresPhoneReturnedByApi() {
        KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder()
                .externalId("kakao-1")
                .placeName("Test Hair")
                .addressName("Seoul Gangnam")
                .roadAddressName("1 Teheran-ro")
                .phone("02-123-4567")
                .placeUrl("https://place.map.kakao.com/1")
                .build();

        when(kakaoLocalSearchClient.searchSalons("Test", "Gangnam", 1, 15)).thenReturn(List.of(result));
        when(salonRepository.findByExternalId("kakao-1")).thenReturn(Optional.empty());
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

        List<Integer> savedIds = externalSalonSyncService.syncFromKakao("Test", "Gangnam");

        ArgumentCaptor<Salon> salonCaptor = ArgumentCaptor.forClass(Salon.class);
        verify(salonRepository).save(salonCaptor.capture());

        assertThat(savedIds).containsExactly(1);
        assertThat(salonCaptor.getValue().getPhone()).isEqualTo("02-123-4567");
        assertThat(salonCaptor.getValue().getPlaceUrl()).isEqualTo("https://place.map.kakao.com/1");
        verify(designerRepository).saveAll(any());
        verify(salonServiceRepository).saveAll(any());
    }

    @Test
    void syncFromKakaoStoresNullWhenApiFieldsAreBlank() {
        KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder()
                .externalId("kakao-2")
                .placeName("Test Hair")
                .addressName("Seoul Gangnam")
                .roadAddressName(" ")
                .phone(" ")
                .placeUrl("")
                .build();

        when(kakaoLocalSearchClient.searchSalons("Test", "Gangnam", 1, 15)).thenReturn(List.of(result));
        when(salonRepository.findByExternalId("kakao-2")).thenReturn(Optional.empty());
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

        externalSalonSyncService.syncFromKakao("Test", "Gangnam");

        ArgumentCaptor<Salon> salonCaptor = ArgumentCaptor.forClass(Salon.class);
        verify(salonRepository).save(salonCaptor.capture());

        assertThat(salonCaptor.getValue().getRoadAddress()).isNull();
        assertThat(salonCaptor.getValue().getPhone()).isNull();
        assertThat(salonCaptor.getValue().getPlaceUrl()).isNull();
    }

    @Test
    void syncFromKakaoDoesNotCreateDefaultsForExistingSalon() {
        KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder()
                .externalId("kakao-3")
                .placeName("Test Hair")
                .addressName("Seoul Gangnam")
                .build();
        Salon existingSalon = new Salon();
        existingSalon.setSalonId(3);
        existingSalon.setExternalId("kakao-3");

        when(kakaoLocalSearchClient.searchSalons("Test", "Gangnam", 1, 15)).thenReturn(List.of(result));
        when(salonRepository.findByExternalId("kakao-3")).thenReturn(Optional.of(existingSalon));
        when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> invocation.getArgument(0));

        externalSalonSyncService.syncFromKakao("Test", "Gangnam");

        verify(salonRepository).save(existingSalon);
        verify(salonSeedDataFactory, never()).createDesigners(any());
        verify(salonSeedDataFactory, never()).createServices(any());
    }

    @Test
    void syncFromKakaoUsesFactoryForNewSalon() {
        KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder()
                .externalId("kakao-4")
                .placeName("Rule Hair")
                .addressName("Seoul Gangnam")
                .build();
        List<Designer> designers = List.of(Designer.builder().name("Rule Hair Stylist").careerYears(5).build());
        List<SalonService> services = List.of(SalonService.builder().name("Perm").price(120000).duration(150).build());

        when(kakaoLocalSearchClient.searchSalons("Rule", "Gangnam", 1, 15)).thenReturn(List.of(result));
        when(salonRepository.findByExternalId("kakao-4")).thenReturn(Optional.empty());
        when(designerRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
        when(salonServiceRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
        when(salonSeedDataFactory.createDesigners(any(Salon.class))).thenReturn(designers);
        when(salonSeedDataFactory.createServices(any(Salon.class))).thenReturn(services);
        when(memberRepository.findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(MemberRole.DESIGNER, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
            Salon salon = invocation.getArgument(0);
            salon.setSalonId(4);
            return salon;
        });
        when(designerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        externalSalonSyncService.syncFromKakao("Rule", "Gangnam");

        verify(salonSeedDataFactory, times(1)).createDesigners(any(Salon.class));
        verify(salonSeedDataFactory, times(1)).createServices(any(Salon.class));
        verify(designerRepository).saveAll(designers);
        verify(salonServiceRepository).saveAll(services);
    }

    @Test
    void syncFromKakaoLinksHighestCareerDesignerToAvailableDesignerMember() {
        KakaoPlaceSearchResult result = KakaoPlaceSearchResult.builder()
                .externalId("kakao-5")
                .placeName("Link Hair")
                .addressName("Seoul Gangnam")
                .build();
        Designer junior = Designer.builder().designerId(10).name("Link Hair Stylist").careerYears(2).build();
        Designer senior = Designer.builder().designerId(11).name("Link Hair Director").careerYears(9).build();
        Designer mid = Designer.builder().designerId(12).name("Link Hair Senior Stylist").careerYears(5).build();
        Member designerMember = Member.builder()
                .memberId("designer999")
                .password("encoded")
                .name("Old Name")
                .phone("010-9999-9999")
                .email("designer999@example.com")
                .role(MemberRole.DESIGNER)
                .status(MemberStatus.ACTIVE)
                .build();

        when(kakaoLocalSearchClient.searchSalons("Link", "Gangnam", 1, 15)).thenReturn(List.of(result));
        when(salonRepository.findByExternalId("kakao-5")).thenReturn(Optional.empty());
        when(designerRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
        when(salonServiceRepository.existsBySalonSalonId(anyInt())).thenReturn(false);
        when(salonSeedDataFactory.createDesigners(any(Salon.class))).thenReturn(List.of(junior, senior, mid));
        when(salonSeedDataFactory.createServices(any(Salon.class))).thenReturn(List.of());
        when(memberRepository.findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(MemberRole.DESIGNER, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(designerMember));
        when(salonRepository.save(any(Salon.class))).thenAnswer(invocation -> {
            Salon salon = invocation.getArgument(0);
            salon.setSalonId(5);
            return salon;
        });
        when(designerRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        externalSalonSyncService.syncFromKakao("Link", "Gangnam");

        assertThat(senior.getMember()).isEqualTo(designerMember);
        assertThat(designerMember.getName()).isEqualTo("Link Hair Director");
        assertThat(junior.getMember()).isNull();
        assertThat(mid.getMember()).isNull();
    }
}
