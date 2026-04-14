package com.hairsalonproject2.common.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchTermsTest {

    @Test
    void parseSplitsRegionTokensAndKeyword() {
        GlobalSearchTerms terms = GlobalSearchTerms.parse("부산 수영구 펌");

        assertThat(terms.city()).isEqualTo("부산");
        assertThat(terms.district()).isEqualTo("수영구");
        assertThat(terms.neighborhood()).isNull();
        assertThat(terms.keyword()).isEqualTo("펌");
        assertThat(terms.boardKeyword("부산 수영구 펌")).isEqualTo("펌");
    }

    @Test
    void parseKeepsFallbackForRegionOnlySearch() {
        GlobalSearchTerms terms = GlobalSearchTerms.parse("부산 수영구");

        assertThat(terms.city()).isEqualTo("부산");
        assertThat(terms.district()).isEqualTo("수영구");
        assertThat(terms.keyword()).isNull();
        assertThat(terms.boardKeyword("부산 수영구")).isEqualTo("부산 수영구");
    }

    @Test
    void parseRecognizesNeighborhoodAfterRegionPrefix() {
        GlobalSearchTerms terms = GlobalSearchTerms.parse("서울 강남구 역삼동 염색");

        assertThat(terms.city()).isEqualTo("서울");
        assertThat(terms.district()).isEqualTo("강남구");
        assertThat(terms.neighborhood()).isEqualTo("역삼동");
        assertThat(terms.keyword()).isEqualTo("염색");
    }
}
