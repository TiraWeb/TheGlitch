package com.theglitch.glitchclasses.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern UI kit for GlitchClasses menus — glyphs, gradients and cached panes.
 * Glyph codepoints map to textures via Oraxen's vanilla glyph handler
 * (server/plugins/Oraxen/glyphs/theglitch.yml); bedrock clients see fallbacks.
 */
public final class UiKit {

    public static final String GLYPH_OPEN = "<font:minecraft:default>\uE049</font><font:theglitch:ui>";
    public static final String GLYPH_CLOSE = "</font><font:minecraft:default>\uE049</font>";
    public static final String DIVIDER_MM = "<dark_gray>\uE048</dark_gray>";
    public static final String STAR_FULL = "\uE046";
    public static final String STAR_EMPTY = "\uE047";

    /** Light-to-dark framing ramp used left-to-right across banner rows. */
    public static final Material[] RAMP = {
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE};

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Map<Material, ItemStack> PANE_CACHE = new ConcurrentHashMap<>();

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

    public static String classGradientFrom(String className) {
        return switch (className == null ? "" : className.toLowerCase(Locale.ROOT)) {
            case "vanguard" -> "#F87171";
            case "warden" -> "#34D399";
            case "specter" -> "#818CF8";
            case "operator" -> "#C084FC";
            default -> "#C084FC";
        };
    }

    public static String classGradientTo(String className) {
        return switch (className == null ? "" : className.toLowerCase(Locale.ROOT)) {
            case "vanguard" -> "#FCA5A5";
            case "warden" -> "#6EE7B7";
            case "specter" -> "#A5B4FC";
            case "operator" -> "#E879F9";
            default -> "#F0ABFC";
        };
    }

    /** Spread 0..8 columns onto the RAMP light-to-dark. */
    public static Material rampMaterial(int column) {
        int clamped = Math.max(0, Math.min(8, column));
        int idx = Math.min(RAMP.length - 1, clamped * (RAMP.length - 1) / 8);
        return RAMP[idx];
    }

    public static ItemStack blankPane(Material m) {
        return PANE_CACHE.computeIfAbsent(m, mat -> {
            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.customName(Component.empty());
                stack.setItemMeta(meta);
            }
            return stack;
        }).clone();
    }

    public static ItemStack rampPane(int column) {
        return blankPane(rampMaterial(column));
    }

    public static ItemStack runeCorner() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.customName(deserialized("\uE049 <gradient:#C084FC:#E879F9><bold>Rift Attuned</bold></gradient>"));
            meta.lore(List.of(
                    deserialized("<gray>The anomaly hums around this place.</gray>"),
                    deserialized(DIVIDER_MM + " <dark_purple><italic>tuned to the rift</italic></dark_purple>")));
            item.setItemMeta(meta);
        }
        return item;
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
