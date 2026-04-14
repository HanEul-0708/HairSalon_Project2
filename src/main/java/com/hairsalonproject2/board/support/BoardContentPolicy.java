package com.hairsalonproject2.board.support;

import org.jsoup.Jsoup;

public final class BoardContentPolicy {

    public static final int MAX_TEXT_LENGTH = 5000;
    public static final int MAX_HTML_LENGTH = 20000;

    private BoardContentPolicy() {
    }

    public static int textLength(String html) {
        String text = Jsoup.parse(html == null ? "" : html).text();
        return text.codePointCount(0, text.length());
    }

    public static boolean exceedsTextLimit(String html) {
        return textLength(html) > MAX_TEXT_LENGTH;
    }
}
