package com.theglitch.glitchstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * GlitchStash — Extraction vault system for The Glitch.
 * Saves player inventory on extraction win, provides retrieval via /stash.
 */
public final class GlitchStash extends JavaPlugin {

    private static GlitchStash instance;
    private StashManager stashManager;
    private ExtractionVariantManager variantManager;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();

        stashManager = new StashManager(this);
        variantManager = new ExtractionVariantManager(this);

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(new ExtractionListener(this, stashManager), this);
        Bukkit.getPluginManager().registerEvents(new StashGUI(), this);
        Bukkit.getPluginManager().registerEvents(new ExtractionVariantListener(this, variantManager), this);

        // Register commands
        getCommand("stash").setExecutor(new StashCommand(this, stashManager));
        getCommand("stashtp").setExecutor(new StashCommand(this, stashManager));
        getCommand("stashadmin").setExecutor(new StashAdminCommand(this, stashManager));
        getCommand("extractadmin").setExecutor(new ExtractionVariantCommand(this, variantManager));

        getLogger().info("GlitchStash enabled — " + stashManager.getStashCount() + " stashes loaded.");
    }

    @Override
    public void onDisable() {
        if (stashManager != null) {
            stashManager.shutdown();
        }
        instance = null;
        getLogger().info("GlitchStash disabled.");
    }

    private void loadMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void reloadPlugin() {
        reloadConfig();
        loadMessages();
        if (variantManager != null) {
            variantManager.reload();
        }
        getLogger().info("GlitchStash reloaded.");
    }

    public String getMessage(String key) {
        String msg = messagesConfig.getString(key, key);
        return msg;
    }

    public Component getComponent(String key) {
        return MiniMessage.miniMessage().deserialize(getMessage(key));
    }

    public Component getComponent(String key, String placeholder, String value) {
        return MiniMessage.miniMessage().deserialize(
                getMessage(key).replace(placeholder, value));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2) {
        return MiniMessage.miniMessage().deserialize(
                getMessage(key).replace(ph1, v1).replace(ph2, v2));
    }

    public static GlitchStash getInstance() {
        return instance;
    }

    public StashManager getStashManager() {
        return stashManager;
    }

    public ExtractionVariantManager getExtractionVariantManager() {
        return variantManager;
    }
}
