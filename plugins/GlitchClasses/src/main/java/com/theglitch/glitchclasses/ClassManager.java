package com.theglitch.glitchclasses;

import com.theglitch.common.AtomicFiles;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Manages all player class data — YAML-based persistent storage.
 * Each player gets their own file under plugins/GlitchClasses/players/
 * <p>
 * Persistence is async + atomic: main-thread callers build a YamlConfiguration
 * snapshot and schedule an async write that saves to a temp file then atomically
 * moves it over the target. shutdown()/saveAll() perform synchronous atomic
 * writes to guarantee flush before disable.
 */
public final class ClassManager {

    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^a-z]");
    private static final int BASE_HEALTH = 20;
    private static final int HEALTH_PER_LEVEL = 2;

    private final GlitchClasses plugin;
    private final Map<UUID, ClassData> players = new ConcurrentHashMap<>();
    /** Per-class progress (class -> {level, xp}) so switching back restores it (2026-10-01). */
    private final Map<UUID, Map<String, int[]>> savedProgress = new ConcurrentHashMap<>();
    private final Path playerDir;
    private volatile int cachedMaxLevel = 10;
    private volatile int cachedResetCost = 500;
    // Async write coalescing: one in-flight write per player, draining the
    // latest snapshot, so independent mutations can never land out of order.
    private final Set<UUID> writePending = ConcurrentHashMap.newKeySet();
    private final Map<UUID, YamlConfiguration> latestSnapshot = new ConcurrentHashMap<>();

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
        ClassData known = players.get(uuid);
        if (known != null) return known;
        // Rogue Raider bots (Citizens NPCs) are Players too — never give them stored class data.
        // Entity lookups are main-thread only; async callers (TAB/PAPI placeholders) only ask about real players.
        if (org.bukkit.Bukkit.isPrimaryThread()
                && com.theglitch.common.Bots.isBot(org.bukkit.Bukkit.getEntity(uuid))) return new ClassData(uuid, "none", 0, 0);
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
     * Set a player's class. Each class keeps its own level/XP: the current
     * class's progress is stored and the target class resumes where it was
     * left (a class never played starts at 0). The paid reset (resetClass)
     * still wipes the current class.
     */
    public void setClass(UUID uuid, String className) {
        String sanitized = sanitizeClassName(className);
        ClassData data = getClassData(uuid);
        boolean changed = !data.className().equals(sanitized);
        Map<String, int[]> saved = savedProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (changed && !data.className().equals("none")) {
            saved.put(data.className(), new int[]{data.level(), data.xp()});
        }
        int[] target = changed ? saved.getOrDefault(sanitized, new int[]{0, 0}) : new int[]{data.level(), data.xp()};
        ClassData updated = new ClassData(uuid, sanitized, target[0], target[1]);
        players.put(uuid, updated);
        saveToFile(uuid, updated);
    }

    /** Saved {level, xp} for a class the player isn't currently using ({0,0} if never played). */
    public int[] savedProgress(UUID uuid, String className) {
        Map<String, int[]> saved = savedProgress.get(uuid);
        int[] p = saved == null ? null : saved.get(sanitizeClassName(className));
        return p == null ? new int[]{0, 0} : p.clone();
    }

    private void writeProgress(YamlConfiguration yaml, UUID uuid, ClassData data) {
        Map<String, int[]> saved = savedProgress.get(uuid);
        if (saved != null) {
            for (Map.Entry<String, int[]> e : saved.entrySet()) {
                if (e.getKey().equals(data.className())) continue;
                yaml.set("progress." + e.getKey() + ".level", e.getValue()[0]);
                yaml.set("progress." + e.getKey() + ".xp", e.getValue()[1]);
            }
        }
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
        Map<String, int[]> saved = savedProgress.get(uuid);
        if (saved != null) saved.remove(getClassData(uuid).className());
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
                    var progress = yaml.getConfigurationSection("progress");
                    if (progress != null) {
                        Map<String, int[]> saved = new ConcurrentHashMap<>();
                        for (String cls : progress.getKeys(false)) {
                            saved.put(sanitizeClassName(cls), new int[]{
                                    progress.getInt(cls + ".level", 0), progress.getInt(cls + ".xp", 0)});
                        }
                        savedProgress.put(uuid, saved);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load player: " + path.getFileName(), e);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to list player files", e);
        }
    }

    private void saveToFile(UUID uuid, ClassData data) {
        // Serialize the snapshot on the calling (main) thread — immutable
        // state, single source of truth. The async task only writes these.
        Path file = playerDir.resolve(uuid.toString() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", uuid.toString());
        yaml.set("class", data.className());
        yaml.set("level", data.level());
        yaml.set("xp", data.xp());
        writeProgress(yaml, uuid, data);

        latestSnapshot.put(uuid, yaml);
        // Only one write in flight per player; it drains the latest snapshot
        // so the last scheduled write always persists the newest state.
        if (writePending.add(uuid)) {
            scheduleWrite(uuid, file);
        }
    }

    private void scheduleWrite(UUID uuid, Path file) {
        try {
            // Paper async scheduler (1.20+)
            Bukkit.getAsyncScheduler().runNow(plugin, task -> drainWrites(uuid, file));
        } catch (Throwable t) {
            // Fallback to Bukkit scheduler for compatibility / unit tests
            try {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> drainWrites(uuid, file));
            } catch (Throwable t2) {
                // Last resort: synchronous atomic save (e.g. scheduler shut down during disable)
                drainWrites(uuid, file);
                plugin.getLogger().log(Level.WARNING, "Async scheduler unavailable, saved synchronously for " + uuid, t2);
            }
        }
    }

    private void drainWrites(UUID uuid, Path file) {
        try {
            YamlConfiguration snapshot;
            while ((snapshot = latestSnapshot.remove(uuid)) != null) {
                AtomicFiles.save(snapshot, file, plugin.getLogger());
            }
        } finally {
            writePending.remove(uuid);
        }
        // A snapshot may have raced in while the flag was still held — its
        // caller could not schedule a write, so claim and flush it here.
        if (writePending.add(uuid)) {
            if (latestSnapshot.remove(uuid) != null) {
                scheduleWrite(uuid, file);
            } else {
                writePending.remove(uuid);
            }
        }
    }

    /**
     * Synchronous atomic save — used by shutdown/saveAll to guarantee flush.
     */
    private void saveToFileSync(UUID uuid, ClassData data) {
        Path file = playerDir.resolve(uuid.toString() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", uuid.toString());
        yaml.set("class", data.className());
        yaml.set("level", data.level());
        yaml.set("xp", data.xp());
        writeProgress(yaml, uuid, data);
        AtomicFiles.save(yaml, file, plugin.getLogger());
    }

    public void saveAll() {
        for (Map.Entry<UUID, ClassData> entry : players.entrySet()) {
            saveToFileSync(entry.getKey(), entry.getValue());
        }
    }

    public void shutdown() {
        saveAll();
    }

    private String sanitizeClassName(String className) {
        if (className == null) return "none";
        return SANITIZE_PATTERN.matcher(className.toLowerCase(java.util.Locale.ROOT)).replaceAll("");
    }
}
