package com.hairsalonproject2.common.integration.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KakaoLocalSearchClient {
 private static final String SALON_KEYWORD = "\uBBF8\uC6A9\uC2E4";
 private static final String PLACE_PANEL_ENDPOINT = "https://place-api.map.kakao.com/places/panel3/";
 private static final String PLACE_REFERER = "https://place.map.kakao.com/";
 private static final String PLACE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

 private final ObjectMapper objectMapper;
 private final RestClient restClient;
 private final String apiBaseUrl;
 private final String placePanelEndpoint;
 private final Map<String, Optional<KakaoAddressSearchResult>> addressSearchCache = new ConcurrentHashMap<>();
 private final Map<String, Optional<String>> imageSearchCache = new ConcurrentHashMap<>();
 private final Map<String, Optional<KakaoPlaceRating>> placeRatingCache = new ConcurrentHashMap<>();

 @Value("${kakao.rest-api-key:}")
 private String restApiKey;

 @Value("${kakao.javascript-key:}")
 private String javascriptKey;

 public KakaoLocalSearchClient(ObjectMapper objectMapper) {
  this(objectMapper, 2000, 5000, "https://dapi.kakao.com", PLACE_PANEL_ENDPOINT);
 }

 @Autowired
 public KakaoLocalSearchClient(ObjectMapper objectMapper,
         @Value("${kakao.http.connect-timeout-ms:2000}") int connectTimeoutMs,
         @Value("${kakao.http.read-timeout-ms:5000}") int readTimeoutMs,
         @Value("${kakao.http.api-base-url:https://dapi.kakao.com}") String apiBaseUrl,
         @Value("${kakao.http.place-panel-endpoint:https://place-api.map.kakao.com/places/panel3/}") String placePanelEndpoint) {
  if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) throw new IllegalArgumentException("Kakao HTTP timeouts must be positive");
  this.objectMapper = objectMapper;
  this.apiBaseUrl = apiBaseUrl;
  this.placePanelEndpoint = placePanelEndpoint;
  SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(connectTimeoutMs);
  factory.setReadTimeout(readTimeoutMs);
  this.restClient = RestClient.builder().requestFactory(factory).build();
 }

 public boolean isConfigured() {
  return restApiKey != null && !restApiKey.isBlank();
 }

 public boolean isJavascriptConfigured() {
  return javascriptKey != null && !javascriptKey.isBlank();
 }

 public List<KakaoPlaceSearchResult> searchSalons(String keyword, String region, int page, int size) {
  if (!isConfigured()) return List.of(); String query = buildSalonQuery(keyword, region);
  if (query.isBlank()) return List.of(); String uri = UriComponentsBuilder.fromUriString(apiBaseUrl + "/v2/local/search/keyword.json").queryParam("query", query).queryParam("page", page).queryParam("size", size).build(false).toUriString();
  String body = restClient.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
  try { JsonNode root = objectMapper.readTree(body);
   Map<String, KakaoPlaceSearchResult> deduplicatedResults = new LinkedHashMap<>();
   for (JsonNode node : root.path("documents")) {
    String externalId = normalize(node.path("id").asText());
    if (externalId.isBlank() || deduplicatedResults.containsKey("external:" + externalId)) continue;
	KakaoPlaceSearchResult place = KakaoPlaceSearchResult.builder().externalId(normalize(node.path("id").asText())).placeName(normalize(node.path("place_name").asText())).addressName(normalize(node.path("address_name").asText())).roadAddressName(normalize(node.path("road_address_name").asText())).phone(normalize(node.path("phone").asText())).placeUrl(normalize(node.path("place_url").asText())).thumbnailUrl(searchImageThumbnail(node.path("place_name").asText(), node.path("address_name").asText(), node.path("road_address_name").asText()).orElse(null)).longitude(parseCoordinate(node.path("x").asText())).latitude(parseCoordinate(node.path("y").asText())).build();
	deduplicatedResults.putIfAbsent(buildPlaceDedupKey(place), place);
   } List<KakaoPlaceSearchResult> results = new ArrayList<>();
   for (KakaoPlaceSearchResult place : deduplicatedResults.values()) results.add(withPlaceRating(place));
   return results;
  } catch (Exception e) { throw new IllegalStateException("Failed to parse Kakao API response", e);
  }//외부에서 호출하는 메인 검색 메소드입니다. API 키 설정 여부를 검증한 후, 입력된 키워드와 지역 정보를 조합하여 API 요청 URI를 생성합니다. RestClient를 통해 https://dapi.kakao.com/v2/local/search/keyword.json에 GET 요청을 전송합니다. 반환된 JSON 데이터에서 미용실 정보를 추출하여 KakaoPlaceSearchResult 객체로 변환하며, 자체 기준(buildPlaceDedupKey)을 통해 중복된 미용실 데이터를 제거한 후 리스트로 반환합니다.
 }

 private String normalize(String value) { return value == null ? "" : value.trim(); }

 String buildSalonQuery(String keyword, String region) {
  String normalizedKeyword = normalize(keyword); String normalizedRegion = normalize(region);
  if (normalizedKeyword.isBlank() && normalizedRegion.isBlank()) return "";
  if (normalizedKeyword.isBlank()) return appendSalonKeyword(normalizedRegion);
  if (normalizedRegion.isBlank()) return appendSalonKeyword(normalizedKeyword);
  return appendSalonKeyword(normalizedRegion + " " + normalizedKeyword);
 }//검색어(keyword)와 지역(region) 문자열을 정제(공백 제거 등)한 후, 하나의 검색 쿼리 스트링으로 결합합니다. 두 값이 모두 없으면 빈 문자열을 반환합니다.

 private String appendSalonKeyword(String value) {
  String normalizedValue = normalize(value); if (normalizedValue.isBlank()) return "";
  return normalizedValue.contains(SALON_KEYWORD) ? normalizedValue : normalizedValue + " " + SALON_KEYWORD;//검색 정확도를 높이기 위해 문자열에 '미용실'이라는 단어가 포함되어 있지 않은 경우, 접미사로 '미용실'을 자동으로 추가합니다.
 }

 String buildPlaceDedupKey(KakaoPlaceSearchResult place) {
  return "external:" + normalize(place.getExternalId());
 }

 public Optional<KakaoAddressSearchResult> searchAddress(String address) {
  if (!isConfigured()) return Optional.empty(); String normalizedAddress = normalize(address);
  if (normalizedAddress.isBlank()) return Optional.empty();
  return cachedResult(addressSearchCache, normalizedAddress, this::requestAddress);
 }//외부에서 주소 조회를 위해 호출하는 메소드입니다. API 설정 및 입력값 검증을 거친 후, ConcurrentHashMap 구조인 addressSearchCache를 조회하여 이미 검색한 이력이 있다면 캐싱된 결과를 반환하고, 없다면 실제 API 요청을 수행합니다.


 private Optional<KakaoAddressSearchResult> requestAddress(String address) throws Exception {
  String uri = UriComponentsBuilder.fromUriString(apiBaseUrl + "/v2/local/search/address.json").queryParam("query", address).build(false).toUriString();
  String body = restClient.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
  JsonNode root = objectMapper.readTree(body);
  JsonNode first = root.path("documents").path(0);
  if (first.isMissingNode() || first.isNull()) return Optional.empty();
  String longitude = first.path("x").asText("");
  String latitude = first.path("y").asText("");
  if (longitude.isBlank() || latitude.isBlank()) return Optional.empty();
  return Optional.of(KakaoAddressSearchResult.builder().addressName(first.path("address_name").asText("")).roadAddressName(first.path("road_address").path("address_name").asText("")).longitude(new BigDecimal(longitude)).latitude(new BigDecimal(latitude)).build());
 }//https://dapi.kakao.com/v2/local/search/address.json 엔드포인트로 실제 HTTP 요청을 보냅니다. 응답 데이터 중 첫 번째 문서(documents[0])의 지번 주소, 도로명 주소, X/Y 좌표를 추출하여 KakaoAddressSearchResult 객체를 생성합니다.

 private BigDecimal parseCoordinate(String value) { String normalizedValue = normalize(value);
  if (normalizedValue.isBlank()) return null; return new BigDecimal(normalizedValue);
 }//카카오 API가 문자열(String)로 반환하는 좌표 값을 전달받아 앞뒤 공백을 제거한 후, 데이터 누락이나 오차가 없는 BigDecimal 타입으로 변환하여 반환합니다. 빈 문자열이 입력되면 null을 반환합니다.

 private KakaoPlaceSearchResult withPlaceRating(KakaoPlaceSearchResult place) {
  if (place == null) return null; KakaoPlaceRating rating = searchPlaceRating(place.getExternalId()).orElse(null);
  if (rating == null) return place; return place.toBuilder().averageRating(rating.averageRating()).reviewCount(rating.reviewCount()).build();
 }//키워드 검색으로 얻은 개별 미용실 결과에 카카오 장소 상세 평점을 조회하여 병합합니다. 평점을 확보하지 못하면 원본 결과를 그대로 반환합니다.

 public Optional<KakaoPlaceRating> searchPlaceRating(String placeId) {
  String normalizedId = normalize(placeId); if (normalizedId.isBlank()) return Optional.empty();
  return cachedResult(placeRatingCache, normalizedId, this::requestPlaceRating);
 }//카카오 장소 식별자(placeId)로 평점을 조회하는 진입점입니다. placeRatingCache를 활용하여 동일 장소에 대한 반복 요청을 캐싱하고, 식별자가 비어 있으면 즉시 빈 값을 반환합니다.


 private Optional<KakaoPlaceRating> requestPlaceRating(String placeId) throws Exception {
  String body = restClient.get().uri(placePanelEndpoint + placeId).header(HttpHeaders.ACCEPT, "application/json, text/plain, */*").header(HttpHeaders.USER_AGENT, PLACE_USER_AGENT).header(HttpHeaders.REFERER, PLACE_REFERER).header("pf", "web").retrieve().body(String.class);
  JsonNode scoreSet = objectMapper.readTree(body).path("kakaomap_review").path("score_set");
  if (scoreSet.isMissingNode() || scoreSet.isNull()) return Optional.empty();
  int reviewCount = scoreSet.path("review_count").asInt(0); if (reviewCount <= 0) return Optional.empty();
  JsonNode averageNode = scoreSet.path("average_score"); String averageText = normalize(averageNode.asText());
  if (averageNode.isMissingNode() || averageNode.isNull() || averageText.isBlank()) return Optional.empty();
  BigDecimal averageRating = new BigDecimal(averageText).setScale(1, RoundingMode.HALF_UP);
  return Optional.of(new KakaoPlaceRating(averageRating, reviewCount));
 }//카카오 장소 상세 비공식 엔드포인트(place-api.map.kakao.com/places/panel3/{id})에 pf:web 헤더로 요청하여 kakaomap_review.score_set의 평균 평점과 리뷰 수를 추출합니다. 리뷰가 없거나 평점이 없으면 빈 값을 반환합니다.

 private Optional<String> searchImageThumbnail(String placeName, String addressName, String roadAddressName) { if (!isConfigured()) return Optional.empty();
  String query = buildImageQuery(placeName, addressName, roadAddressName);
  if (query.isBlank()) return Optional.empty();
  return cachedResult(imageSearchCache, query, this::requestImageThumbnail);
 }//미용실 이름과 주소 정보를 기반으로 이미지 검색을 요청하는 메서드입니다. imageSearchCache를 확인하여 기존에 검색된 결과가 있으면 즉시 반환하고, 없으면 API 요청을 진행합니다.

 String buildImageQuery(String placeName, String addressName, String roadAddressName) {
  String normalizedPlaceName = normalize(placeName);
  if (!normalizedPlaceName.isBlank()) return appendSalonKeyword(normalizedPlaceName);
  String normalizedRoadAddress = normalize(roadAddressName);
  if (!normalizedRoadAddress.isBlank()) return appendSalonKeyword(normalizedRoadAddress);
  String normalizedAddress = normalize(addressName);
  if (!normalizedAddress.isBlank()) return appendSalonKeyword(normalizedAddress); return "";
 }//이미지 검색에 최적화된 쿼리를 생성합니다. 미용실 이름, 도로명 주소, 지번 주소 순으로 유효성을 확인하여 가장 적합한 대상을 선택한 뒤 appendSalonKeyword를 통해 '미용실' 키워드와 결합합니다.


 private Optional<String> requestImageThumbnail(String query) throws Exception {
  String uri = UriComponentsBuilder.fromUriString(apiBaseUrl + "/v2/search/image").queryParam("query", query).queryParam("page", 1).queryParam("size", 1).queryParam("sort", "accuracy").build(false).toUriString();
  String body = restClient.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey).accept(MediaType.APPLICATION_JSON).retrieve().body(String.class);
  JsonNode root = objectMapper.readTree(body); JsonNode first = root.path("documents").path(0); if (first.isMissingNode() || first.isNull()) return Optional.empty();
  String thumbnailUrl = first.path("thumbnail_url").asText("");
  if (!thumbnailUrl.isBlank()) return Optional.of(thumbnailUrl);
  String imageUrl = first.path("image_url").asText("");
  return imageUrl.isBlank() ? Optional.empty() : Optional.of(imageUrl);
 }//https://dapi.kakao.com/v2/search/image 엔드포인트에 검색 정확도(accuracy) 순으로 설정하여 상위 1개의 이미지를 요청합니다. 결과 데이터가 존재할 경우 일차적으로 thumbnail_url을 추출하며, 해당 값이 비어있다면 원본 이미지 주소인 image_url을 대안으로 선택하여 반환합니다.
 private <T> Optional<T> cachedResult(Map<String, Optional<T>> cache, String key, ExternalLookup<T> lookup) {
  try {
   return cache.computeIfAbsent(key, value -> {
    try { return lookup.fetch(value); }
    catch (Exception ex) { throw new LookupFailure(ex); }
   });
  } catch (LookupFailure ex) {
   return Optional.empty();
  }
 }

 @FunctionalInterface
 private interface ExternalLookup<T> {
  Optional<T> fetch(String value) throws Exception;
 }

 private static class LookupFailure extends RuntimeException {
  private LookupFailure(Exception cause) { super(cause); }
 }

}
