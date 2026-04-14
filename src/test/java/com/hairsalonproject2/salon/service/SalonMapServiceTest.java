package com.hairsalonproject2.salon.service;

import com.hairsalonproject2.salon.dto.response.SalonMapResultsPayload;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salon.repository.SalonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonMapServiceTest {

    @Mock
    private ExternalSalonSyncService externalSalonSyncService;

    @Mock
    private SalonRepository salonRepository;

    @InjectMocks
    private SalonMapService salonMapService;

    @Test
    void getMapResultsReturnsBranchMarkerPayload() {
        ReflectionTestUtils.setField(salonMapService, "kakaoRestApiKey", "test-rest-key");
        Salon salon = Salon.builder()
                .salonId(1)
                .externalId("kakao-1")
                .sourceType("KAKAO")
                .name("준오헤어")
                .address("서울 강남구 역삼동 1")
                .roadAddress("서울 강남구 테헤란로 1")
                .phone("02-1234-5678")
                .latitude(new BigDecimal("37.1234567"))
                .longitude(new BigDecimal("127.1234567"))
                .build();

        when(externalSalonSyncService.syncFromKakao("준오헤어", "서울특별시 강남구")).thenReturn(List.of(1));
        when(salonRepository.findById(1)).thenReturn(Optional.of(salon));

        SalonMapResultsPayload payload =
                salonMapService.getMapResults("준오헤어", null, "서울특별시", "강남구", null);

        assertThat(payload.getResults()).hasSize(1);
        assertThat(payload.getResults().get(0).getMarkerKey()).isEqualTo("salon-1");
        assertThat(payload.getResults().get(0).getDetailUrl()).isEqualTo("/salons/1");
        assertThat(payload.getResults().get(0).getPhone()).isEqualTo("02-1234-5678");
        assertThat(payload.getResults().get(0).isExternal()).isTrue();
        assertThat(payload.getResults().get(0).getExternalLabel()).isEqualTo("카카오");
        assertThat(payload.getDebug()).isEqualTo("카카오 검색어: '서울특별시 강남구 준오헤어 미용실', 결과 1건");
    }

    @Test
    void getMapResultsReturnsEmptyPayloadWhenKakaoSyncFails() {
        ReflectionTestUtils.setField(salonMapService, "kakaoRestApiKey", "test-rest-key");
        when(externalSalonSyncService.syncFromKakao("준오헤어", "성수"))
                .thenThrow(new IllegalStateException("remote\nfailure"));

        SalonMapResultsPayload payload = salonMapService.getMapResults("준오헤어", "성수", null, null, null);

        assertThat(payload.getResults()).isEmpty();
        assertThat(payload.getDebug()).isEqualTo("카카오 검색어: '성수 준오헤어 미용실'");
        assertThat(payload.getError()).isEqualTo("remote failure");
    }
}
