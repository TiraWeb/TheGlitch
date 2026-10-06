package com.theglitch.glitchshops.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class UiKit {

    public static final String GLYPH_OPEN = "<font:minecraft:default>\uE049</font>";
    public static final String GLYPH_CLOSE = "<font:minecraft:default>\uE049</font>";
    public static final String DIVIDER_MM = "<dark_gray>\uE048</dark_gray>";
    public static final String SHARD_GLYPH = "\uE045";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private UiKit() {
    }

    public static Component deserialized(String mini) {
        return MM.deserialize(mini);
    }

    public static String title(String label) {
        return titleCustom("#C084FC", "#F0ABFC", label);
    }

    public static String titleCustom(String fromHex, String toHex, String label) {
        return GLYPH_OPEN + " <gradient:" + fromHex + ":" + toHex
                + "><bold>" + label + "</bold></gradient> " + GLYPH_CLOSE;
    }
}
