package com.theglitch.common;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single canonical Nexo bridge for The Glitch.
 * <p>
 * This is the <b>single source of truth</b> for Nexo id detection and item building.
 * Other plugins (GlitchItems, GlitchShops, etc.) should delegate to this class instead
 * of re-implementing id lookups or Nexo API calls.
 * Centralizing here makes updates easy — bump Nexo logic once, all plugins benefit.
 * </p>
 * <p>
 * Migrated from the Oraxen-backed {@code OraxenUtil} 2026-09-20. Uses reflection so
 * GlitchCommon does not require Nexo at compile time (pom stays paper-api only).
 * </p>
 */
public final class NexoUtil {

    /** How long an {@link #available()} lookup is cached (ms). Short TTL keeps late-enable correct. */
    private static final long AVAILABLE_CACHE_MS = 5000L;
    private static volatile long availableCacheTime;
    private static volatile boolean availableCache;

    /** Cached reflection for {@code NexoItems#itemFromId(String)} / {@code #idFromItem(ItemStack)}. */
    private static volatile Class<?> nexoItemsClass;
    private static volatile Method itemFromIdMethod;
    private static volatile Method idFromItemMethod;
    /** Cached {@code ItemBuilder#build()} methods per builder class. */
    private static final Map<Class<?>, Method> BUILD_METHODS = new ConcurrentHashMap<>();

    private NexoUtil() {
    }

    /**
     * Whether Nexo is present on the server.
     * Result is cached briefly to avoid a plugin-manager scan per item build.
     */
    public static boolean available() {
        long now = System.currentTimeMillis();
        if (now - availableCacheTime < AVAILABLE_CACHE_MS && availableCacheTime != 0L) {
            return availableCache;
        }
        boolean present = Bukkit.getPluginManager().getPlugin("Nexo") != null;
        availableCache = present;
        availableCacheTime = now;
        return present;
    }

    /**
     * Build a real Nexo item (texture + lore from the resource pack).
     * Returns null when Nexo is missing or the id is unknown.
     * <p>
     * Uses reflection so GlitchCommon does not require Nexo at compile time. At runtime,
     * if Nexo is present the call delegates to {@code com.nexomc.nexo.api.NexoItems#itemFromId(String)}.
     * </p>
     */
    public static ItemStack build(String id) {
        if (!available()) return null;
        try {
            Method itemFromId = itemFromIdMethod();
            if (itemFromId == null) return null;
            Object builder = itemFromId.invoke(null, id);
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

    private static Method itemFromIdMethod() {
        Method cached = itemFromIdMethod;
        if (cached != null) return cached;
        synchronized (NexoUtil.class) {
            if (itemFromIdMethod != null) return itemFromIdMethod;
            try {
                Class<?> clazz = nexoItemsClass();
                itemFromIdMethod = clazz.getMethod("itemFromId", String.class);
            } catch (Exception e) {
                itemFromIdMethod = null;
            }
            return itemFromIdMethod;
        }
    }

    private static Class<?> nexoItemsClass() throws ClassNotFoundException {
        Class<?> cached = nexoItemsClass;
        if (cached != null) return cached;
        nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
        return nexoItemsClass;
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
     * Nexo id of an item, delegating to {@code NexoItems#idFromItem(ItemStack)}.
     * Returns null when Nexo is missing, the item is null, or the item isn't a NexoItem.
     */
    public static String idOf(ItemStack item) {
        if (item == null || !available()) return null;
        try {
            Method idFromItem = idFromItemMethod();
            if (idFromItem == null) return null;
            Object result = idFromItem.invoke(null, item);
            return result instanceof String ? (String) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Method idFromItemMethod() {
        Method cached = idFromItemMethod;
        if (cached != null) return cached;
        synchronized (NexoUtil.class) {
            if (idFromItemMethod != null) return idFromItemMethod;
            try {
                Class<?> clazz = nexoItemsClass();
                idFromItemMethod = clazz.getMethod("idFromItem", ItemStack.class);
            } catch (Exception e) {
                idFromItemMethod = null;
            }
            return idFromItemMethod;
        }
    }
}
