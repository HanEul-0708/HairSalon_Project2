package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.response.SalonMapResultsPayload;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonMapService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ConcurrentModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonControllerTest {

    @Mock
    private SalonQueryService salonQueryService;

    @Mock
    private ExternalSalonSyncService externalSalonSyncService;

    @Mock
    private SalonMapService salonMapService;

    @InjectMocks
    private SalonController salonController;

    @Test
    void listSearchesStoredSalonsWithoutKakaoSync() {
        SalonSearchRequest request = new SalonSearchRequest();
        request.setSearched(true);
        request.setKeyword("준오헤어");
        request.setRegion("성수");
        ConcurrentModel model = new ConcurrentModel();

        when(salonQueryService.search(request, 0, 9)).thenReturn(new PageImpl<>(List.of()));
        when(salonQueryService.getCityOptions()).thenReturn(List.of());
        when(salonQueryService.getDistrictOptions(any())).thenReturn(List.of());
        when(salonQueryService.getNeighborhoodOptions(any(), any())).thenReturn(List.of());
        when(salonQueryService.getAddressOptions()).thenReturn(List.of());

        String viewName = salonController.list(request, 1, model);

        assertThat(viewName).isEqualTo("salon/list");
        verify(externalSalonSyncService, never()).syncFromSearch(any(), any());
        verify(salonQueryService).search(request, 0, 9);
    }

    @Test
    void listDoesNotSyncWhenUserHasNotSearchedYet() {
        SalonSearchRequest request = new SalonSearchRequest();
        ConcurrentModel model = new ConcurrentModel();

        when(salonQueryService.getCityOptions()).thenReturn(List.of());
        when(salonQueryService.getDistrictOptions(any())).thenReturn(List.of());
        when(salonQueryService.getNeighborhoodOptions(any(), any())).thenReturn(List.of());
        when(salonQueryService.getAddressOptions()).thenReturn(List.of());

        String viewName = salonController.list(request, 1, model);

        assertThat(viewName).isEqualTo("salon/list");
        verify(externalSalonSyncService, never()).syncFromSearch(any(), any());
        verify(salonQueryService, never()).search(any(SalonSearchRequest.class), eq(0), eq(9));
    }

    @Test
    void mapSearchExposesJavascriptKeyToTemplate() {
        ReflectionTestUtils.setField(salonController, "kakaoJavascriptKey", "test-js-key");
        ConcurrentModel model = new ConcurrentModel();

        when(salonQueryService.getCityOptions()).thenReturn(List.of("서울특별시"));
        when(salonQueryService.getDistrictOptions(any())).thenReturn(List.of("강남구"));
        when(salonQueryService.getNeighborhoodOptions(any(), any())).thenReturn(List.of("역삼동"));
        when(salonQueryService.getAddressOptions()).thenReturn(List.of("서울특별시 강남구 역삼동"));

        String viewName = salonController.mapSearch("준오헤어", "서울특별시 강남구 역삼동", null, null, null, model);

        assertThat(viewName).isEqualTo("salon/map-search");
        assertThat(model.getAttribute("keyword")).isEqualTo("준오헤어");
        assertThat(model.getAttribute("city")).isEqualTo("서울특별시");
        assertThat(model.getAttribute("district")).isEqualTo("강남구");
        assertThat(model.getAttribute("neighborhood")).isEqualTo("역삼동");
        assertThat(model.getAttribute("kakaoJavascriptKey")).isEqualTo("test-js-key");
    }

    @Test
    void mapResultsDelegatesToMapService() {
        SalonMapResultsPayload payload = SalonMapResultsPayload.builder()
                .results(List.of())
                .debug("카카오 검색어: '서울특별시 강남구 준오헤어 미용실', 결과 0건")
                .build();
        when(salonMapService.getMapResults("준오헤어", null, "서울특별시", "강남구", null))
                .thenReturn(payload);

        ResponseEntity<SalonMapResultsPayload> response =
                salonController.mapResults("준오헤어", null, "서울특별시", "강남구", null);

        assertThat(response.getBody()).isSameAs(payload);
        verify(salonMapService).getMapResults("준오헤어", null, "서울특별시", "강남구", null);
    }
}
