package com.theglitch.glitchitems;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Small Oraxen bridge so item code does not re-implement id detection per
 * plugin. Builds items through the Oraxen API (textures + lore + sell-price
 * lines come from the resource pack config) and detects item ids the same way
 * ShopManager does (PDC scan — Oraxen's own PDC key name varies, so scanning
 * every string value for a lowercase id shape is the reliable route).
 */
public final class OraxenUtil {

    private static final NamespacedKey ORAXEN_ID_KEY = new NamespacedKey("oraxen", "custom_item_id");

    private OraxenUtil() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().getPlugin("Oraxen") != null;
    }

    /**
     * Build a real Oraxen item (texture + lore from the pack). Returns null
     * when Oraxen is missing or the id is unknown.
     */
    public static ItemStack build(String id) {
        if (!available()) return null;
        try {
            ItemBuilder builder = OraxenItems.getItemById(id);
            return builder == null ? null : builder.build();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Best-effort Oraxen id of an item: the old custom_item_id key first, then
     * a scan of all string PDC values for an id-shaped value — the same
     * strategy GlitchShops uses successfully for sell prices on real items.
     */
    private static boolean isIdShaped(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '_' && (c < 'a' || c > 'z')) return false;
        }
        return true;
    }

    public static String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(ORAXEN_ID_KEY, PersistentDataType.STRING);
        if (id != null && !id.isEmpty()) return id;
        for (NamespacedKey key : pdc.getKeys()) {
            String value = pdc.get(key, PersistentDataType.STRING);
            if (isIdShaped(value)) {
                return value;
            }
        }
        return null;
    }
}
