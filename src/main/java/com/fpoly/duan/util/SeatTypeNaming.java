package com.fpoly.duan.util;

import java.util.Locale;

public final class SeatTypeNaming {

    private SeatTypeNaming() {
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
