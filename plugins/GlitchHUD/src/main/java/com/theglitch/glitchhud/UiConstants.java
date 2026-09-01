package com.theglitch.glitchhud;

/**
 * Shared glyph / font constants — mirrors
 * {@code server/plugins/Oraxen/glyphs/theglitch.yml} and
 * {@code plugins/GlitchItems/.../GlitchUI.java}.
 * Bedrock fallback: every usage must pair glyph with plain text.
 */
public final class UiConstants {

    // Oraxen PUA glyphs (minecraft:default)
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

    // Negative-space font (minecraft:negative_space) — advance shims.
    // Generates pixel-precise shifts without mods. Add via
    // server/plugins/Oraxen/pack/assets/minecraft/font/negative_space.json
    public static final String NEG_1  = "\uF801"; // advance -2 (tuned by font json)
    public static final String NEG_2  = "\uF802";
    public static final String NEG_4  = "\uF803";
    public static final String NEG_8  = "\uF804";
    public static final String NEG_16 = "\uF805";

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

    public static String stars(int filled, int total) {
        StringBuilder sb = new StringBuilder(total * 4);
        for (int i = 0; i < total; i++) {
            sb.append(i < filled ? STAR_FULL : STAR_EMPTY);
        }
        return sb.toString();
    }
}
