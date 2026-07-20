package com.hairsalonproject2.salon.controller;

import com.hairsalonproject2.common.integration.kakao.KakaoAddressSearchResult;
import com.hairsalonproject2.common.integration.kakao.KakaoLocalSearchClient;
import com.hairsalonproject2.common.integration.kakao.KakaoPlaceSearchResult;
import com.hairsalonproject2.salon.dto.request.SalonCreateRequest;
import com.hairsalonproject2.salon.dto.request.SalonSearchRequest;
import com.hairsalonproject2.salon.dto.request.SalonUpdateRequest;
import com.hairsalonproject2.salon.dto.response.SalonBranchMarkerResponse;
import com.hairsalonproject2.salon.dto.response.SalonSummaryResponse;
import com.hairsalonproject2.salon.service.ExternalSalonSyncService;
import com.hairsalonproject2.salon.service.SalonQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/salons")
public class SalonController {
 private static final String SALON_LIST_VIEW = "salon/list";

 private final SalonQueryService salonQueryService;
 private final ExternalSalonSyncService externalSalonSyncService;
 private final KakaoLocalSearchClient kakaoLocalSearchClient;

 @Value("${kakao.javascript-key:}")
 private String kakaoJavascriptKey;

 @GetMapping
 public String list(@RequestParam(required = false) String view, @RequestParam(required = false) String preset, @RequestParam(required = false) String styleKeyword, @RequestParam MultiValueMap<String, String> params, @ModelAttribute("search") SalonSearchRequest request, Model model) {//살롱 검색 요청을 받아 지정된 프리셋 필터를 적용하고, 결과 목록, 지도 마커, 카카오 비교 데이터를 통합하여 목록 화면을 반환합니다.
  if (view != null && !view.isBlank()) {
   LinkedMultiValueMap<String, String> merged = copyParams(params);
   merged.remove("view");
   return redirectTo("/salons", merged);
  } StandardPreset resolvedPreset = StandardPreset.from(preset);
  applyStandardPreset(request, styleKeyword, resolvedPreset);
  populateListPage(model, request, createStandardSpec(resolvedPreset));
  populateKakaoComparison(model, request);
  return SALON_LIST_VIEW;
 }

 @GetMapping("/search")//검색 요청 파라미터를 유지한 채 기본 목록 주소(/salons)로 리다이렉트합니다.
 public String searchRedirect(@RequestParam MultiValueMap<String, String> params) {
  return redirectTo("/salons", params);
 }

 @GetMapping("/branches")//지점 뷰 요청을 처리하기 위해 파라미터를 정리하고 목록 주소로 리다이렉트합니다.
 public String branchesRedirect(@RequestParam MultiValueMap<String, String> params) {
  LinkedMultiValueMap<String, String> merged = copyParams(params);
  merged.remove("view");
  return redirectTo("/salons", merged);
 }

 @GetMapping("/top-rated")
 public String topRatedRedirect(@RequestParam MultiValueMap<String, String> params) {
  LinkedMultiValueMap<String, String> merged = copyParams(params);
  merged.set("preset", StandardPreset.TOP_RATED.value); merged.remove("view");
  setIfBlank(merged, "minRating", "4.0");
  setIfBlank(merged, "sort", "rating");
  return redirectTo("/salons", merged);
 }//평점 4.0 이상 및 평점 순 정렬 프리셋 조건을 강제 설정하여 목록 주소로 리다이렉트합니다.

 @GetMapping("/recommend-by-service")
 public String recommendByServiceRedirect(@RequestParam MultiValueMap<String, String> params) {
  LinkedMultiValueMap<String, String> merged = copyParams(params);
  String styleKeyword = merged.getFirst("styleKeyword");
  if ((merged.getFirst("keyword") == null || merged.getFirst("keyword").isBlank()) && styleKeyword != null && !styleKeyword.isBlank()) merged.set("keyword", styleKeyword);
  merged.remove("styleKeyword");
  merged.remove("view");//스타일 키워드 기반 추천 프리셋 조건을 바인딩하여 목록 주소로 리다이렉트합니다.
  merged.set("preset", StandardPreset.RECOMMEND_BY_SERVICE.value);
  setIfBlank(merged, "sort", "rating");
  return redirectTo("/salons", merged);
 }

 @GetMapping("/kakao-search")//외부 검색 요청 파라미터를 포함하여 목록 주소로 리다이렉트합니다.
 public String kakaoSearchRedirect(@RequestParam MultiValueMap<String, String> params) {
  LinkedMultiValueMap<String, String> merged = copyParams(params);
  merged.remove("view");
  return redirectTo("/salons", merged);
 }

 private void normalizeSearch(SalonSearchRequest request) {
  if (request.getSort() == null || request.getSort().isBlank()) request.setSort("recommended");
 }//검색 정렬 조건이 누락된 경우 기본값(recommended)을 부여합니다.

 private boolean hasMinRatingSliderValue(SalonSearchRequest request) {
  return request != null && request.getMinRating() != null && request.getMinRating().compareTo(BigDecimal.ZERO) > 0;//검색 조건 중 최소 평점 필터의 유효 설정 여부를 판별합니다.
 }

 private String getMinRatingSliderValue(SalonSearchRequest request) {
  if (!hasMinRatingSliderValue(request)) return "0";//입력된 최소 평점 값을 가공하여 문자열로 추출합니다.
  return request.getMinRating().stripTrailingZeros().toPlainString();
 }

 private String getMinRatingSliderLabel(SalonSearchRequest request) {
  if (!hasMinRatingSliderValue(request)) return "전체";
  return getMinRatingSliderValue(request) + "점";//화면 슬라이더에 표시할 평점 텍스트 라벨을 생성합니다.
 }

 private void applyStandardPreset(SalonSearchRequest request, String styleKeyword, StandardPreset preset) {
  request.setServiceKeywordSearchEnabled(preset == StandardPreset.RECOMMEND_BY_SERVICE);
  if (preset.minRating != null && request.getMinRating() == null) request.setMinRating(preset.minRating); if (preset == StandardPreset.RECOMMEND_BY_SERVICE && (request.getKeyword() == null || request.getKeyword().isBlank()) && styleKeyword != null && !styleKeyword.isBlank())
   request.setKeyword(styleKeyword); if (preset.sort != null && (request.getSort() == null || request.getSort().isBlank())) request.setSort(preset.sort);
 }//선택된 StandardPreset 종류에 맞추어 평점 조건, 스타일 키워드 및 정렬 기준을 검색 요청 객체에 주입합니다.

 private SalonListPageSpec createStandardSpec(StandardPreset preset) { return switch (preset) {
  case TOP_RATED ->
		  new SalonListPageSpec("살롱 통합 탐색", "목록, 지점 지도, 카카오 외부 검색을 한 화면에서 비교하면서 평점 4점 이상 조건을 적용합니다.", "평점 4점 이상 프리셋", "높은 평점의 내부 살롱과 외부 검색 결과를 같은 화면에서 바로 비교할 수 있습니다.", "/salons", true, "이름, 주소, 설명", "필터 적용", true, true, "평점 4점 이상 조건에 맞는 미용실이 없습니다.", preset.value);
  case TOP_RATED_45 ->
		  new SalonListPageSpec("살롱 통합 탐색", "목록, 지점 지도, 카카오 외부 검색을 한 화면에서 비교하면서 평점 4.5점 이상 조건을 적용합니다.", "평점 4.5점 이상 프리셋", "더 높은 평점의 내부 살롱과 외부 검색 결과를 같은 화면에서 바로 비교할 수 있습니다.", "/salons", true, "이름, 주소, 설명", "필터 적용", true, true, "평점 4.5점 이상 조건에 맞는 미용실이 없습니다.", preset.value);
  case RECOMMEND_BY_SERVICE ->
		  new SalonListPageSpec("살롱 통합 탐색", "목록, 지점 지도, 카카오 외부 검색을 한 화면에서 비교하면서 스타일 키워드 추천을 적용합니다.", "스타일 키워드 추천", "시술명 또는 스타일 키워드로 내부 살롱과 외부 검색 결과를 함께 비교합니다.", "/salons", true, "스타일 또는 시술 키워드", "추천 검색", true, true, "입력한 스타일 키워드에 맞는 미용실이 없습니다.", preset.value);
  case ALL ->
		  new SalonListPageSpec("살롱 통합 탐색", "내부 살롱 목록, 지점 안내 지도, 카카오 외부 검색 결과를 한 페이지에서 확인합니다.", null, null, "/salons", true, "이름, 주소, 설명", "통합 검색", true, true, "조건에 맞는 미용실이 없습니다.", preset.value);
 };//프리셋 모드별 화면 타이틀, 가이드 문구, 버튼 라벨 등의 레이아웃 사양 객체(SalonListPageSpec)를 동적으로 생성합니다.
}

 @GetMapping("/{salonId:\\d+}")//특정 살롱 고유 식별자(salonId)를 기준으로 상세 정보를 조회하고, 로그인한 회원의 해당 살롱 좋아요 등록 여부를 판별하여 상세 화면을 반환합니다.
 public String detail(@PathVariable Integer salonId, Authentication authentication, Model model) {
  model.addAttribute("salon", salonQueryService.getDetail(salonId));
  model.addAttribute("likedByCurrentUser", isAuthenticated(authentication) && salonQueryService.isLikedByMember(salonId, authentication.getName()));
  return "salon/detail";
 }

 @GetMapping("/likes")//현재 인증된 회원이 좋아요를 부여한 살롱 목록을 일괄 조회하여 전용 화면으로 전달합니다.
 public String likedSalons(Authentication authentication, Model model) {
  if (!isAuthenticated(authentication)) return "redirect:/members/login";
  model.addAttribute("salons", salonQueryService.getLikedSalons(authentication.getName())); return "salon/liked-list";
 }

 @PostMapping("/{salonId:\\d+}/likes")
 public String like(@PathVariable Integer salonId, Authentication authentication, RedirectAttributes redirectAttributes) { if (!isAuthenticated(authentication)) {
  redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
  return "redirect:/members/login";
 } boolean created = salonQueryService.like(salonId, authentication.getName());
  redirectAttributes.addFlashAttribute("message", created ? "좋아요가 반영되었습니다." : "이미 좋아요한 미용실입니다."); return "redirect:/salons/" + salonId;
 }//특정 살롱에 대한 좋아요 등록 요청을 처리하고 결과를 플래시 속성에 담아 리다이렉트합니다.

 @DeleteMapping("/{salonId:\\d+}/likes")
 public String unlike(@PathVariable Integer salonId, Authentication authentication, RedirectAttributes redirectAttributes) { if (!isAuthenticated(authentication)) {
  redirectAttributes.addFlashAttribute("message", "로그인이 필요합니다.");
  return "redirect:/members/login";
 } boolean deleted = salonQueryService.unlike(salonId, authentication.getName());
  redirectAttributes.addFlashAttribute("message", deleted ? "좋아요가 취소되었습니다." : "좋아요 정보가 없습니다."); return "redirect:/salons/" + salonId;
 }//특정 살롱에 등록된 좋아요 취소 요청을 처리하고 결과를 플래시 속성에 담아 리다이렉트합니다.

 @PostMapping("/sync/kakao")
 public String syncFromKakao(@RequestParam String keyword, @RequestParam(required = false) String region, Authentication authentication, RedirectAttributes redirectAttributes) {
  String guard = requireAdmin(authentication); if (guard != null) return guard;
  if (keyword == null || keyword.isBlank()) {
   redirectAttributes.addFlashAttribute("message", "동기화할 검색어를 입력해 주세요."); return buildRedirectToSalonList(keyword, region);
  } if (!kakaoLocalSearchClient.isConfigured()) {
   redirectAttributes.addFlashAttribute("message", "카카오 API 키가 설정되지 않아 동기화를 수행할 수 없습니다."); return buildRedirectToSalonList(keyword, region);
  } try { int count = externalSalonSyncService.syncFromKakao(keyword, region).size();
   redirectAttributes.addFlashAttribute("message", count == 0 ? "카카오 검색 결과가 없어 동기화한 미용실이 없습니다." : count + "건의 미용실 데이터를 동기화했습니다.");
  } catch (IllegalStateException ex) { redirectAttributes.addFlashAttribute("message", "Kakao API 연동에 실패했습니다.");
  } return buildRedirectToSalonList(keyword, region);
 }//관리자 권한을 검증한 뒤, 입력된 키워드와 지역을 바탕으로 카카오 외부 API를 호출하여 살롱 데이터를 로컬 데이터베이스에 수집 및 동기화합니다.

 @GetMapping("/price-compare")
 public String priceCompareRedirect(@RequestParam(required = false) String serviceName, @RequestParam(required = false) String region) {
  UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/salon-services/compare");
  if (serviceName != null && !serviceName.isBlank()) builder.queryParam("serviceName", serviceName);
  if (region != null && !region.isBlank()) builder.queryParam("region", region);
  return "redirect:" + builder.toUriString();
 }//시술명과 지역 정보를 매개변수로 삼아 시술 가격 비교 컨트롤러 주소(/salon-services/compare)로 리다이렉트 경로를 생성합니다.

 private void populateListPage(Model model, SalonSearchRequest request, SalonListPageSpec spec) {
  normalizeSearch(request); List<SalonSummaryResponse> salons = salonQueryService.search(request);
  model.addAttribute("salons", salons); populateBranchMarkers(model, salons);
  model.addAttribute("search", request);
  model.addAttribute("pageTitle", spec.pageTitle());
  model.addAttribute("pageDescription", spec.pageDescription());
  model.addAttribute("presetTitle", spec.presetTitle());
  model.addAttribute("presetDescription", spec.presetDescription());
  model.addAttribute("searchAction", spec.searchAction());
  model.addAttribute("showPrimarySearch", spec.showPrimarySearch());
  model.addAttribute("primaryKeywordPlaceholder", spec.primaryKeywordPlaceholder());
  model.addAttribute("primarySubmitLabel", spec.primarySubmitLabel());
  model.addAttribute("branchView", spec.branchView());
  model.addAttribute("showKakaoSyncAction", spec.showKakaoSyncAction());
  model.addAttribute("emptyMessage", spec.emptyMessage());
  model.addAttribute("activePreset", spec.activePreset());
  model.addAttribute("minRatingSliderActive", hasMinRatingSliderValue(request));
  model.addAttribute("minRatingSliderValue", getMinRatingSliderValue(request));
  model.addAttribute("minRatingSliderLabel", getMinRatingSliderLabel(request));
  model.addAttribute("kakaoResults", List.of());
  model.addAttribute("kakaoResultsMessage", null);
  model.addAttribute("kakaoJavascriptKey", kakaoJavascriptKey);
  model.addAttribute("kakaoMapConfigured", kakaoLocalSearchClient.isJavascriptConfigured());
 }//내부 검색 수행, 지도 마커 바인딩, 프리셋 사양 설정 및 카카오 API 호출 결과를 종합하여 목록 제어용 Model 객체에 적재합니다.

 private void populateBranchMarkers(Model model, List<SalonSummaryResponse> salons) {
  if (salons == null || salons.isEmpty()) {
   model.addAttribute("branchMarkers", List.of());
   model.addAttribute("branchMapStatus", "표시할 지점 정보가 없습니다.");
   return;
  } if (!kakaoLocalSearchClient.isConfigured()) {
   model.addAttribute("branchMarkers", List.of());
   model.addAttribute("branchMapStatus", "카카오 REST API 키가 설정되지 않아 지도를 표시할 수 없습니다."); return;
  } List<SalonBranchMarkerResponse> markers = new ArrayList<>();
  int unresolvedCount = 0; for (SalonSummaryResponse salon : salons) {
   Optional<SalonBranchMarkerResponse> marker = createBranchMarker(salon);
   if (marker.isPresent()) markers.add(marker.get()); else unresolvedCount += 1;
  } model.addAttribute("branchMarkers", markers); if (markers.isEmpty()) {
   model.addAttribute("branchMapStatus", "좌표를 찾을 수 있는 지점이 없어 지도를 표시할 수 없습니다."); return;
  } model.addAttribute("branchMapStatus", unresolvedCount > 0 ? "일부 지점은 좌표를 찾지 못해 지도에서 제외했습니다." : "지점 주소를 기준으로 지도에 위치를 표시합니다.");
 }//검색된 내부 살롱 엔티티 목록의 주소를 기반으로 지도에 렌더링할 마커 객체 리스트를 구성하고 지도 표시 상태 문구를 결정합니다.

 private Optional<SalonBranchMarkerResponse> createBranchMarker(SalonSummaryResponse salon) {
  if (salon == null || salon.getSalonId() == null) return Optional.empty();
  Optional<KakaoAddressSearchResult> coordinate = resolveBranchCoordinate(salon);
  if (coordinate.isEmpty()) return Optional.empty();
  KakaoAddressSearchResult result = coordinate.get();
  return Optional.of(SalonBranchMarkerResponse.builder().markerKey("internal-" + salon.getSalonId()).salonId(salon.getSalonId()).name(salon.getName()).address(salon.getAddress()).roadAddress(salon.getRoadAddress()).phone(salon.getPhone()).detailUrl("/salons/" + salon.getSalonId()).external(false).longitude(result.getLongitude()).latitude(result.getLatitude()).build());
 }//개별 내부 살롱 데이터를 지도 마커 표현 규격 응답 DTO로 매핑합니다.

 private Optional<KakaoAddressSearchResult> resolveBranchCoordinate(SalonSummaryResponse salon) {
  Optional<KakaoAddressSearchResult> roadAddressResult = kakaoLocalSearchClient.searchAddress(salon.getRoadAddress()); if (roadAddressResult.isPresent()) return roadAddressResult;
  return kakaoLocalSearchClient.searchAddress(salon.getAddress());
 }//카카오 주소 검색 클라이언트를 호출하여 도로명 주소 또는 지번 주소의 위경도 좌표를 확보합니다.

 private void populateKakaoComparison(Model model, SalonSearchRequest request) {
  if (request.getKeyword() == null || request.getKeyword().isBlank()) {
   model.addAttribute("kakaoResultsMessage", "검색어를 입력하면 내부 살롱과 카카오 외부 결과를 함께 비교할 수 있습니다."); return; } if (!kakaoLocalSearchClient.isConfigured()) {
   model.addAttribute("kakaoResultsMessage", "카카오 API 키가 설정되지 않아 외부 검색을 수행할 수 없습니다."); return;
  } List<KakaoPlaceSearchResult> kakaoResults = kakaoLocalSearchClient.searchSalons(request.getKeyword(), request.getRegion(), 1, 15);
  List<KakaoPlaceSearchResult> ratedResults = filterByMinRating(kakaoResults, request.getMinRating());
  model.addAttribute("kakaoResults", ratedResults);
  if (ratedResults.isEmpty() && hasMinRatingSliderValue(request) && kakaoResults != null && !kakaoResults.isEmpty())
   model.addAttribute("kakaoResultsMessage", "평점 " + getMinRatingSliderValue(request) + "점 이상 조건을 만족하는 카카오 외부 결과가 없습니다.");
  mergeExternalBranchMarkers(model, ratedResults);
 }//사용자가 입력한 검색어가 존재할 시 카카오 장소 검색 API를 연동하여 실시간 외부 살롱 검색 결과를 모델에 추가합니다. 카카오 상세 평점을 기준으로 최소 평점 조건을 외부 결과에도 동일하게 적용합니다.

 private List<KakaoPlaceSearchResult> filterByMinRating(List<KakaoPlaceSearchResult> results, BigDecimal minRating) {
  if (results == null || results.isEmpty()) return List.of();
  if (minRating == null) return results;
  return results.stream().filter(place -> place.getAverageRating() != null && place.getAverageRating().compareTo(minRating) >= 0).toList();
 }//최소 평점 조건이 없으면 카카오 외부 결과를 그대로 두고, 조건이 있으면 카카오 상세 평점이 기준 이상인 결과만 남깁니다. 평점을 확인하지 못한 결과는 조건 활성화 시 제외합니다.

 private void mergeExternalBranchMarkers(Model model, List<KakaoPlaceSearchResult> kakaoResults) {
  if (kakaoResults == null || kakaoResults.isEmpty()) return;
  List<SalonBranchMarkerResponse> mergedMarkers = new ArrayList<>();
  Object currentMarkers = model.asMap().get("branchMarkers");
  if (currentMarkers instanceof List<?> markerList) for (Object marker : markerList)
   if (marker instanceof SalonBranchMarkerResponse branchMarker) mergedMarkers.add(branchMarker);
  int unresolvedCount = 0; for (KakaoPlaceSearchResult place : kakaoResults) {
   Optional<SalonBranchMarkerResponse> externalMarker = createExternalBranchMarker(place);
   if (externalMarker.isPresent()) mergedMarkers.add(externalMarker.get());
   else unresolvedCount += 1; } if (mergedMarkers.isEmpty()) return;
  model.addAttribute("branchMarkers", mergedMarkers);
  boolean hasInternalMarker = mergedMarkers.stream().anyMatch(marker -> !marker.isExternal());
  boolean hasExternalMarker = mergedMarkers.stream().anyMatch(SalonBranchMarkerResponse::isExternal); if (hasInternalMarker && hasExternalMarker) {
   model.addAttribute("branchMapStatus", unresolvedCount > 0 ? "내부 살롱과 카카오 외부 결과를 지도에 표시하되, 일부 외부 결과는 좌표 정보가 없어 제외되었습니다." : "내부 살롱과 카카오 외부 결과를 지도에 표시합니다."); return;
  } if (hasExternalMarker) model.addAttribute("branchMapStatus", unresolvedCount > 0 ? "카카오 외부 결과를 지도에 표시하되, 일부 결과는 좌표 정보가 없어 제외되었습니다." : "카카오 외부 결과를 지도에 표시합니다.");
 }//기존 내부 살롱 마커 목록에 카카오 외부 검색 결과 마커들을 통합하고, 좌표 획득 실패 건수를 계산하여 최종 지도 상태 문구를 동적으로 조합합니다.

 private Optional<SalonBranchMarkerResponse> createExternalBranchMarker(KakaoPlaceSearchResult place) {//카카오 API로 수신한 외부 미용실 위치 정보를 기반으로 외부 전용 지도 마커 규격 객체를 생성합니다.
  if (place == null || place.getExternalId() == null || place.getExternalId().isBlank() || place.getLatitude() == null || place.getLongitude() == null) return Optional.empty();
  return Optional.of(SalonBranchMarkerResponse.builder().markerKey("kakao-" + place.getExternalId()).name(place.getPlaceName()).address(place.getAddressName()).roadAddress(place.getRoadAddressName()).phone(place.getPhone()).detailUrl(place.getPlaceUrl()).external(true).externalLabel("KAKAO").longitude(place.getLongitude()).latitude(place.getLatitude()).build());
 }

 @GetMapping("/new")
 public String createForm(Authentication authentication, Model model) {
  String guard = requireAdmin(authentication);
  if (guard != null) return guard;
  model.addAttribute("form", new SalonCreateRequest());
  return "salon/form";
 }//HTTP 매핑 및 경로: /salons/new 경로로 들어오는 GET 요청을 처리합니다. 관리자 권한 검증: requireAdmin(authentication) 메서드를 호출하여 요청을 보낸 사용자가 관리자 권한(ROLE_ADMIN)을 보유했는지 확인합니다. 권한이 없거나 비인증 상태인 경우 권한 제어 리다이렉트 경로를 반환하여 접근을 차단합니다. 폼 객체 생성 및 바인딩: 권한 검증을 통과하면 새로운 미용실 등록 요청 생성을 위한 빈 SalonCreateRequest 객체를 뷰 계층으로 전달하기 위해 Model 객체에 "form"이라는 이름의 속성으로 추가합니다. 화면 반환: 미용실 등록 양식 폼을 렌더링하는 뷰 템플릿 파일 경로인 "salon/form"을 반환합니다.

 @PostMapping
 public String create(@ModelAttribute("form") SalonCreateRequest request, BindingResult bindingResult, Authentication authentication) { String guard = requireAdmin(authentication);
  if (guard != null) return guard; if (bindingResult.hasErrors()) return "salon/form";
  Integer salonId = salonQueryService.create(request); return "redirect:/salons/" + salonId;
 }

 @GetMapping("/{salonId:\\d+}/edit")
 public String editForm(@PathVariable Integer salonId, Authentication authentication, Model model) { String guard = requireAdmin(authentication); if (guard != null) return guard;
  var detail = salonQueryService.getDetail(salonId);
  SalonUpdateRequest form = new SalonUpdateRequest(); form.setName(detail.getName());
  form.setAddress(detail.getAddress()); form.setRoadAddress(detail.getRoadAddress());
  form.setPhone(detail.getPhone()); form.setDescription(detail.getDescription());
  form.setImageUrl(detail.getImageUrl()); form.setPlaceUrl(detail.getPlaceUrl());
  form.setReservable(detail.getReservable()); model.addAttribute("salonId", salonId);
  model.addAttribute("form", form); return "salon/form";
 }

 @PostMapping("/{salonId:\\d+}/edit")
 public String update(@PathVariable Integer salonId, @ModelAttribute("form") SalonUpdateRequest request, BindingResult bindingResult, Authentication authentication, Model model) {
  String guard = requireAdmin(authentication); if (guard != null) return guard;
  if (bindingResult.hasErrors()) { model.addAttribute("salonId", salonId);
   return "salon/form";
  } salonQueryService.update(salonId, request);
  return "redirect:/salons/" + salonId;
 }

 @DeleteMapping("/{salonId:\\d+}")
 public String delete(@PathVariable Integer salonId, Authentication authentication) {
  String guard = requireAdmin(authentication); if (guard != null) return guard;
  salonQueryService.delete(salonId); return "redirect:/salons";
 }

 private LinkedMultiValueMap<String, String> copyParams(MultiValueMap<String, String> params) {
  LinkedMultiValueMap<String, String> copied = new LinkedMultiValueMap<>();
  params.forEach((key, values) -> copied.put(key, values == null ? List.of() : List.copyOf(values))); return copied;
 }

 private void setIfBlank(LinkedMultiValueMap<String, String> params, String key, String value) {
  String current = params.getFirst(key); if (current == null || current.isBlank()) params.set(key, value);
 }

 private String redirectTo(String path, MultiValueMap<String, String> params) {
  UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
  params.forEach((key, values) -> { if (values == null || values.isEmpty()) return; values.stream().filter(value -> value != null && !value.isBlank()).forEach(value -> builder.queryParam(key, value));
  }); return "redirect:" + builder.build().encode().toUriString();
 }

 private String buildRedirectToSalonList(String keyword, String region) {
  LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
  if (keyword != null && !keyword.isBlank()) params.add("keyword", keyword);
  if (region != null && !region.isBlank()) params.add("region", region);
  return redirectTo("/salons", params);
 }

 private String requireAdmin(Authentication authentication) {
  if (!isAuthenticated(authentication)) return "redirect:/members/login";
  boolean isAdmin = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals); return isAdmin ? null : "redirect:/access-denied";
 }

 private boolean isAuthenticated(Authentication authentication) {
  return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
 }

 private enum StandardPreset {
  ALL("all", null, null), TOP_RATED("top-rated", "rating", BigDecimal.valueOf(4.0)), TOP_RATED_45("top-rated-4-5", "rating", BigDecimal.valueOf(4.5)), RECOMMEND_BY_SERVICE("recommend-by-service", "rating", null);
  private final String value; private final String sort; private final BigDecimal minRating;
  StandardPreset(String value, String sort, BigDecimal minRating) {
   this.value = value; this.sort = sort; this.minRating = minRating;
  }
  private static StandardPreset from(String value) {
   if (value == null || value.isBlank()) return ALL; for (StandardPreset preset : values())
	if (preset.value.equals(value)) return preset; return ALL;
  }
 }

 private record SalonListPageSpec(String pageTitle, String pageDescription, String presetTitle, String presetDescription, String searchAction, boolean showPrimarySearch, String primaryKeywordPlaceholder, String primarySubmitLabel, boolean branchView, boolean showKakaoSyncAction, String emptyMessage, String activePreset) {
 }
}
