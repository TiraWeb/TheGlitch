package com.theglitch.glitchitems;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class GlitchItems extends JavaPlugin {

    private static GlitchItems instance;
    private GearManager gearManager;
    private ResidualGlitchManager glitchManager;
    private IdentifyManager identifyManager;
    private ContainerManager containerManager;
    private ScatterManager scatterManager;
    private Economy economy;
    private boolean economyLookupDone;
    private long economyLookupFailedAt;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        economy = getEconomy();
        if (economy == null) {
            getLogger().warning("No Vault economy provider registered — identify fees disabled until an economy plugin is present.");
        }

        gearManager = new GearManager(this);
        glitchManager = new ResidualGlitchManager(this);
        identifyManager = new IdentifyManager(this, gearManager);
        containerManager = new ContainerManager(this);
        // Automatic loot scatter — RED WORLD only, sparse, on solid ground (see ScatterManager.java:1)
        scatterManager = new ScatterManager(this, containerManager);

        Bukkit.getPluginManager().registerEvents(new CombatListener(this, gearManager, glitchManager), this);
        Bukkit.getPluginManager().registerEvents(new ContainerListener(containerManager), this);
        Bukkit.getPluginManager().registerEvents(new ConsumableListener(this, gearManager, identifyManager), this);
        glitchManager.start();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GlitchExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered (%glitchitems_*%).");
        }

        getCommand("identify").setExecutor(new IdentifyCommand(identifyManager));
        getCommand("glitchitems").setExecutor(new GlitchItemsCommand(this, gearManager, glitchManager));
        getCommand("glitchcontainers").setExecutor(new ContainerCommand(this, containerManager, scatterManager));
        getCommand("armor").setExecutor(new ArmorCommand(this, gearManager));

        // Late-hook for GlitchStash if it loads after GlitchItems (soft-depend)
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if ("GlitchStash".equals(event.getPlugin().getName()) && scatterManager != null) {
                    getLogger().info("GlitchStash detected late — re-attempting scatter cycle hook.");
                    try {
                        scatterManager.registerCycleHook();
                        // Also re-evaluate scheduler: when Stash appears, disable fixed-rate timer
                        // so the 31m cycle (30m+5s event) is the single source of truth.
                        scatterManager.startScheduler();
                    } catch (Exception ex) { getLogger().warning("Late hook failed: " + ex.getMessage()); }
                }
            }
        }, this);

        getLogger().info("GlitchItems enabled — scatter every " + scatterManager.getIntervalMinutes() + "m in " + scatterManager.getEnabledWorlds() + " (" + scatterManager.getTrackedCount() + " tracked).");
    }

    @Override
    public void onDisable() {
        if (scatterManager != null) {
            try { scatterManager.shutdown(); } catch (Exception e) { getLogger().warning("Error shutting down ScatterManager: " + e.getMessage()); }
        }
        if (glitchManager != null) {
            glitchManager.shutdown();
        }
        instance = null;
        getLogger().info("GlitchItems disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        invalidateEconomyCache();
        if (gearManager != null) gearManager.reload();
        if (glitchManager != null) glitchManager.reload();
        if (identifyManager != null) identifyManager.reload();
        if (containerManager != null) {
            containerManager.reload();
        }
        if (scatterManager != null) {
            scatterManager.reload();
            // Restart scheduler with new interval
            scatterManager.startScheduler();
        }
        getLogger().info("GlitchItems reloaded.");
    }

    public void invalidateEconomyCache() {
        economy = null;
        economyLookupDone = false;
        economyLookupFailedAt = 0L;
    }

    public static GlitchItems getInstance() {
        return instance;
    }

    public GearManager getGearManager() {
        return gearManager;
    }

    public ResidualGlitchManager getGlitchManager() {
        return glitchManager;
    }

    public IdentifyManager getIdentifyManager() {
        return identifyManager;
    }

    public ContainerManager getContainerManager() {
        return containerManager;
    }

    public ScatterManager getScatterManager() {
        return scatterManager;
    }

    /**
     * Folia-safe entry for extraction hook (GlitchStash AutoExtractScheduler reflectively probes
     * GlitchItems for scatter methods). Supports multiple probed names.
     */
    public void scatter() { if (scatterManager != null) scatterManager.scatterNow(); }
    public void scatterLoot() { if (scatterManager != null) scatterManager.scatterNow(); }
    public void onCycleEnd() { if (scatterManager != null) scatterManager.scatterNow(); }
    public void handleCycleEnd() { if (scatterManager != null) scatterManager.scatterNow(); }
    public void doScatter() { if (scatterManager != null) scatterManager.scatterNow(); }

    public Economy getEconomy() {
        if (economyLookupDone) return economy;
        long now = System.currentTimeMillis();
        if (now - economyLookupFailedAt < 5000L) return economy;
        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (provider != null) {
            economy = provider.getProvider();
            if (economy != null) {
                getLogger().info("Economy provider found: " + economy.getName());
                economyLookupDone = true;
                return economy;
            }
        }
        economyLookupFailedAt = now;
        return economy;
    }
}
