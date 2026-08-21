package com.theglitch.common;

import java.util.regex.Pattern;

/**
 * Shared color code helper — dedupes the copy-pasta {@code colorize} in GlitchDungeons.
 * Converts &-codes to § codes for legacy chat.
 */
public final class ColorUtil {

    private static final Pattern COLOR_PATTERN = Pattern.compile("&([0-9a-fk-or])");

    private ColorUtil() {
    }

    /**
     * Convert &-color codes to § codes.
     *
     * @param s input string with &-codes
     * @return colorized string or empty if null
     */
    public static String colorize(String s) {
        return s == null ? "" : COLOR_PATTERN.matcher(s).replaceAll("\u00A7$1");
    }
}
