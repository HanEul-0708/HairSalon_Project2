package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.common.integration.kakao.KakaoAddressSearchResult;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.common.util.AddressRegionUtils;
import com.hairsalonproject2.salon.dto.response.SalonBranchMarkerResponse;
import com.hairsalonproject2.salon.dto.response.SalonMapResultsPayload;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalonMapService {

    private final ExternalSalonSyncService externalSalonSyncService;
    private final SalonRepository salonRepository;
    private final KakaoLocalSearchClient kakaoLocalSearchClient;

    @Value("${kakao.rest-api-key:${KAKAO_REST_API_KEY:}}")
    private String kakaoRestApiKey;

    public SalonMapResultsPayload getMapResults(String keyword,
                                                String region,
                                                String city,
                                                String district,
                                                String neighborhood) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String safeRegion = resolveRegion(region, city, district, neighborhood);
        String queryUsed = buildSalonQuery(safeKeyword, safeRegion);

        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "카카오 REST API 키가 비어 있습니다. kakao.rest-api-key 또는 KAKAO_REST_API_KEY 설정을 확인하세요."
            );
        }

        List<Integer> savedIds;
        try {
            savedIds = externalSalonSyncService.syncFromKakao(safeKeyword, safeRegion);
        } catch (RuntimeException ex) {
            log.warn("mapResults failed. keyword='{}', region='{}'", safeKeyword, safeRegion, ex);
            return SalonMapResultsPayload.builder()
                    .results(List.of())
                    .debug("카카오 검색어: '" + queryUsed + "'")
                    .error(toSafeErrorMessage(ex))
                    .build();
        }

        List<SalonBranchMarkerResponse> results = savedIds.stream()
                .map(salonRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(this::toBranchMarkerResponse)
                .toList();

        return SalonMapResultsPayload.builder()
                .results(results)
                .debug("카카오 검색어: '" + queryUsed + "', 결과 " + results.size() + "건")
                .build();
    }

    private String resolveRegion(String region, String city, String district, String neighborhood) {
        String safeRegion = AddressRegionUtils.combine(city, district, neighborhood);
        if (safeRegion.isBlank()) {
            return region == null ? "" : region.trim();
        }
        return safeRegion;
    }

    private String buildSalonQuery(String keyword, String region) {
        if (keyword.isBlank() && region.isBlank()) {
            return "";
        }
        if (keyword.isBlank()) {
            return region + " 미용실";
        }
        if (region.isBlank()) {
            return keyword + " 미용실";
        }
        return region + " " + keyword + " 미용실";
    }

    private String toSafeErrorMessage(RuntimeException ex) {
        String safeMessage = (ex.getMessage() == null ? "" : ex.getMessage())
                .replaceAll("\\r?\\n", " ")
                .trim();
        if (safeMessage.length() > 120) {
            safeMessage = safeMessage.substring(0, 120) + "...";
        }
        return safeMessage.isBlank() ? "카카오 REST API 검색 실패" : safeMessage;
    }

    private Optional<KakaoAddressSearchResult> resolveSalonCoordinate(Salon salon) {
        if (salon == null) {
            return Optional.empty();
        }

        // DB에 이미 좌표가 있으면 그대로 사용
        if (salon.getLatitude() != null && salon.getLongitude() != null) {
            return Optional.of(
                    KakaoAddressSearchResult.builder()
                            .addressName(salon.getAddress())
                            .roadAddressName(salon.getRoadAddress())
                            .latitude(salon.getLatitude())
                            .longitude(salon.getLongitude())
                            .build()
            );
        }

        // 도로명 주소 우선 검색
        Optional<KakaoAddressSearchResult> roadAddressResult =
                kakaoLocalSearchClient.searchAddress(salon.getRoadAddress());

        if (roadAddressResult.isPresent()) {
            return roadAddressResult;
        }

        // 지번 주소 fallback
        return kakaoLocalSearchClient.searchAddress(salon.getAddress());
    }

    private SalonBranchMarkerResponse toBranchMarkerResponse(Salon salon) {
        boolean external = salon.getExternalId() != null && !salon.getExternalId().isBlank();
        Optional<KakaoAddressSearchResult> resolvedCoordinate = resolveSalonCoordinate(salon);

        return SalonBranchMarkerResponse.builder()
                .markerKey("salon-" + salon.getSalonId())
                .salonId(salon.getSalonId())
                .name(salon.getName())
                .address(salon.getAddress())
                .roadAddress(salon.getRoadAddress())
                .phone(salon.getPhone())
                .detailUrl("/salons/" + salon.getSalonId())
                .external(external)
                .externalLabel(external ? resolveExternalLabel(salon.getSourceType()) : null)
                .latitude(
                        resolvedCoordinate.map(KakaoAddressSearchResult::getLatitude)
                                .orElse(salon.getLatitude())
                )
                .longitude(
                        resolvedCoordinate.map(KakaoAddressSearchResult::getLongitude)
                                .orElse(salon.getLongitude())
                )
                .build();
    }

    private String resolveExternalLabel(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "외부";
        }
        if ("KAKAO".equalsIgnoreCase(sourceType)) {
            return "카카오";
        }
        return sourceType.trim();
    }
}
