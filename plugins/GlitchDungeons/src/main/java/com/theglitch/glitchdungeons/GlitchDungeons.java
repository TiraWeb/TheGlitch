package com.theglitch.glitchdungeons;

import com.theglitch.glitchdungeons.commands.DungeonAdminCommand;
import com.theglitch.glitchdungeons.commands.DungeonCommand;
import com.theglitch.glitchdungeons.commands.PartyCommand;
import com.theglitch.glitchdungeons.config.DungeonConfig;
import com.theglitch.glitchdungeons.gui.DungeonSelectGUI;
import com.theglitch.glitchdungeons.listeners.DungeonListener;
import com.theglitch.glitchdungeons.listeners.ExtractionListener;
import com.theglitch.glitchdungeons.listeners.InventoryListener;
import com.theglitch.glitchdungeons.managers.CooldownManager;
import com.theglitch.glitchdungeons.managers.DungeonManager;
import com.theglitch.glitchdungeons.managers.PartyManager;
import com.theglitch.glitchdungeons.managers.RewardManager;
import com.theglitch.glitchdungeons.managers.WaveManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class GlitchDungeons extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchDungeons instance;
    private DungeonConfig dungeonConfig;
    private PartyManager partyManager;
    private DungeonManager dungeonManager;
    private WaveManager waveManager;
    private CooldownManager cooldownManager;
    private RewardManager rewardManager;
    private DungeonSelectGUI selectGui;
    private ExtractionListener extractionListener;

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
        extractionListener = new ExtractionListener(this);
        getServer().getPluginManager().registerEvents(extractionListener, this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        selectGui = new DungeonSelectGUI(this);
        getServer().getPluginManager().registerEvents(selectGui, this);

        PluginCommand partyCmd = getCommand("party");
        if (partyCmd != null) partyCmd.setExecutor(new PartyCommand(this));
        PluginCommand pchatCmd = getCommand("pchat");
        if (pchatCmd != null) pchatCmd.setExecutor(new PartyCommand(this));
        PluginCommand dungeonCmd = getCommand("dungeon");
        if (dungeonCmd != null) dungeonCmd.setExecutor(new DungeonCommand(this));
        PluginCommand adminCmd = getCommand("dungeonadmin");
        if (adminCmd != null) adminCmd.setExecutor(new DungeonAdminCommand(this));
        PluginCommand uiCmd = getCommand("dungeonui");
        if (uiCmd != null) uiCmd.setExecutor(new DungeonUICommand(this));

        com.theglitch.glitchdungeons.ui.DungeonPanel.init(this);

        getLogger().info("GlitchDungeons enabled!");
    }

    @Override
    public void onDisable() {
        com.theglitch.glitchdungeons.ui.DungeonPanel.shutdown();
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
    public DungeonSelectGUI getSelectGui() { return selectGui; }
    public ExtractionListener getExtractionListener() { return extractionListener; }

    public static MiniMessage mm() {
        return MM;
    }
}
