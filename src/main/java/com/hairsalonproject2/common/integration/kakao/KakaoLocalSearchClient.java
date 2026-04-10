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
            List<KakaoPlaceSearchResult> results = new ArrayList<>();

            for (JsonNode node : root.path("documents")) {
                results.add(KakaoPlaceSearchResult.builder()
                        .externalId(node.path("id").asText())
                        .placeName(node.path("place_name").asText())
                        .addressName(node.path("address_name").asText())
                        .roadAddressName(node.path("road_address_name").asText())
                        .phone(node.path("phone").asText())
                        .placeUrl(node.path("place_url").asText())
                        .build());
            }

            return results;
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

    private Optional<KakaoAddressSearchResult> requestAddressSafely(String address) {
        try {
            return requestAddress(address);
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
}
