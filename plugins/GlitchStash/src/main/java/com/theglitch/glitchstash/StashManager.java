package com.theglitch.glitchstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
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
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

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
                    mergeStack(merged, item.clone());
                }
            }
            // Add new extraction items
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR) {
                    mergeStack(merged, item.clone());
                }
            }

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
            // No merge needed — filter null AIR but keep array as is for first save
            // Defensive copy to avoid external mutation
            List<ItemStack> filtered = new ArrayList<>(contents.length);
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR) filtered.add(item.clone());
                else filtered.add(item);
            }
            mergedContents = filtered.toArray(new ItemStack[0]);
            mergedArmor = armor;
            mergedOffhand = offhand;
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

    private static void mergeStack(List<ItemStack> target, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        int remaining = stack.getAmount();
        int max = stack.getMaxStackSize();
        // Try to top-up existing similar stacks
        for (ItemStack existing : target) {
            if (existing.isSimilar(stack) && existing.getAmount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                int toAdd = Math.min(remaining, space);
                existing.setAmount(existing.getAmount() + toAdd);
                remaining -= toAdd;
                if (remaining <= 0) return;
            }
        }
        // Add remainder as new stack(s), splitting if > max
        while (remaining > 0) {
            int chunk = Math.min(remaining, max);
            ItemStack part = stack.clone();
            part.setAmount(chunk);
            target.add(part);
            remaining -= chunk;
        }
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
        return data == null ? new ArrayList<>() : flattenUi(data);
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

        dirty.add(uuid);
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                try {
                    atomicSave(yaml, file);
                } finally {
                    dirty.remove(uuid);
                }
            });
        } catch (Throwable t) {
            try {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        atomicSave(yaml, file);
                    } finally {
                        dirty.remove(uuid);
                    }
                });
            } catch (Throwable t2) {
                try {
                    atomicSave(yaml, file);
                } finally {
                    dirty.remove(uuid);
                }
                plugin.getLogger().log(Level.WARNING, "Async scheduler unavailable, saved synchronously for " + uuid, t2);
            }
        }
    }

    private void saveToFileSync(UUID uuid, StashData data) {
        Path file = stashDir.resolve(uuid.toString() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player-name", data.playerName());
        yaml.set("timestamp", data.timestamp());
        yaml.set("contents", serializeItemStacks(data.contents()));
        yaml.set("armor", serializeItemStacks(data.armor()));
        yaml.set("offhand", serializeItemStack(data.offhand()));
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, uuid.toString() + "-", ".tmp");
            try {
                yaml.save(tmp.toFile());
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save stash for " + data.playerName(), e);
        }
    }

    /**
     * Static utility for atomic YAML persistence.
     * Writes to a temp file in the same directory then atomically moves to target.
     * Falls back to non-atomic move if ATOMIC_MOVE is unsupported.
     */
    static void atomicSave(YamlConfiguration yaml, Path target) {
        atomicSave(yaml, target, Bukkit.getLogger());
    }

    static void atomicSave(YamlConfiguration yaml, Path target, java.util.logging.Logger logger) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, target.getFileName().toString() + "-", ".tmp");
            try {
                yaml.save(tmp.toFile());
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to atomically save " + target, e);
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

    public static ItemStack deserializeItemStack(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
              BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    public static List<String> serializeItemStacks(ItemStack[] items) {
        List<String> result = new ArrayList<>();
        for (ItemStack item : items) {
            result.add(serializeItemStack(item));
        }
        return result;
    }

    public static ItemStack[] deserializeItemStacks(List<String> encoded) {
        ItemStack[] items = new ItemStack[encoded.size()];
        for (int i = 0; i < encoded.size(); i++) {
            items[i] = deserializeItemStack(encoded.get(i));
        }
        return items;
    }

    public void saveAll() {
        for (Map.Entry<UUID, StashData> entry : stashes.entrySet()) {
            saveToFileSync(entry.getKey(), entry.getValue());
        }
        dirty.clear();
    }

    public void shutdown() {
        saveAll();
    }
}
