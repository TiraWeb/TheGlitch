package com.theglitch.glitchclasses;

import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Manages all player class data — YAML-based persistent storage.
 * Each player gets their own file under plugins/GlitchClasses/players/
 */
public final class ClassManager {

    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^a-z]");
    private static final int BASE_HEALTH = 20;
    private static final int HEALTH_PER_LEVEL = 2;

    private final GlitchClasses plugin;
    private final Map<UUID, ClassData> players = new ConcurrentHashMap<>();
    private final Path playerDir;
    private volatile int cachedMaxLevel = 10;
    private volatile int cachedResetCost = 500;

    public ClassManager(GlitchClasses plugin) {
        this.plugin = plugin;
        this.playerDir = plugin.getDataFolder().toPath().resolve("players");
        try {
            Files.createDirectories(playerDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create players directory", e);
        }
        loadAllPlayers();
        reloadCaches();
    }

    public void reloadCaches() {
        cachedMaxLevel = plugin.getConfig().getInt("max-level", 10);
        cachedResetCost = plugin.getConfig().getInt("reset-cost", 500);
    }

    /**
     * Get or create class data for a player.
     */
    public ClassData getClassData(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> new ClassData(id, "none", 0, 0));
    }

    /**
     * Sanitize any in-memory data that might have bad class names.
     */
    public void sanitizeAll() {
        for (Map.Entry<UUID, ClassData> entry : players.entrySet()) {
            ClassData d = entry.getValue();
            String clean = sanitizeClassName(d.className());
            if (!clean.equals(d.className())) {
                ClassData fixed = new ClassData(d.uuid(), clean, d.level(), d.xp());
                entry.setValue(fixed);
                saveToFile(d.uuid(), fixed);
                plugin.getLogger().info("Sanitized class name for " + d.uuid() + ": '" + d.className() + "' -> '" + clean + "'");
            }
        }
    }

    /**
     * Set a player's class.
     */
    public void setClass(UUID uuid, String className) {
        String sanitized = sanitizeClassName(className);
        ClassData data = getClassData(uuid);
        ClassData updated = new ClassData(uuid, sanitized, data.level(), data.xp());
        players.put(uuid, updated);
        saveToFile(uuid, updated);
    }

    /**
     * Set a player's class level.
     */
    public void setLevel(UUID uuid, int level) {
        ClassData data = getClassData(uuid);
        ClassData updated = new ClassData(uuid, data.className(), level, data.xp());
        players.put(uuid, updated);
        saveToFile(uuid, updated);
    }

    /**
     * Add experience to a player. Level up if threshold reached.
     */
    public boolean addXp(UUID uuid, int amount) {
        ClassData data = getClassData(uuid);
        if (data.className().equals("none") || data.level() >= getMaxLevel()) return false;

        int newXp = data.xp() + amount;
        int xpNeeded = getXpForLevel(data.level() + 1);
        int newLevel = data.level();
        boolean leveledUp = false;

        while (newXp >= xpNeeded && newLevel < getMaxLevel()) {
            newXp -= xpNeeded;
            newLevel++;
            xpNeeded = getXpForLevel(newLevel + 1);
            leveledUp = true;
        }

        ClassData updated = new ClassData(uuid, data.className(), newLevel, newXp);
        players.put(uuid, updated);
        saveToFile(uuid, updated);
        return leveledUp;
    }

    /**
     * Check if a player has a class.
     */
    public boolean hasClass(UUID uuid) {
        ClassData data = players.get(uuid);
        return data != null && !data.className().equals("none");
    }

    /**
     * Reset a player's class to none.
     */
    public void resetClass(UUID uuid) {
        ClassData updated = new ClassData(uuid, "none", 0, 0);
        players.put(uuid, updated);
        saveToFile(uuid, updated);
    }

    /**
     * Get XP needed for a given level.
     */
    public int getXpForLevel(int level) {
        return 100 + (level - 1) * 50;
    }

    /**
     * Get max level from config.
     */
    public int getMaxLevel() {
        return cachedMaxLevel;
    }

    /**
     * Get reset cost from config.
     */
    public int getResetCost() {
        return cachedResetCost;
    }

    public static int getMaxHealthForLevel(int level) {
        return BASE_HEALTH + level * HEALTH_PER_LEVEL;
    }

    public void applyMaxHealth(Player player, int level) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(getMaxHealthForLevel(level));
    }

    public void applyMaxHealth(Player player) {
        ClassData data = getClassData(player.getUniqueId());
        applyMaxHealth(player, data.level());
    }

    /**
     * Get upgrade cost for a specific level.
     */
    public int getUpgradeCost(int currentLevel) {
        return 50 + (currentLevel * 50);
    }

    /**
     * Get the number of loaded players.
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * Get all class names from config.
     */
    public Set<String> getClassNames() {
        org.bukkit.configuration.ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("classes");
        return section == null ? Set.of() : section.getKeys(false);
    }

    /**
     * Get all class data entries.
     */
    public Collection<ClassData> getAllPlayers() {
        return players.values();
    }

    private void loadAllPlayers() {
        if (!Files.exists(playerDir)) return;

        try (var stream = Files.list(playerDir)) {
            stream.filter(p -> p.toString().endsWith(".yml")).forEach(path -> {
                try {
                    UUID uuid = UUID.fromString(path.getFileName().toString().replace(".yml", ""));
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());

                    String className = sanitizeClassName(yaml.getString("class", "none"));
                    int level = yaml.getInt("level", 0);
                    int xp = yaml.getInt("xp", 0);

                    ClassData data = new ClassData(uuid, className, level, xp);
                    players.put(uuid, data);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load player: " + path.getFileName(), e);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to list player files", e);
        }
    }

    private void saveToFile(UUID uuid, ClassData data) {
        Path file = playerDir.resolve(uuid.toString() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("uuid", uuid.toString());
        yaml.set("class", data.className());
        yaml.set("level", data.level());
        yaml.set("xp", data.xp());

        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player data for " + uuid, e);
        }
    }

    public void shutdown() {
        players.forEach(this::saveToFile);
    }

    private String sanitizeClassName(String className) {
        if (className == null) return "none";
        return SANITIZE_PATTERN.matcher(className.toLowerCase(java.util.Locale.ROOT)).replaceAll("");
    }
}
