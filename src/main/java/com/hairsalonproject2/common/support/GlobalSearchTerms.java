package com.hairsalonproject2.common.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record GlobalSearchTerms(
        String keyword,
        String city,
        String district,
        String neighborhood
) {

    private static final Map<String, String> CITY_TOKEN_ALIASES = Map.ofEntries(
            Map.entry("서울", "서울"),
            Map.entry("서울시", "서울"),
            Map.entry("서울특별시", "서울"),
            Map.entry("부산", "부산"),
            Map.entry("부산시", "부산"),
            Map.entry("부산광역시", "부산"),
            Map.entry("대구", "대구"),
            Map.entry("대구시", "대구"),
            Map.entry("대구광역시", "대구"),
            Map.entry("인천", "인천"),
            Map.entry("인천시", "인천"),
            Map.entry("인천광역시", "인천"),
            Map.entry("광주", "광주"),
            Map.entry("광주시", "광주"),
            Map.entry("광주광역시", "광주"),
            Map.entry("대전", "대전"),
            Map.entry("대전시", "대전"),
            Map.entry("대전광역시", "대전"),
            Map.entry("울산", "울산"),
            Map.entry("울산시", "울산"),
            Map.entry("울산광역시", "울산"),
            Map.entry("세종", "세종"),
            Map.entry("세종시", "세종"),
            Map.entry("세종특별자치시", "세종"),
            Map.entry("경기", "경기"),
            Map.entry("경기도", "경기"),
            Map.entry("강원", "강원"),
            Map.entry("강원도", "강원"),
            Map.entry("강원특별자치도", "강원"),
            Map.entry("충북", "충북"),
            Map.entry("충청북도", "충북"),
            Map.entry("충남", "충남"),
            Map.entry("충청남도", "충남"),
            Map.entry("전북", "전북"),
            Map.entry("전라북도", "전북"),
            Map.entry("전북특별자치도", "전북"),
            Map.entry("전남", "전남"),
            Map.entry("전라남도", "전남"),
            Map.entry("경북", "경북"),
            Map.entry("경상북도", "경북"),
            Map.entry("경남", "경남"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주", "제주"),
            Map.entry("제주도", "제주"),
            Map.entry("제주특별자치도", "제주")
    );

    public static GlobalSearchTerms parse(String keyword) {
        String city = null;
        String district = null;
        String neighborhood = null;
        List<String> remainingKeywords = new ArrayList<>();

        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        for (String rawToken : normalizedKeyword.split("\\s+")) {
            String token = rawToken.trim();
            if (token.isBlank()) {
                continue;
            }

            String normalizedCity = normalizeCityToken(token);
            if (city == null && normalizedCity != null) {
                city = normalizedCity;
                continue;
            }

            if (district == null && isDistrictToken(token)) {
                district = token;
                continue;
            }

            if (neighborhood == null && isNeighborhoodToken(token, city != null || district != null)) {
                neighborhood = token;
                continue;
            }

            remainingKeywords.add(token);
        }

        String parsedKeyword = String.join(" ", remainingKeywords).trim();
        return new GlobalSearchTerms(toNullIfBlank(parsedKeyword), city, district, neighborhood);
    }

    public String boardKeyword(String fallbackKeyword) {
        return keyword == null ? fallbackKeyword : keyword;
    }

    private static String normalizeCityToken(String token) {
        return CITY_TOKEN_ALIASES.get(token);
    }

    private static boolean isDistrictToken(String token) {
        return token.endsWith("구") || token.endsWith("군") || token.endsWith("시");
    }

    private static boolean isNeighborhoodToken(String token, boolean hasRegionPrefix) {
        return token.endsWith("동")
                || token.endsWith("읍")
                || token.endsWith("면")
                || (hasRegionPrefix && (token.endsWith("가") || token.endsWith("리")));
    }

    private static String toNullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
