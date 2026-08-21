package com.theglitch.glitchloot;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GlitchLoot — Smart loot system (ROADMAP 5.9.7).
 * Adaptive drop rates, contextual loot, item power budget, anti-funneling.
 */
public final class GlitchLoot extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private LootEngine lootEngine;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        lootEngine = new LootEngine(this);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new LootListener(this, lootEngine), this);

        // Register commands
        PluginCommand command = getCommand("glitchloot");
        if (command != null) {
            GlitchLootCommand executor = new GlitchLootCommand(this, lootEngine);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Command 'glitchloot' not found in plugin.yml — check registration.");
        }

        getLogger().info("GlitchLoot enabled — adaptive=" + lootEngine.isAdaptiveEnabled()
                + ", maxBonus=" + lootEngine.getMaxBonusPercent() + "%"
                + ", powerBudget=" + lootEngine.isPowerBudgetEnabled()
                + " (" + lootEngine.powerRemaining() + "/" + lootEngine.getMaxPowerPerHour() + ")"
                + ", antiFunnel=" + lootEngine.isAntiFunnelEnabled()
                + " (" + lootEngine.getCooldownSeconds() + "s)"
                + ", worlds=" + lootEngine.getEnabledWorlds() + ".");
    }

    @Override
    public void onDisable() {
        if (lootEngine != null) {
            lootEngine.shutdown();
        }
        getLogger().info("GlitchLoot disabled.");
    }

    /**
     * Reloads config and all cached values.
     */
    public void reloadPlugin() {
        reloadConfig();
        if (messages != null) {
            messages.reload();
        }
        if (lootEngine != null) {
            lootEngine.reload();
        }
        getLogger().info("GlitchLoot configuration reloaded.");
    }

    public LootEngine getLootEngine() {
        return lootEngine;
    }

    public Messages getMessages() {
        return messages;
    }

    public static MiniMessage mm() {
        return MM;
    }
}
