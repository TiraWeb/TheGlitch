package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GlitchRaid — Raid lifecycle manager (ROADMAP 5.9.3).
 * Timers, party assignment, post-raid summary screen, death recap, loot accounting.
 */
public final class GlitchRaid extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchRaid instance;
    private RaidManager raidManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        raidManager = new RaidManager(this);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new RaidListener(this, raidManager), this);
        // VelKoth bridge — only if VelKoth present, to avoid NoClassDefFoundError when hard import missing
        if (Bukkit.getPluginManager().getPlugin("VelKoth") != null) {
            try {
                Class<?> listenerClass = Class.forName("com.theglitch.glitchraid.RaidExtractionListener");
                Object listener = listenerClass.getDeclaredConstructor(GlitchRaid.class, RaidManager.class).newInstance(this, raidManager);
                Bukkit.getPluginManager().registerEvents((org.bukkit.event.Listener) listener, this);
                getLogger().info("VelKoth bridge registered — KothWinEvent will trigger raid extraction.");
            } catch (ClassNotFoundException e) {
                getLogger().warning("VelKoth found but RaidExtractionListener class not found: " + e.getMessage());
            } catch (Exception e) {
                getLogger().warning("Failed to register VelKoth bridge: " + e.getMessage());
            }
        } else {
            getLogger().info("VelKoth not found — raid extraction will use world-change fallback only.");
        }

        // Register commands
        if (getCommand("raid") != null) {
            getCommand("raid").setExecutor(new RaidCommand(this, raidManager));
        } else {
            getLogger().warning("Command 'raid' not found in plugin.yml — check registration.");
        }
        if (getCommand("raidadmin") != null) {
            getCommand("raidadmin").setExecutor(new RaidAdminCommand(this, raidManager));
        } else {
            getLogger().warning("Command 'raidadmin' not found in plugin.yml — check registration.");
        }

        // PlaceholderAPI expansion (optional, reflection-based to avoid hard compile dep)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                RaidExpansion expansion = new RaidExpansion(this, raidManager);
                boolean registered = expansion.register();
                if (registered) {
                    getLogger().info("PlaceholderAPI expansion registered (%glitchraid_*)");
                } else {
                    // MVP: reflection-based check already logged availability
                    getLogger().info("PlaceholderAPI found — placeholders %glitchraid_in_raid%, %glitchraid_time_left%, %glitchraid_loot% available via RaidExpansion.");
                }
            } catch (Exception e) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            }
        } else {
            getLogger().info("PlaceholderAPI not found — placeholders disabled.");
        }

        // VaultUnlocked check (MVP: optional, not required)
        if (Bukkit.getPluginManager().getPlugin("VaultUnlocked") == null
                && Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("VaultUnlocked not found — economy/payout features will be disabled (MVP).");
        }

        getLogger().info("GlitchRaid enabled — Raid lifecycle manager ready (duration=" + raidManager.getDurationSeconds() + "s, payout=" + raidManager.getPayoutMultiplier() + ").");
    }

    @Override
    public void onDisable() {
        if (raidManager != null) {
            raidManager.shutdown();
        }
        instance = null;
        getLogger().info("GlitchRaid disabled.");
    }

    /**
     * Reloads config and cached values.
     */
    public void reloadPlugin() {
        reloadConfig();
        if (raidManager != null) {
            raidManager.reload();
        }
        getLogger().info("GlitchRaid configuration reloaded.");
    }

    public RaidManager getRaidManager() {
        return raidManager;
    }

    public static GlitchRaid getInstance() {
        return instance;
    }

    public static MiniMessage mm() {
        return MM;
    }
}
