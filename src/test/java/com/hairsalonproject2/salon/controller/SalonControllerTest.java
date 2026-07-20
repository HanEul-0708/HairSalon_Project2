package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.integration.kakao.KakaoAddressSearchResult;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.response.SalonBranchMarkerResponse;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.util.LinkedMultiValueMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalonControllerTest {

 @Mock
 private SalonQueryService salonQueryService;

 @Mock
 private ExternalSalonSyncService externalSalonSyncService;

 @Mock
 private KakaoLocalSearchClient kakaoLocalSearchClient;

 @InjectMocks
 private SalonController salonController;

 @Test
 void listRedirectsLegacyBranchesViewToCanonicalSalonPage() {
  SalonSearchRequest request = new SalonSearchRequest();
  request.setKeyword("cut");
  LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
  params.add("view", "branches");
  params.add("keyword", "cut");
  ConcurrentModel model = new ConcurrentModel();
  String viewName = salonController.list("branches", null, null, params, request, model);
  assertThat(viewName).isEqualTo("redirect:/salons?keyword=cut");
 }

 @Test
 void listPopulatesUnifiedSalonPageWhenKakaoRestKeyMissing() {
  SalonSearchRequest request = new SalonSearchRequest();
  request.setKeyword("cut");
  ConcurrentModel model = new ConcurrentModel();
  when(salonQueryService.search(request)).thenReturn(List.of(SalonSummaryResponse.builder().salonId(1).name("테스트 살롱").address("서울 강남").reservable(true).build()));
  when(kakaoLocalSearchClient.isConfigured()).thenReturn(false);
  String viewName = salonController.list(null, null, null, new LinkedMultiValueMap<>(), request, model);
  assertThat(viewName).isEqualTo("salon/list");
  assertThat(model.getAttribute("pageTitle")).isEqualTo("살롱 통합 탐색");
  assertThat(model.getAttribute("branchView")).isEqualTo(true);
  assertThat(model.getAttribute("primaryKeywordPlaceholder")).isEqualTo("이름, 주소, 설명");
  assertThat(model.getAttribute("branchMapStatus")).isEqualTo("카카오 REST API 키가 설정되지 않아 지도를 표시할 수 없습니다.");
  assertThat(model.getAttribute("kakaoResultsMessage")).isEqualTo("카카오 API 키가 설정되지 않아 외부 검색을 수행할 수 없습니다.");
  verify(salonQueryService).search(request);
 }

 @Test
 void listBuildsBranchMarkersFromAddressSearch() {
  SalonSearchRequest request = new SalonSearchRequest();
  ConcurrentModel model = new ConcurrentModel();
  SalonSummaryResponse summary = SalonSummaryResponse.builder().salonId(7).name("강남 테스트 살롱").address("서울 강남구 테헤란로 123").roadAddress("서울 강남구 테헤란로 123").phone("02-123-4567").reservable(true).build();
  when(salonQueryService.search(request)).thenReturn(List.of(summary));
  when(kakaoLocalSearchClient.isConfigured()).thenReturn(true);
  when(kakaoLocalSearchClient.searchAddress("서울 강남구 테헤란로 123")).thenReturn(Optional.of(KakaoAddressSearchResult.builder().longitude(new BigDecimal("127.031393491745")).latitude(new BigDecimal("37.4995539438207")).build()));
  salonController.list(null, null, null, new LinkedMultiValueMap<>(), request, model);
  @SuppressWarnings("unchecked") List<SalonBranchMarkerResponse> markers = (List<SalonBranchMarkerResponse>) model.getAttribute("branchMarkers");
  assertThat(markers).hasSize(1);
  assertThat(markers.get(0).getSalonId()).isEqualTo(7);
  assertThat(markers.get(0).getDetailUrl()).isEqualTo("/salons/7");
  assertThat(model.getAttribute("branchMapStatus")).isEqualTo("지점 주소를 기준으로 지도에 위치를 표시합니다.");
 }

 @Test
 void syncFromKakaoRequiresAdminLogin() {
  String viewName = salonController.syncFromKakao("cut", "gangnam", null, null);
  assertThat(viewName).isEqualTo("redirect:/members/login");
 }
}
