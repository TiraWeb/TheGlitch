package com.theglitch.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Textured chest-menu titles. Each background is one bitmap glyph in the
 * {@code theglitch:menus} font (built by scripts/gen-menu-backgrounds.py);
 * {@code } shifts it back so the art lines up with the chest window.
 * Empty slots show the art, so menus using these need no glass fillers.
 * Bedrock players (Geyser, UUID msb == 0) can't render the pack glyphs and
 * get a plain text title instead.
 */
public final class MenuTitles {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // 3-row backgrounds
    public static final char REWARDS = '';
    public static final char QUESTS = '';
    public static final char HIDEOUT = '';
    public static final char RED_ZONE = '';
    // 5-row backgrounds
    public static final char CLASS = '';
    // 6-row backgrounds
    public static final char WORKBENCH = '';
    public static final char STASH = '';
    public static final char ARMORY = '';
    public static final char BAZAAR = '';
    public static final char CLASSES = '';
    public static final char DUNGEONS = '';

    private MenuTitles() {
    }

    public static boolean isBedrock(Player player) {
        return player.getUniqueId().getMostSignificantBits() == 0L;
    }

    /** Background glyph title for Java players, {@code plainFallback} (MiniMessage) for Bedrock. */
    public static Component title(Player player, char background, String plainFallback) {
        if (isBedrock(player)) return MM.deserialize(plainFallback);
        return MM.deserialize("<white><font:theglitch:menus>" + background + "</font></white>");
    }
}
