package com.hairsalonproject2.common.util;

import java.util.List;

public final class RegionPresetUtils {

    private static final List<String> BUSAN_DISTRICT_ADDRESSES = List.of(
            "부산 강서구",
            "부산 금정구",
            "부산 기장군",
            "부산 남구",
            "부산 동구",
            "부산 동래구",
            "부산 부산진구",
            "부산 북구",
            "부산 사상구",
            "부산 사하구",
            "부산 서구",
            "부산 수영구",
            "부산 연제구",
            "부산 영도구",
            "부산 중구",
            "부산 해운대구"
    );

    private RegionPresetUtils() {
    }

    public static List<String> getBusanDistrictAddresses() {
        return BUSAN_DISTRICT_ADDRESSES;
    }
}