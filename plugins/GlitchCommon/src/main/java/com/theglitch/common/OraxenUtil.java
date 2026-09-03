package com.theglitch.common;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** How long an {@link #available()} lookup is cached (ms). Short TTL keeps late-enable correct. */
    private static final long AVAILABLE_CACHE_MS = 5000L;
    private static volatile long availableCacheTime;
    private static volatile boolean availableCache;

    /** Cached reflection for {@code OraxenItems#getItemById(String)} — avoids per-build lookup. */
    private static volatile Class<?> oraxenItemsClass;
    private static volatile Method getItemByIdMethod;
    /** Cached {@code ItemBuilder#build()} methods per builder class. */
    private static final Map<Class<?>, Method> BUILD_METHODS = new ConcurrentHashMap<>();

    private OraxenUtil() {
    }

    /**
     * Whether Oraxen is present on the server.
     * Result is cached briefly to avoid a plugin-manager scan per item build.
     */
    public static boolean available() {
        long now = System.currentTimeMillis();
        if (now - availableCacheTime < AVAILABLE_CACHE_MS && availableCacheTime != 0L) {
            return availableCache;
        }
        boolean present = Bukkit.getPluginManager().getPlugin("Oraxen") != null;
        availableCache = present;
        availableCacheTime = now;
        return present;
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
            Method getById = getItemByIdMethod();
            if (getById == null) return null;
            Object builder = getById.invoke(null, id);
            if (builder == null) return null;
            Method buildMethod = BUILD_METHODS.computeIfAbsent(builder.getClass(), clazz -> {
                try {
                    return clazz.getMethod("build");
                } catch (NoSuchMethodException e) {
                    return null;
                }
            });
            if (buildMethod == null) return null;
            Object result = buildMethod.invoke(builder);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Method getItemByIdMethod() {
        Method cached = getItemByIdMethod;
        if (cached != null) return cached;
        synchronized (OraxenUtil.class) {
            if (getItemByIdMethod != null) return getItemByIdMethod;
            try {
                oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                getItemByIdMethod = oraxenItemsClass.getMethod("getItemById", String.class);
            } catch (Exception e) {
                getItemByIdMethod = null;
            }
            return getItemByIdMethod;
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
