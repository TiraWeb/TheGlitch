package com.theglitch.glitchshops.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UiKit {

    public static final String GLYPH_OPEN = "<font:minecraft:default>\uE049</font>";
    public static final String GLYPH_CLOSE = "<font:minecraft:default>\uE049</font>";
    public static final String DIVIDER_MM = "<dark_gray>\uE048</dark_gray>";
    public static final String SHARD_GLYPH = "\uE045";

    public static final Material[] RAMP = {
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE
    };

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Material, ItemStack> PANE_CACHE = new ConcurrentHashMap<>();

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

    public static ItemStack blankPane(Material material) {
        return PANE_CACHE.computeIfAbsent(material, UiKit::buildBlankPane).clone();
    }

    private static ItemStack buildBlankPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }
        return pane;
    }
}
