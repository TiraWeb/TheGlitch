package com.theglitch.common;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Single canonical Oraxen bridge for The Glitch.
 * <p>
 * This is the <b>single source of truth</b> for Oraxen id detection and item building.
 * Other plugins (GlitchItems, GlitchShops, etc.) should delegate to this class instead
 * of re-implementing PDC scanning, id-shape checks, or Oraxen API calls.
 * Centralizing here makes updates easy — bump Oraxen logic once, all plugins benefit.
 * </p>
 */
public final class OraxenUtil {

    /** PDC key Oraxen uses for custom_item_id (legacy). */
    public static final NamespacedKey ORAXEN_ID_KEY = new NamespacedKey("oraxen", "custom_item_id");

    private OraxenUtil() {
    }

    /**
     * Whether Oraxen is present on the server.
     */
    public static boolean available() {
        return Bukkit.getPluginManager().getPlugin("Oraxen") != null;
    }

    /**
     * Build a real Oraxen item (texture + lore from the resource pack).
     * Returns null when Oraxen is missing or the id is unknown.
     * <p>
     * Uses reflection so GlitchCommon does not require Oraxen at compile time
     * (pom stays paper-api only). At runtime, if Oraxen is present the call
     * delegates to {@code io.th0rgal.oraxen.api.OraxenItems#getItemById(String)}.
     * </p>
     */
    public static ItemStack build(String id) {
        if (!available()) return null;
        try {
            Class<?> oraxenItems = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            java.lang.reflect.Method getById = oraxenItems.getMethod("getItemById", String.class);
            Object builder = getById.invoke(null, id);
            if (builder == null) return null;
            java.lang.reflect.Method buildMethod = builder.getClass().getMethod("build");
            Object result = buildMethod.invoke(builder);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Best-effort id-shape check — char loop, no regex (fastest, matches GlitchItems impl).
     * Valid ids are lowercase a-z and '_' only.
     */
    public static boolean isIdShaped(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '_' && (c < 'a' || c > 'z')) return false;
        }
        return true;
    }

    /**
     * Best-effort Oraxen id of an item: the old custom_item_id key first, then
     * a scan of all string PDC values for an id-shaped value — the same
     * strategy GlitchShops uses successfully for sell prices on real items.
     */
    public static String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        try {
            String id = pdc.get(ORAXEN_ID_KEY, PersistentDataType.STRING);
            if (id != null && !id.isEmpty()) return id;
        } catch (Exception ignored) {}
        for (NamespacedKey key : pdc.getKeys()) {
            try {
                if (!pdc.has(key, PersistentDataType.STRING)) continue;
                String value = pdc.get(key, PersistentDataType.STRING);
                if (isIdShaped(value)) {
                    return value;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
