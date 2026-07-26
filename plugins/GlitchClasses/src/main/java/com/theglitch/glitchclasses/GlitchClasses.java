package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public final class GlitchClasses extends JavaPlugin {

    private static GlitchClasses instance;
    private ClassManager classManager;
    private AbilityItemManager abilityItemManager;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();

        classManager = new ClassManager(this);
        abilityItemManager = new AbilityItemManager(this);

        Bukkit.getPluginManager().registerEvents(new ClassGUI(this, classManager), this);
        Bukkit.getPluginManager().registerEvents(new AbilityListener(this, classManager), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this, classManager, abilityItemManager), this);

        getCommand("class").setExecutor(new ClassCommand(this, classManager));
        getCommand("classadmin").setExecutor(new ClassAdminCommand(this, classManager));

        getLogger().info("GlitchClasses enabled — " + classManager.getPlayerCount() + " players loaded.");
    }

    @Override
    public void onDisable() {
        if (classManager != null) {
            classManager.shutdown();
        }
        instance = null;
        getLogger().info("GlitchClasses disabled.");
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
        getLogger().info("GlitchClasses reloaded.");
    }

    public String getMessage(String key) {
        return messagesConfig.getString("messages." + key, key);
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

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2, String ph3, String v3) {
        return MiniMessage.miniMessage().deserialize(
                getMessage(key).replace(ph1, v1).replace(ph2, v2).replace(ph3, v3));
    }

    public static GlitchClasses getInstance() {
        return instance;
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public AbilityItemManager getAbilityItemManager() {
        return abilityItemManager;
    }
}
