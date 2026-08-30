package com.theglitch.common;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Central Vault Economy cache — any plugin can call {@code VaultHook.getEconomy(this)}
 * without duplicating per-plugin cache logic.
 * <p>
 * Caches the Vault {@code Economy} provider for 30 seconds to avoid per-transaction
 * service lookups. Call {@link #invalidate()} on reload or when the provider changes.
 * Uses reflection to avoid hard compile dependency on VaultAPI; pom stays paper-api only.
 * At runtime the returned object is {@code net.milkbowl.vault.economy.Economy}.
 * </p>
 * <p>Canonical usage:</p>
 * <pre>
 *   Object econ = VaultHook.getEconomy(this);
 *   // or typed: Economy econ = VaultHook.getEconomyTyped(this, Economy.class);
 * </pre>
 */
public final class VaultHook {

    // Canonical spec: private static Economy economy; private static long cacheTime;
    // Implemented as Object to keep GlitchCommon compileable with paper-api only.
    // The runtime type is net.milkbowl.vault.economy.Economy when Vault is present.
    private static Object economy;
    private static long cacheTime;

    private VaultHook() {
    }

    /**
     * Get cached Vault Economy. Returns {@code net.milkbowl.vault.economy.Economy} as Object
     * (cast in caller) or null if Vault is absent. Cached for 30 seconds.
     *
     * @param plugin calling plugin (used for future logging, currently unused — lookup is global via Bukkit)
     * @return Economy instance or null
     */
    public static synchronized Object getEconomy(JavaPlugin plugin) {
        long now = System.currentTimeMillis();
        if (cacheTime != 0L && now - cacheTime < 30000L) {
            return economy;
        }
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            @SuppressWarnings("unchecked")
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration((Class<Object>) econClass);
            economy = reg != null ? reg.getProvider() : null;
        } catch (ClassNotFoundException e) {
            economy = null;
        }
        cacheTime = now;
        return economy;
    }

    /**
     * Typed helper for callers that have VaultAPI on their classpath.
     * Avoids unchecked cast at call site.
     *
     * @param plugin calling plugin
     * @param type Economy class token
     * @return typed Economy or null
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T> T getEconomyTyped(JavaPlugin plugin, Class<T> type) {
        Object econ = getEconomy(plugin);
        return type.isInstance(econ) ? (T) econ : null;
    }

    /**
     * Invalidate cached Economy — call on reload or when provider changes.
     */
    public static synchronized void invalidate() {
        economy = null;
        cacheTime = 0;
    }
}
