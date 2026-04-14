package com.hairsalonproject2.common.util;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class AddressRegionUtils {

    private static final String[] NEIGHBORHOOD_SUFFIXES = {"동", "가", "읍", "면", "리"};

    private AddressRegionUtils() {
    }

    public static RegionParts parse(String address) {
        if (address == null || address.isBlank()) {
            return new RegionParts("", "", "");
        }

        String[] tokens = address.trim().split("\\s+");
        String city = tokens.length > 0 ? normalize(tokens[0]) : "";
        String district = tokens.length > 1 ? normalize(tokens[1]) : "";
        String neighborhood = tokens.length > 2 ? normalizeNeighborhood(tokens[2]) : "";
        return new RegionParts(city, district, neighborhood);
    }

    public static String combine(String city, String district, String neighborhood) {
        StringBuilder builder = new StringBuilder();
        append(builder, city);
        append(builder, district);
        append(builder, neighborhood);
        return builder.toString().trim();
    }

    public static boolean matches(String address, String city, String district, String neighborhood) {
        RegionParts parts = parse(address);
        return matchesValue(parts.city(), city)
                && matchesValue(parts.district(), district)
                && matchesValue(parts.neighborhood(), neighborhood);
    }

    public static List<String> cityOptions(Collection<String> addresses) {
        return addresses.stream()
                .map(AddressRegionUtils::parse)
                .map(RegionParts::city)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> districtOptions(Collection<String> addresses, String city) {
        return addresses.stream()
                .map(AddressRegionUtils::parse)
                .filter(parts -> matchesValue(parts.city(), city))
                .map(RegionParts::district)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> neighborhoodOptions(Collection<String> addresses, String city, String district) {
        return addresses.stream()
                .map(AddressRegionUtils::parse)
                .filter(parts -> matchesValue(parts.city(), city))
                .filter(parts -> matchesValue(parts.district(), district))
                .map(RegionParts::neighborhood)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean matchesValue(String actual, String expected) {
        return expected == null || expected.isBlank() || Objects.equals(actual, normalize(expected));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeNeighborhood(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }

        for (String suffix : NEIGHBORHOOD_SUFFIXES) {
            if (normalized.endsWith(suffix)) {
                return normalized;
            }
        }

        return "";
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(value.trim());
    }

    public record RegionParts(String city, String district, String neighborhood) {
    }
}
