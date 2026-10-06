package com.theglitch.glitchstash;

import com.theglitch.common.AtomicFiles;
import com.theglitch.common.InventoryUtil;
import com.theglitch.common.ItemCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player stashes — YAML-based persistent storage.
 * Each player gets their own file under plugins/GlitchStash/stashes/
 * <p>
 * Persistence is async + atomic: saveToFile builds a YamlConfiguration snapshot
 * on the main thread then schedules an async task that writes to a temp file and
 * atomically moves it to the target. shutdown()/saveAll() perform synchronous
 * atomic writes to guarantee no data-loss on crash/disable.
 */
public final class StashManager {

    private final GlitchStash plugin;
    private final Map<UUID, StashData> stashes = new ConcurrentHashMap<>();
    private final Path stashDir;
    // Per-UUID save generation: each scheduled write captures its generation and
    // skips itself if a newer save (or a clearStash tombstone) superseded it —
    // prevents out-of-order async writes resurrecting stale/retrieved items.
    private final Map<UUID, Long> saveGens = new ConcurrentHashMap<>();

    public record StashData(
            UUID uuid,
            String playerName,
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offhand,
            long timestamp
    ) {}

    public StashManager(GlitchStash plugin) {
        this.plugin = plugin;
        this.stashDir = plugin.getDataFolder().toPath().resolve("stashes");
        try {
            Files.createDirectories(stashDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create stashes directory", e);
        }
        loadAllStashes();
    }

    /**
     * Save a player's inventory to their stash.
     * If a stash already exists, items are MERGED (appended) — not replaced.
     * This allows multiple extractions to accumulate items.
     * Optimized: no Bukkit.createInventory allocation — manual stack merging.
     */
    public void saveStash(UUID uuid, String playerName, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        StashData existing = stashes.get(uuid);

        ItemStack[] mergedContents;
        ItemStack[] mergedArmor;
        ItemStack mergedOffhand;

        if (existing != null) {
            // Merge without allocating a Bukkit inventory — stack manually into list
            List<ItemStack> merged = new ArrayList<>(existing.contents().length + contents.length + 5);
            // Add existing stash items first, stacking where possible
            for (ItemStack item : existing.contents()) {
                if (item != null && item.getType() != Material.AIR) {
                    InventoryUtil.mergeStack(merged, item.clone());
                }
            }
            // Previously stashed armor/offhand move into the contents list. The old code
            // REPLACED them whenever the new extraction carried armor, deleting the
            // earlier pieces (the GUI shows everything as one flat list anyway).
            if (existing.armor() != null) {
                for (ItemStack item : existing.armor()) {
                    if (item != null && item.getType() != Material.AIR) merged.add(item.clone());
                }
            }
            if (existing.offhand() != null && existing.offhand().getType() != Material.AIR) {
                merged.add(existing.offhand().clone());
            }
            // Add new extraction items
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR) {
                    InventoryUtil.mergeStack(merged, item.clone());
                }
            }

            mergedContents = merged.toArray(new ItemStack[0]);
            mergedArmor = deepCopy(armor);
            mergedOffhand = offhand != null && offhand.getType() != Material.AIR ? offhand.clone() : null;
        } else {
            // No merge needed — filter null AIR but keep array as is for first save
            // Defensive copy to avoid external mutation
            List<ItemStack> filtered = new ArrayList<>(contents.length);
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR) filtered.add(item.clone());
                else filtered.add(item);
            }
            mergedContents = filtered.toArray(new ItemStack[0]);
            mergedArmor = deepCopy(armor);
            mergedOffhand = offhand != null && offhand.getType() != Material.AIR ? offhand.clone() : null;
        }

        StashData data = new StashData(uuid, playerName, mergedContents, mergedArmor, mergedOffhand, System.currentTimeMillis());
        stashes.put(uuid, data);
        saveToFile(uuid, data);

        int itemCount = 0;
        for (ItemStack item : mergedContents) {
            if (item != null) itemCount++;
        }
        for (ItemStack item : mergedArmor) {
            if (item != null && item.getType() != Material.AIR) itemCount++;
        }
        if (mergedOffhand != null && mergedOffhand.getType() != Material.AIR) itemCount++;
        if (itemCount > 45) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(plugin.getComponent("stash-full"));
            }
        }
    }

    private static ItemStack[] deepCopy(ItemStack[] items) {
        if (items == null) return new ItemStack[4];
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            out[i] = items[i] == null ? null : items[i].clone();
        }
        return out;
    }

    /**
     * Check if a player has a stored stash.
     */
    public boolean hasStash(UUID uuid) {
        return stashes.containsKey(uuid);
    }

    /**
     * Replace a player's stash contents (used after partial GUI retrieval).
     * The GUI flattens contents + armor + offhand into one grid, so the
     * replacement stores the flat list as contents and clears armor/offhand
     * (otherwise already-retrieved armor pieces would duplicate).
     */
    public void replaceStash(UUID uuid, ItemStack[] newContents) {
        StashData existing = stashes.get(uuid);
        String playerName = existing == null ? "Unknown" : existing.playerName();
        StashData data = new StashData(uuid, playerName, newContents, new ItemStack[4], null, System.currentTimeMillis());
        stashes.put(uuid, data);
        saveToFile(uuid, data);
    }

    /**
     * Get stash data without removing it.
     */
    public Optional<StashData> peekStash(UUID uuid) {
        return Optional.ofNullable(stashes.get(uuid));
    }

    public List<ItemStack> listStash(UUID uuid) {
        StashData data = stashes.get(uuid);
        if (data == null) return List.of();
        return List.copyOf(flattenUi(data));
    }

    public boolean takeFromUi(Player player, int index) {
        UUID uuid = player.getUniqueId();
        StashData data = stashes.get(uuid);
        if (data == null) {
            player.sendMessage(plugin.getComponent("stash-empty"));
            return false;
        }
        List<ItemStack> flat = flattenUi(data);
        if (index < 0 || index >= flat.size()) {
            return false;
        }
        ItemStack clicked = flat.get(index);
        if (clicked == null || clicked.getType().isAir()) {
            return false;
        }
        flat.set(index, null);

        int remaining = 0;
        for (ItemStack item : flat) {
            if (item != null && !item.getType().isAir()) remaining++;
        }
        ItemStack[] newContents = flat.toArray(new ItemStack[0]);
        replaceStash(uuid, newContents);
        if (remaining == 0) {
            clearStash(uuid);
            player.sendMessage(plugin.getComponent("all-retrieved"));
        }

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(clicked.clone());
        if (leftover.isEmpty()) {
            player.sendMessage(Component.text("+ " + clicked.getAmount() + " " +
                    clicked.getType().name().toLowerCase().replace("_", " "),
                    NamedTextColor.GREEN));
        } else {
            int dropped = 0;
            World world = player.getWorld();
            Location loc = player.getLocation();
            for (ItemStack left : leftover.values()) {
                if (left == null) continue;
                dropped += left.getAmount();
                world.dropItemNaturally(loc, left);
            }
            player.sendMessage(Component.text("Inventory full! Dropped " + dropped + " at your feet.",
                    NamedTextColor.RED));
        }
        // Keep any open StashGUI in sync — the manager is the single source of
        // truth, so a dialog/`stashui take` must invalidate the stale chest view.
        StashGUI.refresh(player);
        return true;
    }

    private List<ItemStack> flattenUi(StashData data) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack item : data.contents()) {
            if (item != null && !item.getType().isAir()) out.add(item.clone());
        }
        for (ItemStack item : data.armor()) {
            if (item != null && !item.getType().isAir()) out.add(item.clone());
        }
        if (data.offhand() != null && !data.offhand().getType().isAir()) {
            out.add(data.offhand().clone());
        }
        return out;
    }

    /**
     * Get total number of stashes.
     */
    public int getStashCount() {
        return stashes.size();
    }

    /**
     * Clear a player's stash.
     */
    public boolean clearStash(UUID uuid) {
        StashData removed = stashes.remove(uuid);
        if (removed != null) {
            // Tombstone: voids any in-flight async write so it cannot
            // re-create the file we are about to delete.
            saveGens.put(uuid, Long.MIN_VALUE);
            deleteFile(uuid);
            return true;
        }
        return false;
    }

    private void loadAllStashes() {
        if (!Files.exists(stashDir)) return;

        try (var stream = Files.list(stashDir)) {
            stream.filter(p -> p.toString().endsWith(".yml")).forEach(path -> {
                try {
                    UUID uuid = UUID.fromString(path.getFileName().toString().replace(".yml", ""));
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());

                    String playerName = yaml.getString("player-name", "Unknown");
                    long timestamp = yaml.getLong("timestamp", System.currentTimeMillis());

                    ItemStack[] contents = ItemCodec.decodeAll(yaml.getStringList("contents"));
                    ItemStack[] armor = ItemCodec.decodeAll(yaml.getStringList("armor"));
                    ItemStack offhand = ItemCodec.decode(yaml.getString("offhand"));

                    StashData data = new StashData(uuid, playerName, contents, armor, offhand, timestamp);
                    stashes.put(uuid, data);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load stash: " + path.getFileName(), e);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to list stash files", e);
        }
    }

    private void saveToFile(UUID uuid, StashData data) {
        Path file = stashDir.resolve(uuid.toString() + ".yml");
        YamlConfiguration yaml = buildYaml(data);

        final long gen = saveGens.merge(uuid, 1L, Long::sum);
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                if (saveGens.get(uuid) == gen) {
                    AtomicFiles.save(yaml, file, plugin.getLogger());
                }
            });
        } catch (Throwable t) {
            try {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (saveGens.get(uuid) == gen) {
                        AtomicFiles.save(yaml, file, plugin.getLogger());
                    }
                });
            } catch (Throwable t2) {
                AtomicFiles.save(yaml, file, plugin.getLogger());
                plugin.getLogger().log(Level.WARNING, "Async scheduler unavailable, saved synchronously for " + uuid, t2);
            }
        }
    }

    private void saveToFileSync(UUID uuid, StashData data) {
        Path file = stashDir.resolve(uuid.toString() + ".yml");
        YamlConfiguration yaml = buildYaml(data);
        AtomicFiles.save(yaml, file, plugin.getLogger());
    }

    /** Single helper for YAML snapshot — saveToFile/saveToFileSync share it. */
    private static YamlConfiguration buildYaml(StashData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player-name", data.playerName());
        yaml.set("timestamp", data.timestamp());
        yaml.set("contents", ItemCodec.encodeAll(data.contents()));
        yaml.set("armor", ItemCodec.encodeAll(data.armor()));
        yaml.set("offhand", ItemCodec.encode(data.offhand()));
        return yaml;
    }

    private void deleteFile(UUID uuid) {
        Path file = stashDir.resolve(uuid.toString() + ".yml");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete stash file for " + uuid, e);
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, StashData> entry : stashes.entrySet()) {
            saveToFileSync(entry.getKey(), entry.getValue());
        }
    }

    public void shutdown() {
        saveAll();
    }
}
