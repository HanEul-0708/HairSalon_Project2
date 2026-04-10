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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class KakaoLocalSearchClient {
    private static final String SALON_KEYWORD = "\uBBF8\uC6A9\uC2E4";

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();
    private final Map<String, Optional<KakaoAddressSearchResult>> addressSearchCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> imageSearchCache = new ConcurrentHashMap<>();

    @Value("${kakao.rest-api-key:}")
    private String restApiKey;

    @Value("${kakao.javascript-key:}")
    private String javascriptKey;

    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    public boolean isJavascriptConfigured() {
        return javascriptKey != null && !javascriptKey.isBlank();
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
                .build(false)
                .toUriString();

        String body = restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            Map<String, KakaoPlaceSearchResult> deduplicatedResults = new LinkedHashMap<>();

            for (JsonNode node : root.path("documents")) {
                KakaoPlaceSearchResult place = KakaoPlaceSearchResult.builder()
                        .externalId(normalize(node.path("id").asText()))
                        .placeName(normalize(node.path("place_name").asText()))
                        .addressName(normalize(node.path("address_name").asText()))
                        .roadAddressName(normalize(node.path("road_address_name").asText()))
                        .phone(normalize(node.path("phone").asText()))
                        .placeUrl(normalize(node.path("place_url").asText()))
                        .thumbnailUrl(searchImageThumbnail(
                                node.path("place_name").asText(),
                                node.path("address_name").asText(),
                                node.path("road_address_name").asText()
                        ).orElse(null))
                        .longitude(parseCoordinate(node.path("x").asText()))
                        .latitude(parseCoordinate(node.path("y").asText()))
                        .build();

                deduplicatedResults.putIfAbsent(buildPlaceDedupKey(place), place);
            }

            return new ArrayList<>(deduplicatedResults.values());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Kakao API response", e);
        }
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

    String buildSalonQuery(String keyword, String region) {
        String normalizedKeyword = normalize(keyword);
        String normalizedRegion = normalize(region);

        if (normalizedKeyword.isBlank() && normalizedRegion.isBlank()) {
            return "";
        }

        if (normalizedKeyword.isBlank()) {
            return appendSalonKeyword(normalizedRegion);
        }

        if (normalizedRegion.isBlank()) {
            return appendSalonKeyword(normalizedKeyword);
        }

        return appendSalonKeyword(normalizedRegion + " " + normalizedKeyword);
    }

    String buildImageQuery(String placeName, String addressName, String roadAddressName) {
        String normalizedPlaceName = normalize(placeName);
        if (!normalizedPlaceName.isBlank()) {
            return appendSalonKeyword(normalizedPlaceName);
        }

        String normalizedRoadAddress = normalize(roadAddressName);
        if (!normalizedRoadAddress.isBlank()) {
            return appendSalonKeyword(normalizedRoadAddress);
        }

        String normalizedAddress = normalize(addressName);
        if (!normalizedAddress.isBlank()) {
            return appendSalonKeyword(normalizedAddress);
        }

        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private BigDecimal parseCoordinate(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return null;
        }
        return new BigDecimal(normalizedValue);
    }

    String buildPlaceDedupKey(KakaoPlaceSearchResult place) {
        String normalizedPlaceName = canonicalizeDedupText(place.getPlaceName());
        if (!normalizedPlaceName.isBlank()) {
            return "name:" + normalizedPlaceName;
        }

        String roadAddress = canonicalizeDedupText(place.getRoadAddressName());
        if (!roadAddress.isBlank()) {
            return "road:" + roadAddress;
        }

        String address = canonicalizeDedupText(place.getAddressName());
        if (!address.isBlank()) {
            return "address:" + address;
        }

        return "external:" + normalize(place.getExternalId());
    }

    private String canonicalizeDedupText(String value) {
        String normalizedValue = normalize(value).toLowerCase();
        if (normalizedValue.isBlank()) {
            return "";
        }

        return normalizedValue.replaceAll("[\\s\\-_.(),/·ㆍ]+", "");
    }

    private String appendSalonKeyword(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return "";
        }

        return normalizedValue.contains(SALON_KEYWORD)
                ? normalizedValue
                : normalizedValue + " " + SALON_KEYWORD;
    }

    private Optional<String> searchImageThumbnail(String placeName, String addressName, String roadAddressName) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String query = buildImageQuery(placeName, addressName, roadAddressName);
        if (query.isBlank()) {
            return Optional.empty();
        }

        return imageSearchCache.computeIfAbsent(query, this::requestImageThumbnailSafely);
    }

    private Optional<KakaoAddressSearchResult> requestAddressSafely(String address) {
        try {
            return requestAddress(address);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> requestImageThumbnailSafely(String query) {
        try {
            return requestImageThumbnail(query);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

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

        String longitude = first.path("x").asText("");
        String latitude = first.path("y").asText("");
        if (longitude.isBlank() || latitude.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(KakaoAddressSearchResult.builder()
                .addressName(first.path("address_name").asText(""))
                .roadAddressName(first.path("road_address").path("address_name").asText(""))
                .longitude(new BigDecimal(longitude))
                .latitude(new BigDecimal(latitude))
                .build());
    }

    private Optional<String> requestImageThumbnail(String query) throws Exception {
        String uri = UriComponentsBuilder
                .fromUriString("https://dapi.kakao.com/v2/search/image")
                .queryParam("query", query)
                .queryParam("page", 1)
                .queryParam("size", 1)
                .queryParam("sort", "accuracy")
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

        String thumbnailUrl = first.path("thumbnail_url").asText("");
        if (!thumbnailUrl.isBlank()) {
            return Optional.of(thumbnailUrl);
        }

        String imageUrl = first.path("image_url").asText("");
        return imageUrl.isBlank() ? Optional.empty() : Optional.of(imageUrl);
    }
}
