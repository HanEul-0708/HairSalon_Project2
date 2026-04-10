package com.hairsalonproject2.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameMaskingUtilsTest {

    @Test
    void maskNameReturnsEmptyStringForNullOrBlank() {
        assertThat(NameMaskingUtils.maskName(null)).isEmpty();
        assertThat(NameMaskingUtils.maskName("   ")).isEmpty();
    }

    @Test
    void maskNameMasksSingleCharacterName() {
        assertThat(NameMaskingUtils.maskName("이")).isEqualTo("이*");
    }

    @Test
    void maskNameMasksTwoCharacterName() {
        assertThat(NameMaskingUtils.maskName("이영")).isEqualTo("이*");
    }

    @Test
    void maskNameMasksMiddleCharactersForLongerNames() {
        assertThat(NameMaskingUtils.maskName("이수영")).isEqualTo("이*영");
        assertThat(NameMaskingUtils.maskName("홍길동님")).isEqualTo("홍**님");
    }
}
