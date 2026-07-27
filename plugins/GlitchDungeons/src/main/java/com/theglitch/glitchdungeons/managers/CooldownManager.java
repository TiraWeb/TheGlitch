package com.theglitch.glitchdungeons.managers;

import com.theglitch.glitchdungeons.GlitchDungeons;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {
    private final GlitchDungeons plugin;
    private final File cooldownFile;
    private final Map<String, Long> cooldowns; // "playerUuid:dungeonTier" -> expiry timestamp

    public CooldownManager(GlitchDungeons plugin) {
        this.plugin = plugin;
        this.cooldownFile = new File(plugin.getDataFolder(), "cooldowns.yml");
        this.cooldowns = new HashMap<>();
        loadCooldowns();
    }

    private void loadCooldowns() {
        if (!cooldownFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cooldownFile);
        for (String key : yaml.getKeys(false)) {
            cooldowns.put(key, yaml.getLong(key));
        }
    }

    public void saveCooldowns() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(cooldownFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save cooldowns: " + e.getMessage());
        }
    }

    public boolean isOnCooldown(UUID playerUuid, int tier) {
        String key = playerUuid + ":" + tier;
        Long expiry = cooldowns.get(key);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(key);
            return false;
        }
        return true;
    }

    public long getRemainingCooldown(UUID playerUuid, int tier) {
        String key = playerUuid + ":" + tier;
        Long expiry = cooldowns.get(key);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(key);
            return 0;
        }
        return remaining / 1000; // return seconds
    }

    public void setCooldown(UUID playerUuid, int tier) {
        String key = playerUuid + ":" + tier;
        long cooldownSeconds = plugin.getDungeonConfig().getCooldownPerDungeon();
        cooldowns.put(key, System.currentTimeMillis() + (cooldownSeconds * 1000));
        saveCooldowns();
    }

    public void resetAll() {
        cooldowns.clear();
        saveCooldowns();
    }
}
