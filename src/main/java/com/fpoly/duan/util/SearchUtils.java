package com.fpoly.duan.util;

import java.text.Normalizer;
import java.util.Locale;

public final class SearchUtils {

    private SearchUtils() {
    }

    public static String pick(String search, String keyword, String q) {
        if (hasText(search)) return search;
        if (hasText(keyword)) return keyword;
        if (hasText(q)) return q;
        return "";
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean matches(String rawNeedle, Object... values) {
        if (isBlank(rawNeedle)) return true;
        String needle = normalize(rawNeedle);
        for (Object value : values) {
            if (value != null && normalize(String.valueOf(value)).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return normalized.toLowerCase(Locale.ROOT).trim();
    }
}
