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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class GlitchDeathRules extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchDeathRules instance;
    private RedZoneInvulnerability invulnerability;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    // Cached hot-path config — refreshed on reload
    private volatile Set<String> mercyWorlds = new HashSet<>();
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();
        cacheConfig();

        invulnerability = new RedZoneInvulnerability(this);

        Bukkit.getPluginManager().registerEvents(new DeathRulesListener(this), this);
        Bukkit.getPluginManager().registerEvents(invulnerability, this);

        getCommand("deathrules").setExecutor((CommandExecutor) this::onCommand);

        getLogger().info("GlitchDeathRules enabled (mercyWorlds=" + mercyWorlds + ").");
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
        messageCache.clear();
        for (String k : messagesConfig.getKeys(false)) {
            String v = messagesConfig.getString(k);
            if (v != null) messageCache.put(k, v);
        }
        for (String k : messagesConfig.getKeys(true)) {
            if (!messageCache.containsKey(k)) {
                String v = messagesConfig.getString(k);
                if (v != null) messageCache.put(k, v);
            }
        }
    }

    private void cacheConfig() {
        try {
            Set<String> set = new HashSet<>(getConfig().getStringList("mercy-worlds"));
            if (set.isEmpty()) {
                getLogger().warning("mercy-worlds empty — no world will have mercy rule.");
            }
            mercyWorlds = set;
        } catch (Exception e) {
            getLogger().warning("Failed to cache GlitchDeathRules config: " + e.getMessage());
            mercyWorlds = new HashSet<>(Set.of("glitch_red"));
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        loadMessages();
        cacheConfig();
        if (invulnerability != null) {
            invulnerability.reloadCache();
        }
        getLogger().info("GlitchDeathRules reloaded (mercyWorlds=" + mercyWorlds + ").");
    }

    public String getMessage(String key) {
        String c = messageCache.get(key);
        if (c != null) return c;
        return messagesConfig.getString(key, key);
    }

    public Component getComponent(String key) {
        return MM.deserialize(getMessage(key));
    }

    public Component getComponent(String key, String ph1, String v1) {
        return MM.deserialize(getMessage(key).replace(ph1, v1));
    }

    public Set<String> getMercyWorlds() {
        return mercyWorlds;
    }

    public boolean isMercyWorld(String world) {
        return mercyWorlds.contains(world);
    }

    public static MiniMessage mm() {
        return MM;
    }

    public static GlitchDeathRules getInstance() {
        return instance;
    }

    public RedZoneInvulnerability getInvulnerability() {
        return invulnerability;
    }
}
