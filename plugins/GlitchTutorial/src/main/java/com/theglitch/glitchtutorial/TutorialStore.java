package com.theglitch.glitchtutorial;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** players.yml — one record per player; saved async + atomically (temp file, then move). */
final class TutorialStore {

    enum Status { ACTIVE, COMPLETE, SKIPPED, LEGACY }

    static final class Record {
        Status status = Status.ACTIVE;
        Step step = Step.INTRO;
        int progress;
        boolean rewarded;
        /** Inventory before the tutorial (41 slots, base64 per slot, "" = empty). */
        List<String> snapshot = new ArrayList<>();
    }

    private final GlitchTutorial plugin;
    private final File file;
    private final Map<UUID, Record> records = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    TutorialStore(GlitchTutorial plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, 200L, 200L);
    }

    Record get(UUID id) {
        return records.get(id);
    }

    Record create(UUID id) {
        Record r = new Record();
        records.put(id, r);
        dirty = true;
        return r;
    }

    void markDirty() {
        dirty = true;
    }

    // ---- snapshot ----

    static List<String> snapshot(ItemStack[] contents) {
        List<String> out = new ArrayList<>();
        for (ItemStack it : contents) {
            out.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        }
        return out;
    }

    static List<ItemStack> restore(List<String> snap) {
        List<ItemStack> out = new ArrayList<>();
        for (String s : snap) {
            if (s == null || s.isEmpty()) {
                out.add(null);
                continue;
            }
            try {
                out.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(s)));
            } catch (Exception e) {
                out.add(null);
            }
        }
        return out;
    }

    // ---- persistence ----

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                Record r = new Record();
                r.status = Status.valueOf(s.getString("status", "ACTIVE"));
                r.step = Step.valueOf(s.getString("step", "INTRO"));
                r.progress = s.getInt("progress");
                r.rewarded = s.getBoolean("rewarded");
                r.snapshot = new ArrayList<>(s.getStringList("snapshot"));
                records.put(UUID.fromString(key), r);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping bad tutorial record " + key + ": " + e.getMessage());
            }
        }
    }

    private void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Record> e : records.entrySet()) {
            String k = "players." + e.getKey();
            Record r = e.getValue();
            y.set(k + ".status", r.status.name());
            y.set(k + ".step", r.step.name());
            y.set(k + ".progress", r.progress);
            y.set(k + ".rewarded", r.rewarded);
            if (r.status == Status.ACTIVE && !r.snapshot.isEmpty()) y.set(k + ".snapshot", r.snapshot);
        }
        String data = y.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    void saveNow() {
        dirty = true;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Record> e : records.entrySet()) {
            String k = "players." + e.getKey();
            Record r = e.getValue();
            y.set(k + ".status", r.status.name());
            y.set(k + ".step", r.step.name());
            y.set(k + ".progress", r.progress);
            y.set(k + ".rewarded", r.rewarded);
            if (r.status == Status.ACTIVE && !r.snapshot.isEmpty()) y.set(k + ".snapshot", r.snapshot);
        }
        write(y.saveToString());
        dirty = false;
    }

    private synchronized void write(String data) {
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(plugin.getDataFolder(), "players.yml.tmp");
            Files.writeString(tmp.toPath(), data);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            plugin.getLogger().warning("Couldn't save players.yml: " + e.getMessage());
        }
    }
}
