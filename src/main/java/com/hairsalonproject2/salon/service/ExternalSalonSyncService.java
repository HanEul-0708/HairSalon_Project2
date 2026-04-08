package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.common.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExternalSalonSyncService {
    private final KakaoLocalSearchClient kakaoLocalSearchClient;
    private final SalonRepository salonRepository;

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

            salon.setExternalId(place.getExternalId());
            salon.setSourceType("KAKAO");
            salon.setName(place.getPlaceName());
            salon.setAddress(place.getAddressName());
            salon.setRoadAddress(place.getRoadAddressName());
            salon.setPhone(place.getPhone());
            salon.setLatitude(place.getLatitude());
            salon.setLongitude(place.getLongitude());
            salon.setPlaceUrl(place.getPlaceUrl());
            if (salon.getReservable() == null) {
                salon.setReservable(Boolean.FALSE);
            }
            savedIds.add(salonRepository.save(salon).getSalonId());
        }
        return savedIds;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
