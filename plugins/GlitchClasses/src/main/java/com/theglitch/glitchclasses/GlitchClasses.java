package com.theglitch.glitchclasses;

import com.theglitch.glitchclasses.ui.DialogBridge;
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

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchClasses instance;
    private ClassManager classManager;
    private ClassGUI classGUI;
    private StarterKit starterKit;
    private AbilityListener abilityListener;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();

        classManager = new ClassManager(this);
        classManager.sanitizeAll();
        starterKit = new StarterKit(this);

        classGUI = new ClassGUI(this, classManager);
        abilityListener = new AbilityListener(this, classManager);
        abilityListener.startTickers();
        Bukkit.getPluginManager().registerEvents(classGUI, this);
        Bukkit.getPluginManager().registerEvents(abilityListener, this);

        if (getConfig().getBoolean("modern-ui.enabled", true)) {
            getLogger().info(DialogBridge.runtimeSummary());
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ClassExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered (%glitchclasses_*%).");
        }

        getCommand("class").setExecutor(new ClassCommand(this, classManager));
        getCommand("classadmin").setExecutor(new ClassAdminCommand(this, classManager));
        if (getCommand("classui") != null) {
            getCommand("classui").setExecutor(new ClassUICommand(this));
        }

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
        if (classManager != null) classManager.reloadCaches();
        if (abilityListener != null) abilityListener.reloadConfig();
        if (classGUI != null) classGUI.reloadConfig();
        if (starterKit != null) starterKit.reloadConfig();
        getLogger().info("GlitchClasses reloaded.");
    }

    public String getMessage(String key) {
        return messagesConfig.getString(key, key);
    }

    public Component getComponent(String key) {
        return MM.deserialize(getMessage(key));
    }

    public Component getComponent(String key, String placeholder, String value) {
        return MM.deserialize(getMessage(key).replace(placeholder, value));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2) {
        return MM.deserialize(getMessage(key).replace(ph1, v1).replace(ph2, v2));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2, String ph3, String v3) {
        return MM.deserialize(getMessage(key).replace(ph1, v1).replace(ph2, v2).replace(ph3, v3));
    }

    public MiniMessage mm() { return MM; }

    public static GlitchClasses getInstance() {
        return instance;
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public ClassGUI getClassGUI() {
        return classGUI;
    }

    public StarterKit getStarterKit() {
        return starterKit;
    }
}
