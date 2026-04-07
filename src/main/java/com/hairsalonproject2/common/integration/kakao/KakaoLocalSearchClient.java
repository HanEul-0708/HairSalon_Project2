package com.hairsalonproject2.common.integration.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KakaoLocalSearchClient {

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${kakao.rest-api-key:}")
    private String restApiKey;

    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }

    public List<KakaoPlaceSearchResult> searchSalons(String keyword, String region, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        if (!isConfigured()) {
            return List.of();
        }

        String query = (region == null || region.isBlank() ? keyword : region + " " + keyword) + " 미용실";

        try {
            String uri = UriComponentsBuilder.fromUriString("https://dapi.kakao.com/v2/local/search/keyword.json")
                    .queryParam("query", query)
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .build(true)
                    .toUriString();

            String body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

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
                        .longitude(new BigDecimal(node.path("x").asText("0")))
                        .latitude(new BigDecimal(node.path("y").asText("0")))
                        .build());
            }

            return results;
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to call Kakao API", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Kakao API response", e);
        }
    }
}
