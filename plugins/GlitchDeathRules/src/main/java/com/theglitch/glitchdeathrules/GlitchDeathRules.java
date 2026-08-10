package com.theglitch.glitchdeathrules;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class GlitchDeathRules extends JavaPlugin {

    private static GlitchDeathRules instance;
    private RedZoneInvulnerability invulnerability;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();

        invulnerability = new RedZoneInvulnerability(this);

        Bukkit.getPluginManager().registerEvents(new DeathRulesListener(this), this);
        Bukkit.getPluginManager().registerEvents(invulnerability, this);

        getCommand("deathrules").setExecutor((CommandExecutor) this::onCommand);

        getLogger().info("GlitchDeathRules enabled.");
    }

    @Override
    public void onDisable() {
        if (invulnerability != null) {
            invulnerability.shutdown();
        }
        instance = null;
        getLogger().info("GlitchDeathRules disabled.");
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadPlugin();
            sender.sendMessage(getComponent("reloaded"));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /deathrules reload"));
        return true;
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
        getLogger().info("GlitchDeathRules reloaded.");
    }

    public String getMessage(String key) {
        return messagesConfig.getString(key, key);
    }

    public Component getComponent(String key) {
        return MiniMessage.miniMessage().deserialize(getMessage(key));
    }

    public Component getComponent(String key, String ph1, String v1) {
        return MiniMessage.miniMessage().deserialize(getMessage(key).replace(ph1, v1));
    }

    public static GlitchDeathRules getInstance() {
        return instance;
    }

    public RedZoneInvulnerability getInvulnerability() {
        return invulnerability;
    }
}
