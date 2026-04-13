package com.hairsalonproject2.common.integration.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLocalSearchClientTest {

    private final KakaoLocalSearchClient client = new KakaoLocalSearchClient(new ObjectMapper());

    @Test
    void buildSalonQuerySupportsRegionOnlySearch() {
        assertThat(client.buildSalonQuery("", "부산시 수영구"))
                .isEqualTo("부산시 수영구 미용실");
    }

    @Test
    void buildSalonQuerySupportsKeywordOnlySearch() {
        assertThat(client.buildSalonQuery("준오헤어", ""))
                .isEqualTo("준오헤어 미용실");
    }

    @Test
    void buildSalonQueryCombinesRegionAndKeyword() {
        assertThat(client.buildSalonQuery("준오헤어", "잠실"))
                .isEqualTo("잠실 준오헤어 미용실");
    }

    @Test
    void parseAddressSearchResultsMapsAddressFields() {
        String body = """
                {
                  "documents": [
                    {
                      "address_name": "서울 강남구 역삼동 1",
                      "road_address": {
                        "address_name": "서울 강남구 테헤란로 1"
                      },
                      "x": "127.1234567",
                      "y": "37.1234567"
                    }
                  ]
                }
                """;

        List<KakaoAddressSearchResult> results = client.parseAddressSearchResults(body);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAddressName()).isEqualTo("서울 강남구 역삼동 1");
        assertThat(results.get(0).getRoadAddressName()).isEqualTo("서울 강남구 테헤란로 1");
        assertThat(results.get(0).getLongitude()).isEqualByComparingTo(new BigDecimal("127.1234567"));
        assertThat(results.get(0).getLatitude()).isEqualByComparingTo(new BigDecimal("37.1234567"));
    }

    @Test
    void parseAddressSearchResultsKeepsInvalidCoordinatesNull() {
        String body = """
                {
                  "documents": [
                    {
                      "address_name": "서울 강남구 역삼동 1",
                      "road_address_name": "서울 강남구 테헤란로 1",
                      "x": "",
                      "y": "not-a-number"
                    }
                  ]
                }
                """;

        List<KakaoAddressSearchResult> results = client.parseAddressSearchResults(body);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLongitude()).isNull();
        assertThat(results.get(0).getLatitude()).isNull();
    }

    @Test
    void parsePlaceSearchResultsDefaultsInvalidCoordinatesToZero() {
        String body = """
                {
                  "documents": [
                    {
                      "id": "1",
                      "place_name": "준오헤어",
                      "address_name": "서울 강남구 역삼동 1",
                      "road_address_name": "서울 강남구 테헤란로 1",
                      "phone": "02-1234-5678",
                      "place_url": "https://place.map.kakao.com/1",
                      "x": "",
                      "y": "not-a-number"
                    }
                  ]
                }
                """;

        List<KakaoPlaceSearchResult> results = client.parsePlaceSearchResults(body);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getLongitude()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(results.get(0).getLatitude()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
