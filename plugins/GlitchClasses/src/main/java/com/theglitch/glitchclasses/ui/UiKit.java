package com.theglitch.glitchclasses.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Modern UI kit for GlitchClasses menus — glyphs, gradients and cached panes.
 * Glyph codepoints map to textures via Nexo's vanilla glyph handler
 * (server/plugins/Nexo/glyphs/oraxen_glyphs/theglitch.yml); bedrock clients see fallbacks.
 */
public final class UiKit {

    public static final String GLYPH_OPEN = "<font:minecraft:default>\uE049</font>";
    public static final String GLYPH_CLOSE = "<font:minecraft:default>\uE049</font>";
    public static final String DIVIDER_MM = "<dark_gray>\uE048</dark_gray>";
    public static final String STAR_FULL = "\uE046";
    public static final String STAR_EMPTY = "\uE047";

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private UiKit() {
    }

    public static MiniMessage mm() {
        return MINI;
    }

    public static Component deserialized(String s) {
        return MINI.deserialize(s);
    }

    public static String title(String label) {
        return titleCustom("#C084FC", "#F0ABFC", label);
    }

    public static String titleCustom(String fromHex, String toHex, String label) {
        return GLYPH_OPEN + " <gradient:" + fromHex + ":" + toHex + "><bold>" + label
                + "</bold></gradient> " + GLYPH_CLOSE;
    }

    public static ItemStack pipsItem(int filled, int total) {
        int max = Math.max(1, total);
        int shown = Math.max(0, Math.min(filled, max));
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < max; i++) {
            stars.append(i < shown ? "<gold>" + STAR_FULL + "</gold>"
                    : "<dark_gray>" + STAR_EMPTY + "</dark_gray>");
        }
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.customName(deserialized("<gold><bold>MASTERY</bold></gold>"));
            meta.lore(List.of(
                    deserialized(stars.toString()),
                    deserialized("<gray>" + shown + "</gray><dark_gray>/</dark_gray><gray>" + max + "</gray>")));
            item.setItemMeta(meta);
        }
        return item;
    }
}
