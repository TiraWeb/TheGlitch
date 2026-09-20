package com.theglitch.glitchhud;

/**
 * Shared glyph / font constants — mirrors
 * {@code server/plugins/Nexo/glyphs/oraxen_glyphs/theglitch.yml} and
 * {@code plugins/GlitchItems/.../GlitchUI.java}.
 * Bedrock fallback: every usage must pair glyph with plain text.
 */
public final class UiConstants {

    // Nexo PUA glyphs (minecraft:default)
    public static final String RES_AEGIS   = "\uE040";
    public static final String RES_VEIL    = "\uE041";
    public static final String RES_BLOOM   = "\uE042";
    public static final String RES_WARD    = "\uE043";
    public static final String RES_HOLLOW  = "\uE044";
    public static final String SHARD       = "\uE045";
    public static final String STAR_FULL   = "\uE046";
    public static final String STAR_EMPTY  = "\uE047";
    public static final String DIVIDER     = "\uE048";
    public static final String TITLE_RUNE  = "\uE049";

    private UiConstants() {}

    public static String resIcon(String className) {
        if (className == null) return STAR_FULL;
        return switch (className.toLowerCase(java.util.Locale.ROOT)) {
            case "vanguard", "aegis" -> RES_AEGIS;
            case "veil", "specter"   -> RES_VEIL;
            case "bloom", "apothecary" -> RES_BLOOM;
            case "ward", "bulwark"   -> RES_WARD;
            case "hollow", "hollows" -> RES_HOLLOW;
            default -> STAR_FULL;
        };
    }
}
