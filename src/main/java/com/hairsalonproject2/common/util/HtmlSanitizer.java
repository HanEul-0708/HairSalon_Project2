package com.hairsalonproject2.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HtmlSanitizer
 * 게시글 본문 HTML에서 위험한 태그와 속성을 제거하는 도구
 */
public class HtmlSanitizer {

    /**
     * HTML 정리
     * - 기본적인 글자 꾸밈 허용
     * - 이미지 허용
     * - 링크 허용
     * - script, iframe 같은 위험 태그 제거
     * - style 속성 제거
     */
    public static String sanitize(String html) {

        if (html == null || html.isBlank()) {
            return "";
        }

        /*
         * basicWithImages()
         * -> 기본 글자 꾸밈 + 링크 + 이미지 허용
         */
        Safelist safelist = Safelist.basicWithImages();

        /*
         * 링크 허용 속성 보강
         */
        safelist.addAttributes("a", "href", "title", "target");

        /*
         * 이미지 허용 속성 보강
         */
        safelist.addAttributes("img", "src", "alt", "title", "width", "height");

        /*
         * iframe 제거
         * 영상 임베드 등을 막고 싶을 때 안전하다
         */
        safelist.removeTags("iframe");

        /*
         * style 속성 제거
         * CSS를 이용한 우회 공격 가능성을 줄임
         */
        safelist.removeAttributes(":all", "style");

        /*
         * clean()
         * -> 허용한 것만 남기고 나머지는 제거
         */
        return Jsoup.clean(html, safelist);
    }
}