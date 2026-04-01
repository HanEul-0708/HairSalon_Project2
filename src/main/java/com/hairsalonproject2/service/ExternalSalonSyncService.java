package com.hairsalonproject2.service;

import com.hairsalonproject2.entity.Salon;
import com.hairsalonproject2.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.repository.SalonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ExternalSalonSyncService {
    private final KakaoLocalSearchClient kakaoLocalSearchClient;
    private final SalonRepository salonRepository;

    public List<Integer> syncFromKakao(String keyword, String region) {
        List<KakaoPlaceSearchResult> results = kakaoLocalSearchClient.searchSalons(keyword, region, 1, 15);
        List<Integer> savedIds = new ArrayList<>();

        for (KakaoPlaceSearchResult place : results) {
            Salon salon = salonRepository.findByExternalId(place.getExternalId())
                    .orElseGet(Salon::new);

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
}
