package com.fpoly.duan.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SearchUtilsTest {

    @Test
    void pickUsesTheFirstNonBlankSearchTerm() {
        assertEquals("movie", SearchUtils.pick("movie", "keyword", "query"));
        assertEquals("keyword", SearchUtils.pick("  ", "keyword", "query"));
        assertEquals("query", SearchUtils.pick(null, "", "query"));
        assertEquals("", SearchUtils.pick(null, " ", null));
    }

    @Test
    void matchesIgnoresCaseAndVietnameseDiacritics() {
        assertTrue(SearchUtils.matches("dien anh", "Điện Ảnh Quốc Gia"));
        assertTrue(SearchUtils.matches("DANG", "Đặng Văn A"));
        assertFalse(SearchUtils.matches("rap", "Phim hành động", 42));
    }

    @Test
    void blankSearchMatchesEveryValue() {
        assertTrue(SearchUtils.matches(null, "anything"));
        assertTrue(SearchUtils.matches("   ", "anything"));
        assertTrue(SearchUtils.isBlank("\t"));
        assertFalse(SearchUtils.isBlank("movie"));
    }
}
