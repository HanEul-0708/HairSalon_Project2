package com.hairsalonproject2.common.integration.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}
