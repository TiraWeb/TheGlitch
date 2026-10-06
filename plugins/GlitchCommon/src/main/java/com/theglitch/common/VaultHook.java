package com.theglitch.common;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared Vault economy lookup, replacing the per-plugin {@code getEconomy()} copies.
 * <p>
 * The economy type is passed in by the caller (each plugin already compiles against
 * VaultAPI), so GlitchCommon itself stays {@code paper-api}-only:
 * <pre>
 *   Economy econ = VaultHook.economy(this, Economy.class);
 * </pre>
 * A found provider is cached for {@value #CACHE_MS} ms so a swapped provider is picked
 * up eventually. A missing provider is never cached: an economy plugin that registers
 * after our {@code onEnable} is found on the next call.
 */
public final class VaultHook {

    private static final long CACHE_MS = 30_000L;

    private static Object economy;
    private static long cachedAt;

    private VaultHook() {
    }

    /**
     * The registered economy provider, or {@code null} when none is registered.
     *
     * @param plugin      calling plugin, used to log the first successful lookup
     * @param economyType {@code net.milkbowl.vault.economy.Economy.class}
     */
    public static synchronized <T> T economy(JavaPlugin plugin, Class<T> economyType) {
        long now = System.currentTimeMillis();
        if (economyType.isInstance(economy) && now - cachedAt < CACHE_MS) {
            return economyType.cast(economy);
        }
        RegisteredServiceProvider<T> registration = Bukkit.getServicesManager().getRegistration(economyType);
        T found = registration == null ? null : registration.getProvider();
        if (found != null && found != economy) {
            plugin.getLogger().info("Economy provider found: " + registration.getPlugin().getName());
        }
        economy = found;
        cachedAt = now;
        return found;
    }
}
