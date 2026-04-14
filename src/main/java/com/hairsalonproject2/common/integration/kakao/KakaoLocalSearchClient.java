package com.hairsalonproject2.common.integration.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class KakaoLocalSearchClient {
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();
    private final Map<String, Optional<KakaoAddressSearchResult>> addressSearchCache = new ConcurrentHashMap<>();

    @Value("${kakao.rest-api-key:}")
    private String restApiKey;

    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    public List<KakaoPlaceSearchResult> searchSalons(String keyword, String region, int page, int size) {
        if (!isConfigured()) {
            return List.of();
        }

        String query = buildSalonQuery(keyword, region);
        if (query.isBlank()) {
            return List.of();
        }

        String uri = UriComponentsBuilder
                .fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                .queryParam("query", query)
                .queryParam("page", page)
                .queryParam("size", size)
                // query 값(한글 포함)을 제대로 percent-encoding 해야 합니다.
                // build(true)는 "이미 인코딩된 URI"로 취급해서 한글이 그대로 들어갈 수 있습니다.
                .build(false)
                .toUriString();

        return parsePlaceSearchResults(requestKakao(uri));
    }

    public Optional<KakaoAddressSearchResult> searchAddress(String address) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String normalizedAddress = normalize(address);
        if (normalizedAddress.isBlank()) {
            return Optional.empty();
        }

        return addressSearchCache.computeIfAbsent(normalizedAddress, this::requestAddressSafely);
    }

    public List<KakaoAddressSearchResult> searchAddresses(String query, int page, int size) {
        if (restApiKey == null || restApiKey.isBlank()) {
            return List.of();
        }

        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        String uri = UriComponentsBuilder
                .fromUriString("https://dapi.kakao.com/v2/local/search/address.json")
                .queryParam("query", normalizedQuery)
                .queryParam("page", page)
                .queryParam("size", size)
                .build(false)
                .toUriString();

        return parseAddressSearchResults(requestKakao(uri));
    }

    List<KakaoPlaceSearchResult> parsePlaceSearchResults(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<KakaoPlaceSearchResult> results = new ArrayList<>();

            for (JsonNode node : root.path("documents")) {
                KakaoAddressSearchResult address = toAddressSearchResult(node);
                results.add(KakaoPlaceSearchResult.builder()
                        .externalId(node.path("id").asText())
                        .placeName(node.path("place_name").asText())
                        .addressName(address.getAddressName())
                        .roadAddressName(address.getRoadAddressName())
                        .phone(node.path("phone").asText())
                        .placeUrl(node.path("place_url").asText())
                        .longitude(defaultZero(address.getLongitude()))
                        .latitude(defaultZero(address.getLatitude()))
                        .build());
            }

            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Kakao API response", e);
        }
    }

    List<KakaoAddressSearchResult> parseAddressSearchResults(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            List<KakaoAddressSearchResult> results = new ArrayList<>();

            for (JsonNode node : root.path("documents")) {
                results.add(toAddressSearchResult(node));
            }

            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Kakao API response", e);
        }
    }

    String buildSalonQuery(String keyword, String region) {
        String normalizedKeyword = normalize(keyword);
        String normalizedRegion = normalize(region);

        if (normalizedKeyword.isBlank() && normalizedRegion.isBlank()) {
            return "";
        }

        if (normalizedKeyword.isBlank()) {
            return normalizedRegion + " 미용실";
        }

        if (normalizedRegion.isBlank()) {
            return normalizedKeyword + " 미용실";
        }

        return normalizedRegion + " " + normalizedKeyword + " 미용실";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 좌표 문자열을 BigDecimal로 안전하게 변환한다.
     */
    private BigDecimal parseCoordinate(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return null;
        }
        return new BigDecimal(normalizedValue);
    }

    /**
     * 주소 검색 예외를 바깥으로 던지지 않고 빈 결과로 처리한다.
     */
    private Optional<KakaoAddressSearchResult> requestAddressSafely(String address) {
        try {
            return requestAddress(address);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * 카카오 주소 검색 API를 호출해서 첫 번째 주소 결과의 좌표를 가져온다.
     */
    private Optional<KakaoAddressSearchResult> requestAddress(String address) throws Exception {
        String uri = UriComponentsBuilder
                .fromUriString("https://dapi.kakao.com/v2/local/search/address.json")
                .queryParam("query", address)
                .build(false)
                .toUriString();

        String body = restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(body);
        JsonNode first = root.path("documents").path(0);

        if (first.isMissingNode() || first.isNull()) {
            return Optional.empty();
        }

        BigDecimal longitude = parseCoordinate(first.path("x").asText(""));
        BigDecimal latitude = parseCoordinate(first.path("y").asText(""));

        if (longitude == null || latitude == null) {
            return Optional.empty();
        }

        return Optional.of(
                KakaoAddressSearchResult.builder()
                        .addressName(first.path("address_name").asText(""))
                        .roadAddressName(first.path("road_address").path("address_name").asText(""))
                        .longitude(longitude)
                        .latitude(latitude)
                        .build()
        );
    }

    private String requestKakao(String uri) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to request Kakao API", e);
        }
    }

    private KakaoAddressSearchResult toAddressSearchResult(JsonNode node) {
        return KakaoAddressSearchResult.builder()
                .addressName(node.path("address_name").asText())
                .roadAddressName(resolveRoadAddressName(node))
                .longitude(toBigDecimal(node.path("x").asText()))
                .latitude(toBigDecimal(node.path("y").asText()))
                .build();
    }

    private String resolveRoadAddressName(JsonNode node) {
        String roadAddressName = node.path("road_address_name").asText();
        if (!roadAddressName.isBlank()) {
            return roadAddressName;
        }
        return node.path("road_address").path("address_name").asText();
    }

    private BigDecimal toBigDecimal(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
