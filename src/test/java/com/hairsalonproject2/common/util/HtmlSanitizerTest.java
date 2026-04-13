package com.hairsalonproject2.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    void sanitizeKeepsEditorFormattingAndRemovesExecutableHtml() {
        String html = """
                <p style="color: #ff0000; position: absolute">안녕하세요</p>
                <script>alert('xss')</script>
                <img src="/files/images/test.png" class="board-image-align-center evil" onerror="alert(1)">
                <a href="javascript:alert(1)" onclick="alert(1)">bad link</a>
                <span style="font-size: 16px; background-image: url(javascript:alert(1))">본문</span>
                """;

        String sanitized = HtmlSanitizer.sanitize(html);

        assertThat(sanitized).contains("안녕하세요");
        assertThat(sanitized).contains("style=\"color: #ff0000\"");
        assertThat(sanitized).contains("src=\"/files/images/test.png\"");
        assertThat(sanitized).contains("class=\"board-image-align-center\"");
        assertThat(sanitized).contains("font-size: 16px");
        assertThat(sanitized).doesNotContain("<script");
        assertThat(sanitized).doesNotContain("onerror");
        assertThat(sanitized).doesNotContain("onclick");
        assertThat(sanitized).doesNotContain("javascript:");
        assertThat(sanitized).doesNotContain("position");
        assertThat(sanitized).doesNotContain("evil");
        assertThat(sanitized).doesNotContain("background-image");
    }

    @Test
    void sanitizeRemovesTextareaBreakoutFromLegacyContent() {
        String sanitized = HtmlSanitizer.sanitize("</textarea><script>alert('xss')</script><p>safe</p>");

        assertThat(sanitized).contains("<p>safe</p>");
        assertThat(sanitized).doesNotContain("</textarea>");
        assertThat(sanitized).doesNotContain("<script");
    }
}
