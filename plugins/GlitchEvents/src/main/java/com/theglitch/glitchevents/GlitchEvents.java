package com.theglitch.glitchevents;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GlitchEvents — Dynamic world events (ROADMAP 5.9.6).
 * Server-wide broadcasts, timed extraction windows, roaming bosses, supply drops.
 */
public final class GlitchEvents extends JavaPlugin {

    private static GlitchEvents instance;

    private EventManager eventManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        eventManager = new EventManager(this);

        Bukkit.getPluginManager().registerEvents(new EventListener(this), this);

        if (getCommand("glitchevents") != null) {
            GlitchEventsCommand command = new GlitchEventsCommand(this, eventManager);
            getCommand("glitchevents").setExecutor(command);
            getCommand("glitchevents").setTabCompleter(command);
        } else {
            getLogger().warning("Command 'glitchevents' not found in plugin.yml — check registration.");
        }

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            getLogger().info("MythicMobs found — roaming bosses enabled.");
        } else {
            getLogger().info("MythicMobs not found — roaming bosses disabled.");
        }

        getLogger().info("GlitchEvents enabled — dynamic world events ready (ROADMAP 5.9.6).");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.cancelAll();
        }
        instance = null;
        getLogger().info("GlitchEvents disabled.");
    }

    /**
     * Reloads config and re-caches all manager values (reschedules auto-events).
     */
    public void reloadPlugin() {
        reloadConfig();
        if (eventManager != null) {
            eventManager.reload();
        }
        getLogger().info("GlitchEvents configuration reloaded.");
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public static GlitchEvents getInstance() {
        return instance;
    }
}
