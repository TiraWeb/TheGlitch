package com.theglitch.glitchstash;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player stashes — YAML-based persistent storage.
 * Each player gets their own file under plugins/GlitchStash/stashes/
 */
public final class StashManager {

    private final GlitchStash plugin;
    private final Map<UUID, StashData> stashes = new ConcurrentHashMap<>();
    private final Path stashDir;

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
     */
    public void saveStash(UUID uuid, String playerName, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        StashData existing = stashes.get(uuid);

        ItemStack[] mergedContents;
        ItemStack[] mergedArmor;
        ItemStack mergedOffhand;

        if (existing != null) {
            // Merge: combine existing stash contents with new extraction.
            // A temp inventory handles stacking automatically; anything that
            // does not fit (54-slot cap) is appended after it — never dropped.
            org.bukkit.inventory.Inventory temp = Bukkit.createInventory(null, 54);

            Map<Integer, ItemStack> leftovers = new java.util.HashMap<>();
            // Add existing stash items first
            for (ItemStack item : existing.contents()) {
                if (item != null) leftovers.putAll(temp.addItem(item.clone()));
            }
            // Add new extraction items
            for (ItemStack item : contents) {
                if (item != null) leftovers.putAll(temp.addItem(item.clone()));
            }

            List<ItemStack> merged = new ArrayList<>();
            java.util.Collections.addAll(merged, temp.getContents());
            merged.addAll(leftovers.values());
            mergedContents = merged.toArray(new ItemStack[0]);

            // Merge armor — keep existing if new extraction has empty slots
            if (armor != null && armor.length > 0) {
                boolean hasNewArmor = false;
                for (ItemStack a : armor) {
                    if (a != null && a.getType() != Material.AIR) {
                        hasNewArmor = true;
                        break;
                    }
                }
                mergedArmor = hasNewArmor ? armor : existing.armor();
            } else {
                mergedArmor = existing.armor();
            }

            // Merge offhand — keep existing if new extraction has empty offhand
            if (offhand != null && offhand.getType() != Material.AIR) {
                mergedOffhand = offhand;
            } else {
                mergedOffhand = existing.offhand();
            }
        } else {
            mergedContents = contents;
            mergedArmor = armor;
            mergedOffhand = offhand;
        }

        StashData data = new StashData(uuid, playerName, mergedContents, mergedArmor, mergedOffhand, System.currentTimeMillis());
        stashes.put(uuid, data);
        saveToFile(uuid, data);

        // Warn when the stash exceeds the 45-slot GUI display — items stay saved
        // (tail is preserved on close) but won't all be visible until retrieved.
        int itemCount = 0;
        for (ItemStack item : mergedContents) {
            if (item != null) itemCount++;
        }
        for (ItemStack item : mergedArmor) {
            if (item != null) itemCount++;
        }
        if (mergedOffhand != null && mergedOffhand.getType() != Material.AIR) itemCount++;
        if (itemCount > 45) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(plugin.getComponent("stash-full"));
            }
        }
    }

    /**
     * Retrieve and remove all items from a player's stash.
     * Returns empty optional if stash is empty or doesn't exist.
     */
    public Optional<StashData> retrieveStash(UUID uuid) {
        StashData data = stashes.remove(uuid);
        if (data != null) {
            deleteFile(uuid);
        }
        return Optional.ofNullable(data);
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

    /**
     * Get the number of items in a stash.
     */
    public int getStashItemCount(UUID uuid) {
        StashData data = stashes.get(uuid);
        if (data == null) return 0;
        int count = 0;
        for (ItemStack item : data.contents()) {
            if (item != null) count += item.getAmount();
        }
        for (ItemStack item : data.armor()) {
            if (item != null) count += item.getAmount();
        }
        if (data.offhand() != null) count += data.offhand().getAmount();
        return count;
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

                    ItemStack[] contents = deserializeItemStacks(yaml.getStringList("contents"));
                    ItemStack[] armor = deserializeItemStacks(yaml.getStringList("armor"));
                    ItemStack offhand = deserializeItemStack(yaml.getString("offhand"));

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
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("player-name", data.playerName());
        yaml.set("timestamp", data.timestamp());
        yaml.set("contents", serializeItemStacks(data.contents()));
        yaml.set("armor", serializeItemStacks(data.armor()));
        yaml.set("offhand", serializeItemStack(data.offhand()));

        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save stash for " + data.playerName(), e);
        }
    }

    private void deleteFile(UUID uuid) {
        Path file = stashDir.resolve(uuid.toString() + ".yml");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete stash file for " + uuid, e);
        }
    }

    /**
     * Serialize an ItemStack to a Base64 string.
     */
    public static String serializeItemStack(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(item);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Deserialize a Base64 string to an ItemStack.
     */
    public static ItemStack deserializeItemStack(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Serialize an array of ItemStacks to a list of Base64 strings.
     */
    public static List<String> serializeItemStacks(ItemStack[] items) {
        List<String> result = new ArrayList<>();
        for (ItemStack item : items) {
            result.add(serializeItemStack(item));
        }
        return result;
    }

    /**
     * Deserialize a list of Base64 strings to an array of ItemStacks.
     */
    public static ItemStack[] deserializeItemStacks(List<String> encoded) {
        ItemStack[] items = new ItemStack[encoded.size()];
        for (int i = 0; i < encoded.size(); i++) {
            items[i] = deserializeItemStack(encoded.get(i));
        }
        return items;
    }

    public void shutdown() {
        // Save any in-memory stashes that might not be persisted
        stashes.forEach(this::saveToFile);
    }
}
