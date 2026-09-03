package com.theglitch.glitchdungeons;

/**
 * Local static helper for legacy color codes (SAFE cleanup: same regex, no behavior change).
 * Per-plugin helper — do NOT move cross-plugin yet.
 */
public final class ColorUtil {
    private ColorUtil() {
    }

    public static String colorize(String msg) {
        return msg.replaceAll("&([0-9a-fk-or])", "§$1");
    }
}
