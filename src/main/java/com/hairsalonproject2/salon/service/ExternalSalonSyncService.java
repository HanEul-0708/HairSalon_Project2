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

    public int syncFromSearch(String keyword, String region) {
        if (isBlank(keyword) && isBlank(region)) {
            return 0;
        }

        try {
            return syncFromKakao(keyword, region).size();
        } catch (RuntimeException e) {
            log.warn("Failed to sync salons from Kakao for keyword='{}', region='{}'", keyword, region, e);
            return 0;
        }
    }

    public List<Integer> syncFromKakao(String keyword, String region) {
        List<KakaoPlaceSearchResult> results = kakaoLocalSearchClient.searchSalons(keyword, region, 1, 15);
        List<Integer> savedIds = new ArrayList<>();

        for (KakaoPlaceSearchResult place : results) {
            Salon salon = salonRepository.findByExternalId(place.getExternalId()).orElseGet(Salon::new);
            boolean isNewSalon = salon.getSalonId() == null;

            salon.setExternalId(place.getExternalId());
            salon.setSourceType("KAKAO");
            salon.setName(normalize(place.getPlaceName()));
            salon.setAddress(normalize(place.getAddressName()));
            salon.setRoadAddress(normalize(place.getRoadAddressName()));
            salon.setPhone(normalize(place.getPhone()));
            salon.setLatitude(place.getLatitude());
            salon.setLongitude(place.getLongitude());
            salon.setPlaceUrl(normalize(place.getPlaceUrl()));
            if (salon.getReservable() == null) {
                salon.setReservable(Boolean.FALSE);
            }

            Salon savedSalon = salonRepository.save(salon);
            if (isNewSalon) {
                List<Designer> designers = createDefaultDesigners(savedSalon);
                List<com.hairsalonproject2.salonservice.entity.SalonService> services = createDefaultServices(savedSalon);
                dummyTimelineService.alignSalonSeedTimeline(
                        savedSalon.getSalonId(),
                        designers.stream().map(Designer::getDesignerId).toList(),
                        services.stream().map(com.hairsalonproject2.salonservice.entity.SalonService::getServiceId).toList()
                );
            }
            savedIds.add(savedSalon.getSalonId());
        }
        return savedIds;
    }

    private List<Designer> createDefaultDesigners(Salon salon) {
        if (salon.getSalonId() == null || designerRepository.existsBySalonSalonId(salon.getSalonId())) {
            return List.of();
        }

        List<Designer> designers = designerRepository.saveAll(salonSeedDataFactory.createDesigners(salon));
        linkTopCareerDesignerToAvailableMember(designers);
        return designers;
    }

    private List<com.hairsalonproject2.salonservice.entity.SalonService> createDefaultServices(Salon salon) {
        if (salon.getSalonId() == null || salonServiceRepository.existsBySalonSalonId(salon.getSalonId())) {
            return List.of();
        }
        return salonServiceRepository.saveAll(salonSeedDataFactory.createServices(salon));
    }

    private void linkTopCareerDesignerToAvailableMember(List<Designer> designers) {
        if (designers == null || designers.isEmpty()) {
            return;
        }

        Member availableMember = memberRepository
                .findFirstByRoleAndStatusAndDesignerIsNullOrderByCreatedAtAscMemberIdAsc(
                        MemberRole.DESIGNER,
                        MemberStatus.ACTIVE
                )
                .orElse(null);
        if (availableMember == null) {
            return;
        }

        Designer targetDesigner = designers.stream()
                .filter(designer -> designer.getMember() == null)
                .max(Comparator.comparing(
                                Designer::getCareerYears,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(
                                Designer::getDesignerId,
                                Comparator.nullsLast(Integer::compareTo)))
                .orElse(null);
        if (targetDesigner == null) {
            return;
        }

        targetDesigner.setMember(availableMember);
        String salonName = targetDesigner.getSalon() == null ? targetDesigner.getName() : targetDesigner.getSalon().getName();
        availableMember.updateProfile(salonName, availableMember.getPhone(), availableMember.getEmail());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
