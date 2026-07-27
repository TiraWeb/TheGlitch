package com.theglitch.glitchdungeons;

import com.theglitch.glitchdungeons.commands.DungeonAdminCommand;
import com.theglitch.glitchdungeons.commands.DungeonCommand;
import com.theglitch.glitchdungeons.commands.PartyCommand;
import com.theglitch.glitchdungeons.config.DungeonConfig;
import com.theglitch.glitchdungeons.listeners.DungeonListener;
import com.theglitch.glitchdungeons.listeners.ExtractionListener;
import com.theglitch.glitchdungeons.listeners.InventoryListener;
import com.theglitch.glitchdungeons.managers.CooldownManager;
import com.theglitch.glitchdungeons.managers.DungeonManager;
import com.theglitch.glitchdungeons.managers.PartyManager;
import com.theglitch.glitchdungeons.managers.RewardManager;
import com.theglitch.glitchdungeons.managers.WaveManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class GlitchDungeons extends JavaPlugin {
    private static GlitchDungeons instance;
    private DungeonConfig dungeonConfig;
    private PartyManager partyManager;
    private DungeonManager dungeonManager;
    private WaveManager waveManager;
    private CooldownManager cooldownManager;
    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        dungeonConfig = new DungeonConfig(getConfig());

        partyManager = new PartyManager(this);
        cooldownManager = new CooldownManager(this);
        rewardManager = new RewardManager(this);
        waveManager = new WaveManager(this);
        dungeonManager = new DungeonManager(this);

        getServer().getPluginManager().registerEvents(new DungeonListener(this), this);
        getServer().getPluginManager().registerEvents(new ExtractionListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new com.theglitch.glitchdungeons.gui.DungeonSelectGUI(this), this);

        PluginCommand partyCmd = getCommand("party");
        if (partyCmd != null) partyCmd.setExecutor(new PartyCommand(this));
        PluginCommand pchatCmd = getCommand("pchat");
        if (pchatCmd != null) pchatCmd.setExecutor(new PartyCommand(this));
        PluginCommand dungeonCmd = getCommand("dungeon");
        if (dungeonCmd != null) dungeonCmd.setExecutor(new DungeonCommand(this));
        PluginCommand adminCmd = getCommand("dungeonadmin");
        if (adminCmd != null) adminCmd.setExecutor(new DungeonAdminCommand(this));

        getLogger().info("GlitchDungeons enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("GlitchDungeons disabled.");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (dungeonConfig != null) {
            dungeonConfig.reload(getConfig());
        }
    }

    public static GlitchDungeons getInstance() { return instance; }
    public DungeonConfig getDungeonConfig() { return dungeonConfig; }
    public PartyManager getPartyManager() { return partyManager; }
    public DungeonManager getDungeonManager() { return dungeonManager; }
    public WaveManager getWaveManager() { return waveManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public RewardManager getRewardManager() { return rewardManager; }
}
