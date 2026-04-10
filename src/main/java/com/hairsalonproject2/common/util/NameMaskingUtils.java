package com.hairsalonproject2.common.util;

public final class NameMaskingUtils {

    private NameMaskingUtils() {
    }

    public static String maskName(String name) {
        if (name == null) {
            return "";
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.length() == 1) {
            return trimmed + "*";
        }

        if (trimmed.length() == 2) {
            return trimmed.charAt(0) + "*";
        }

        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 2) + trimmed.charAt(trimmed.length() - 1);
    }
}
