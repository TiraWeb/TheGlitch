package com.theglitch.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Items lent during the new-player tutorial (GlitchTutorial) carry this tag. They can't be
 * stashed, sold, insured, dropped or carried out — every plugin that takes items checks
 * {@link #isTutorial} — and the tutorial strips them when it ends.
 * The key is "glitchtutorial:tutorial_item" in every plugin (fixed namespace, not per-plugin).
 */
public final class TutorialItems {

    @SuppressWarnings("deprecation")
    public static final NamespacedKey KEY = new NamespacedKey("glitchtutorial", "tutorial_item");

    private TutorialItems() {
    }

    public static boolean isTutorial(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** Tags the item (in place) and adds a lore line; returns it for chaining. */
    public static ItemStack tag(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Tutorial item — returned at the end", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Removes every tutorial item from an inventory; returns how many stacks were removed. */
    public static int strip(Inventory inv) {
        int n = 0;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTutorial(contents[i])) {
                contents[i] = null;
                n++;
            }
        }
        if (n > 0) inv.setContents(contents);
        return n;
    }

    /** Inventory (incl. armor/offhand), cursor and ender chest. */
    public static int stripAll(Player p) {
        int n = strip(p.getInventory()) + strip(p.getEnderChest());
        if (isTutorial(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
            n++;
        }
        return n;
    }
}
