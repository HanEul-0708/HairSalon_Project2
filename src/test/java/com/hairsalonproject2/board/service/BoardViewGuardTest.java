package com.hairsalonproject2.board.service;

import com.hairsalonproject2.common.constant.BoardType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoardViewGuardTest {

    private final BoardViewGuard boardViewGuard = new BoardViewGuard();

    @Test
    void shouldIncreaseViewAndWriteCookieForFirstView() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean shouldIncrease = boardViewGuard.shouldIncreaseView(10, BoardType.QNA, request, response);

        assertThat(shouldIncrease).isTrue();
        Cookie cookie = response.getCookie("board_view_history");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).contains(URLEncoder.encode("QNA_10", StandardCharsets.UTF_8));
    }

    @Test
    void shouldNotIncreaseViewWhenBoardAlreadyExistsInCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setCookies(new Cookie(
                "board_view_history",
                URLEncoder.encode("NOTICE_3|QNA_10", StandardCharsets.UTF_8)
        ));

        boolean shouldIncrease = boardViewGuard.shouldIncreaseView(10, BoardType.QNA, request, response);

        assertThat(shouldIncrease).isFalse();
        assertThat(response.getCookie("board_view_history")).isNull();
    }
}
