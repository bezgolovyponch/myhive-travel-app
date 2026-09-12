package com.myhive.backend.util;

/** Cell-level helpers shared by the CSV exporters. */
public final class CsvCells {

    private CsvCells() {}

    public static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Formula-injection guard: a cell starting with = + - @ tab or CR would be executed by
     * Excel/Sheets; a leading apostrophe makes it literal text.
     */
    public static String sanitize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + s;
        }
        return s;
    }
}
