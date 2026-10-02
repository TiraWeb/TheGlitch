package com.theglitch.glitchtutorial;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection into GlitchItems (gear), GlitchClasses (has a class?) and Vault (Shards) —
 * the Glitch plugins are separate jars with no shared compile classpath (same pattern as
 * GlitchBots' GlitchHooks). Every call degrades to a safe default.
 */
final class Hooks {

    private final GlitchTutorial plugin;

    Hooks(GlitchTutorial plugin) {
        this.plugin = plugin;
    }

    private static Plugin enabled(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled() ? p : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    ItemStack gear(String type, String rarity) {
        Plugin items = enabled("GlitchItems");
        if (items == null) return null;
        try {
            Object gm = items.getClass().getMethod("getGearManager").invoke(items);
            ClassLoader cl = items.getClass().getClassLoader();
            Class<? extends Enum> gt = (Class<? extends Enum>) Class.forName("com.theglitch.glitchitems.GearType", true, cl);
            Class<? extends Enum> rt = (Class<? extends Enum>) Class.forName("com.theglitch.glitchitems.Rarity", true, cl);
            Method m = gm.getClass().getMethod("generateGear", gt, rt);
            return (ItemStack) m.invoke(gm, Enum.valueOf(gt, type), Enum.valueOf(rt, rarity));
        } catch (Throwable t) {
            plugin.getLogger().warning("Gear hook failed for " + type + "/" + rarity + ": " + t);
            return null;
        }
    }

    boolean hasClass(UUID id) {
        Plugin classes = enabled("GlitchClasses");
        if (classes == null) return true; // nothing to pick — don't block the tutorial
        try {
            Object cm = classes.getClass().getMethod("getClassManager").invoke(classes);
            return (Boolean) cm.getClass().getMethod("hasClass", UUID.class).invoke(cm, id);
        } catch (Throwable t) {
            return true;
        }
    }

    /** Vault deposit (Shards). */
    boolean deposit(OfflinePlayer player, double amount) {
        try {
            for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
                if (!service.getName().equals("net.milkbowl.vault.economy.Economy")) continue;
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(service);
                if (rsp == null) return false;
                Object econ = rsp.getProvider();
                // Look methods up on the public Vault interface — the provider class itself may not be public
                Object resp = service.getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(econ, player, amount);
                return (Boolean) resp.getClass().getMethod("transactionSuccess").invoke(resp);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Shards reward failed: " + t);
        }
        return false;
    }
}
