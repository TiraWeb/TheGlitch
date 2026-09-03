package com.theglitch.glitchitems;

/**
 * Custom font-glyph unicode constants (Arcane Ruins UI kit).
 *
 * Each codepoint is mapped to a texture by Oraxen's vanilla glyph handler —
 * source of truth: server/plugins/Oraxen/glyphs/theglitch.yml (keep in sync
 * with scripts/gen-ui-textures.py output). Bedrock/Geyser clients cannot
 * render these; every usage must pair a plain-text label alongside.
 */
public final class GlitchUI {

    public static final String RES_AEGIS = "\uE040";
    public static final String RES_VEIL = "\uE041";
    public static final String RES_BLOOM = "\uE042";
    public static final String RES_WARD = "\uE043";
    public static final String RES_HOLLOW = "\uE044";
    public static final String SHARD = "\uE045";
    public static final String STAR_FULL = "\uE046";
    public static final String STAR_EMPTY = "\uE047";
    public static final String DIVIDER = "\uE048";
    public static final String TITLE_RUNE = "\uE049";

    private static final String FULL_PIP = "<gold>" + STAR_FULL + "</gold>";
    private static final String EMPTY_PIP = "<dark_gray>" + STAR_EMPTY + "</dark_gray>";

    private GlitchUI() {
    }

    public static String resIcon(Resonance resonance) {
        return switch (resonance) {
            case AEGIS -> RES_AEGIS;
            case VEIL -> RES_VEIL;
            case BLOOM -> RES_BLOOM;
            case WARD -> RES_WARD;
            case HOLLOW -> RES_HOLLOW;
        };
    }

    /** Wynncraft-style star pips: always five slots, filled up to {@code count}. */
    public static String pips(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < count ? FULL_PIP : EMPTY_PIP);
        }
        return sb.toString();
    }
}
