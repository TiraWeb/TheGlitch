package com.theglitch.glitchdungeons.config;

import com.theglitch.glitchdungeons.models.DungeonSlot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class DungeonConfig {
    private FileConfiguration config;
    private final Map<Integer, DungeonSlot> slots;
    private final Map<Integer, DungeonTierConfig> dungeons;

    public DungeonConfig(FileConfiguration config) {
        this.config = config;
        this.slots = new HashMap<>();
        this.dungeons = new HashMap<>();
        loadSlots();
        loadDungeons();
    }

    private void loadSlots() {
        ConfigurationSection slotSection = config.getConfigurationSection("slots");
        if (slotSection == null) return;
        for (String key : slotSection.getKeys(false)) {
            int id = Integer.parseInt(key);
            int x = slotSection.getInt(key + ".x");
            int z = slotSection.getInt(key + ".z");
            slots.put(id, new DungeonSlot(id, x, z));
        }
    }

    private void loadDungeons() {
        ConfigurationSection dungeonSection = config.getConfigurationSection("dungeons");
        if (dungeonSection == null) return;
        for (String key : dungeonSection.getKeys(false)) {
            int tier = Integer.parseInt(key);
            ConfigurationSection tierSection = dungeonSection.getConfigurationSection(key);
            if (tierSection == null) continue;
            String name = tierSection.getString("name", "Unknown");
            int maxTime = tierSection.getInt("max-time", 600);
            int waveCount = tierSection.getConfigurationSection("waves") != null
                ? tierSection.getConfigurationSection("waves").getKeys(false).size() : 0;
            dungeons.put(tier, new DungeonTierConfig(tier, name, maxTime, waveCount, tierSection));
        }
    }

    public Map<Integer, DungeonSlot> getSlots() { return slots; }
    public DungeonSlot getSlot(int id) { return slots.get(id); }
    public Map<Integer, DungeonTierConfig> getDungeons() { return dungeons; }
    public DungeonTierConfig getDungeon(int tier) { return dungeons.get(tier); }
    public int getPrepTime() { return config.getInt("prep-time", 30); }
    public int getExtractionTime() { return config.getInt("extraction-time", 30); }
    public int getWaveDelay() { return config.getInt("wave-delay", 10); }
    public int getMaxPartySize() { return config.getInt("max-party-size", 4); }
    public int getCooldownPerDungeon() { return config.getInt("cooldown-per-dungeon", 600); }
    public boolean isCooldownPerPlayer() { return config.getBoolean("cooldown-per-player", true); }
    public String getMessage(String key) { return config.getString("messages." + key, "&cMessage not found: " + key); }
    public String getStagingWorld() { return "glitch_pve"; }

    public int getStagingX() { return config.getInt("staging.x", 0); }
    public int getStagingY() { return config.getInt("staging.y", 65); }
    public int getStagingZ() { return config.getInt("staging.z", 0); }

    public int getHubX() { return config.getInt("hub-spawn.x", 0); }
    public int getHubY() { return config.getInt("hub-spawn.y", 65); }
    public int getHubZ() { return config.getInt("hub-spawn.z", 0); }

    public void reload(FileConfiguration newConfig) {
        this.config = newConfig;
        this.slots.clear();
        this.dungeons.clear();
        loadSlots();
        loadDungeons();
    }

    public static class DungeonTierConfig {
        private final int tier;
        private final String name;
        private final int maxTime;
        private final int waveCount;
        private final ConfigurationSection section;

        public DungeonTierConfig(int tier, String name, int maxTime, int waveCount, ConfigurationSection section) {
            this.tier = tier;
            this.name = name;
            this.maxTime = maxTime;
            this.waveCount = waveCount;
            this.section = section;
        }

        public int getTier() { return tier; }
        public String getName() { return name; }
        public int getMaxTime() { return maxTime; }
        public int getWaveCount() { return waveCount; }
        public ConfigurationSection getSection() { return section; }
    }
}
