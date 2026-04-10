package com.hairsalonproject2.common.integration.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLocalSearchClientTest {

    private final KakaoLocalSearchClient client = new KakaoLocalSearchClient(new ObjectMapper());

    @Test
    void buildSalonQuerySupportsRegionOnlySearch() {
        assertThat(client.buildSalonQuery("", "\uBD80\uC0B0\uC2DC \uD574\uC6B4\uB300\uAD6C"))
                .isEqualTo("\uBD80\uC0B0\uC2DC \uD574\uC6B4\uB300\uAD6C \uBBF8\uC6A9\uC2E4");
    }

    @Test
    void buildSalonQuerySupportsKeywordOnlySearch() {
        assertThat(client.buildSalonQuery("\uC900\uC624\uD5E4\uC5B4", ""))
                .isEqualTo("\uC900\uC624\uD5E4\uC5B4 \uBBF8\uC6A9\uC2E4");
    }

    @Test
    void buildSalonQueryCombinesRegionAndKeyword() {
        assertThat(client.buildSalonQuery("\uC900\uC624\uD5E4\uC5B4", "\uC11C\uC6B8"))
                .isEqualTo("\uC11C\uC6B8 \uC900\uC624\uD5E4\uC5B4 \uBBF8\uC6A9\uC2E4");
    }

    @Test
    void buildImageQueryPrefersPlaceName() {
        assertThat(client.buildImageQuery("\uC900\uC624\uD5E4\uC5B4 \uAC15\uB0A8\uC5ED\uC810", "\uC11C\uC6B8 \uAC15\uB0A8\uAD6C", "\uAC15\uB0A8\uB300\uB85C"))
                .isEqualTo("\uC900\uC624\uD5E4\uC5B4 \uAC15\uB0A8\uC5ED\uC810 \uBBF8\uC6A9\uC2E4");
    }

    @Test
    void buildImageQueryFallsBackToRoadAddress() {
        assertThat(client.buildImageQuery("", "\uC11C\uC6B8 \uAC15\uB0A8\uAD6C", "\uAC15\uB0A8\uB300\uB85C"))
                .isEqualTo("\uAC15\uB0A8\uB300\uB85C \uBBF8\uC6A9\uC2E4");
    }

    @Test
    void buildPlaceDedupKeyUsesPlaceNameFirst() {
        KakaoPlaceSearchResult first = KakaoPlaceSearchResult.builder()
                .externalId("1")
                .placeName("\uC900\uC624\uBBF8\uC6A9\uC2E4")
                .addressName("\uC11C\uC6B8 \uC6A9\uC0B0\uAD6C")
                .build();
        KakaoPlaceSearchResult second = KakaoPlaceSearchResult.builder()
                .externalId("2")
                .placeName("\uC900\uC624\uBBF8\uC6A9\uC2E4")
                .addressName("\uC804\uBD81 \uC815\uC74D\uC2DC")
                .build();

        assertThat(client.buildPlaceDedupKey(first))
                .isEqualTo(client.buildPlaceDedupKey(second));
    }
}
