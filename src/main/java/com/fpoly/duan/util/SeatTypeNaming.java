package com.fpoly.duan.util;

import java.util.Locale;

/**
 * Quy ước nhận diện loại ghế đôi (một vé / một chỗ ngồi logic chiếm 2 ô) theo tên loại.
 * Giữ khớp logic phía FE (đôi / sweet / couple / double).
 */
public final class SeatTypeNaming {

    private SeatTypeNaming() {
    }

    public static boolean isCoupleSeatType(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String s = name.toLowerCase();
        return s.contains("đôi") || s.contains("sweet") || s.contains("couple") || s.contains("double");
    }

    /**
     * Chuẩn hóa màu lưu DB: {@code #RRGGBB} in hoa, hoặc {@code null} nếu rỗng / không hợp lệ.
     */
    public static String normalizeColorHex(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String s = input.trim();
        if (!s.startsWith("#")) {
            s = "#" + s;
        }
        if (s.length() != 7) {
            return null;
        }
        String hex = s.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        return "#" + hex.toUpperCase(Locale.ROOT);
    }
}
