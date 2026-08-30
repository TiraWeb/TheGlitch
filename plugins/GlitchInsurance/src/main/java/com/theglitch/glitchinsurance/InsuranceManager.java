package com.theglitch.glitchinsurance;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Manages shard-backed item insurance.
 * Persists per-player under plugins/GlitchInsurance/data/<uuid>.yml using
 * Base64 BukkitObject streams. Async atomic saves mirror StashManager pattern.
 */
public final class InsuranceManager {

    public enum InsureResult {
        SUCCESS, ALREADY_INSURED, MAX_REACHED, NOT_ENOUGH_SHARDS, COOLDOWN, AIR, NO_ECONOMY
    }

    public static final class InsuredItem {
        private final ItemStack item;
        private final long insuredAt;
        private final long expiresAt;
        private final String itemName;

        public InsuredItem(ItemStack item, long insuredAt, long expiresAt, String itemName) {
            this.item = item;
            this.insuredAt = insuredAt;
            this.expiresAt = expiresAt;
            this.itemName = itemName;
        }

        public ItemStack item() {
            return item.clone();
        }

        public ItemStack rawItem() {
            return item;
        }

        public long insuredAt() {
            return insuredAt;
        }

        public long expiresAt() {
            return expiresAt;
        }

        public String itemName() {
            return itemName;
        }

        public long remainingSeconds() {
            long rem = (expiresAt - System.currentTimeMillis()) / 1000;
            return Math.max(0, rem);
        }
    }

    private final GlitchInsurance plugin;
    private final Map<UUID, List<InsuredItem>> insured = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Path dataDir;
    // Per-UUID save generation: write-latest-wins guard for async persistence
    private final Map<UUID, AtomicLong> saveGenerations = new ConcurrentHashMap<>();
    private final Map<UUID, Object> saveLocks = new ConcurrentHashMap<>();
    // Generation value that voids all in-flight writes (set on file deletion)
    private static final long TOMBSTONE = Long.MIN_VALUE;

    // Cached config
    private volatile int premiumPerItem = 100;
    private volatile int maxInsuredItems = 3;
    private volatile int claimWindowSeconds = 300;
    private volatile int cooldownSeconds = 60;
    private volatile Set<String> enabledWorlds = Set.of("glitch_red", "glitch_pve");

    public InsuranceManager(GlitchInsurance plugin) {
        this.plugin = plugin;
        this.dataDir = plugin.getDataFolder().toPath().resolve("data");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create data directory", e);
        }
        cacheConfig();
        loadAll();
    }

    public void reload() {
        cacheConfig();
        // Expire outdated entries on reload
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, List<InsuredItem>> e : insured.entrySet()) {
            e.getValue().removeIf(item -> now > item.expiresAt());
        }
    }

    private void cacheConfig() {
        try {
            premiumPerItem = plugin.getConfig().getInt("insurance.premium-per-item", 100);
            if (premiumPerItem < 0) {
                plugin.getLogger().warning("Invalid premium-per-item " + premiumPerItem + " — clamped to 0.");
                premiumPerItem = Math.max(0, premiumPerItem);
            }
            maxInsuredItems = plugin.getConfig().getInt("insurance.max-insured-items", 3);
            if (maxInsuredItems < 1 || maxInsuredItems > 36) {
                plugin.getLogger().warning("Invalid max-insured-items " + maxInsuredItems + " — clamped to 3.");
                maxInsuredItems = Math.max(1, Math.min(maxInsuredItems, 36));
            }
            claimWindowSeconds = plugin.getConfig().getInt("insurance.claim-window-seconds", 300);
            if (claimWindowSeconds < 1 || claimWindowSeconds > 86400) {
                plugin.getLogger().warning("Invalid claim-window-seconds " + claimWindowSeconds + " — clamped to 300.");
                claimWindowSeconds = Math.max(1, Math.min(claimWindowSeconds, 86400));
            }
            cooldownSeconds = plugin.getConfig().getInt("insurance.cooldown-seconds", 60);
            if (cooldownSeconds < 0 || cooldownSeconds > 3600) {
                plugin.getLogger().warning("Invalid cooldown-seconds " + cooldownSeconds + " — clamped to 60.");
                cooldownSeconds = Math.max(0, Math.min(cooldownSeconds, 3600));
            }
            List<String> worlds = plugin.getConfig().getStringList("insurance.enabled-worlds");
            if (worlds.isEmpty()) {
                plugin.getLogger().warning("enabled-worlds empty — no world will have insurance protection.");
                enabledWorlds = Set.of();
            } else {
                enabledWorlds = Set.copyOf(worlds);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to cache GlitchInsurance config", e);
        }
    }

    public boolean isEnabledWorld(String world) {
        return enabledWorlds.contains(world);
    }

    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public int getPremiumPerItem() {
        return premiumPerItem;
    }

    public int getMaxInsuredItems() {
        return maxInsuredItems;
    }

    public int getClaimWindowSeconds() {
        return claimWindowSeconds;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean isOnCooldown(UUID uuid) {
        Long last = cooldowns.get(uuid);
        if (last == null) return false;
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        return elapsed < cooldownSeconds;
    }

    public long getCooldownRemaining(UUID uuid) {
        Long last = cooldowns.get(uuid);
        if (last == null) return 0;
        long elapsed = (System.currentTimeMillis() - last) / 1000;
        long remaining = cooldownSeconds - elapsed;
        return Math.max(0, remaining);
    }

    public List<InsuredItem> getInsured(UUID uuid) {
        List<InsuredItem> list = insured.get(uuid);
        if (list == null) return Collections.emptyList();
        // Return copy without expired items
        long now = System.currentTimeMillis();
        List<InsuredItem> copy = new ArrayList<>();
        for (InsuredItem it : list) {
            if (now <= it.expiresAt()) copy.add(it);
        }
        return Collections.unmodifiableList(copy);
    }

    public int countInsured(UUID uuid) {
        List<InsuredItem> list = insured.get(uuid);
        if (list == null) return 0;
        long now = System.currentTimeMillis();
        int c = 0;
        for (InsuredItem it : list) if (now <= it.expiresAt()) c++;
        return c;
    }

    /**
     * Attempt to insure the given ItemStack for the player.
     * Checks max, cooldown, already insured, and Vault balance.
     */
    public InsureResult insureItem(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return InsureResult.AIR;
        UUID uuid = player.getUniqueId();

        // Cooldown check
        if (isOnCooldown(uuid)) return InsureResult.COOLDOWN;

        // Purge expired first
        purgeExpired(uuid);

        List<InsuredItem> list = insured.computeIfAbsent(uuid, k -> new ArrayList<>());
        if (list.size() >= maxInsuredItems) return InsureResult.MAX_REACHED;

        // Already insured check (isSimilar)
        for (InsuredItem existing : list) {
            if (existing.rawItem().isSimilar(stack)) {
                return InsureResult.ALREADY_INSURED;
            }
        }

        int premium = premiumPerItem;
        Economy economy = plugin.getEconomy();
        if (premium > 0) {
            if (economy == null) return InsureResult.NO_ECONOMY;
            if (!economy.has(player, premium)) return InsureResult.NOT_ENOUGH_SHARDS;
            economy.withdrawPlayer(player, premium);
        }

        long now = System.currentTimeMillis();
        long expires = now + (long) claimWindowSeconds * 1000L;
        String name = displayName(stack);
        InsuredItem insuredItem = new InsuredItem(stack.clone(), now, expires, name);
        list.add(insuredItem);
        cooldowns.put(uuid, now);
        saveInsurance(uuid);
        return InsureResult.SUCCESS;
    }

    /**
     * Claim all insured items for the player and clear storage.
     * Returns the list of ItemStacks to give back; empty if none or expired.
     */
    public List<ItemStack> claim(UUID uuid) {
        List<InsuredItem> list = insured.remove(uuid);
        if (list == null || list.isEmpty()) return List.of();
        // Filter expired — only return non-expired? Or return all? Claim window means expired shouldn't be claimable.
        long now = System.currentTimeMillis();
        List<ItemStack> result = new ArrayList<>();
        List<InsuredItem> remaining = new ArrayList<>();
        for (InsuredItem it : list) {
            if (now <= it.expiresAt()) {
                result.add(it.item());
            } else {
                // expired — don't return, but still removed
            }
        }
        // If there were expired items mixed with valid, we already removed all; if some were not claimable we don't re-store.
        // If caller wants to keep non-expired that weren't claimed due to filter, they'd be in result already.
        // Save deletion
        deleteFile(uuid);
        // Also purge any expired leftover not returned — already cleared.
        // If there were items that are still valid but we filtered? No, all valid returned.
        // No need to re-store remaining (should be empty).
        if (!remaining.isEmpty()) {
            insured.put(uuid, remaining);
            saveInsurance(uuid);
        }
        return result;
    }

    public ItemStack claimOrdinal(UUID uuid, int ordinal) {
        List<InsuredItem> list = insured.get(uuid);
        if (list == null || list.isEmpty()) return null;
        long now = System.currentTimeMillis();
        int seen = 0;
        for (int i = 0; i < list.size(); i++) {
            InsuredItem candidate = list.get(i);
            if (candidate == null || now > candidate.expiresAt()) continue;
            if (seen++ != ordinal) continue;
            ItemStack out = candidate.item();
            list.remove(i);
            if (list.isEmpty()) {
                insured.remove(uuid);
                deleteFile(uuid);
            } else {
                saveInsurance(uuid);
            }
            return out;
        }
        purgeExpired(uuid);
        return null;
    }

    /**
     * Consume insured items that match drops — used by death listener to auto-keep.
     * Returns number of items moved to keep.
     */
    public int consumeMatching(List<ItemStack> drops, List<ItemStack> itemsToKeep, UUID uuid) {
        List<InsuredItem> list = insured.get(uuid);
        if (list == null || list.isEmpty()) return 0;
        purgeExpired(uuid);
        list = insured.get(uuid);
        if (list == null || list.isEmpty()) return 0;

        int kept = 0;
        // Use iterator over drops to safely remove
        var dropIter = drops.iterator();
        // Track which insured items have been matched and need removal
        List<InsuredItem> matched = new ArrayList<>();
        while (dropIter.hasNext()) {
            ItemStack drop = dropIter.next();
            if (drop == null) continue;
            for (InsuredItem insuredItem : list) {
                if (matched.contains(insuredItem)) continue;
                if (drop.isSimilar(insuredItem.rawItem())) {
                    dropIter.remove();
                    itemsToKeep.add(drop);
                    matched.add(insuredItem);
                    kept++;
                    break;
                }
            }
        }
        if (!matched.isEmpty()) {
            list.removeAll(matched);
            if (list.isEmpty()) {
                insured.remove(uuid);
                deleteFile(uuid);
            } else {
                saveInsurance(uuid);
            }
        }
        return kept;
    }

    /**
     * Kept-inventory deaths (keepInventory or cleared drops): consume policies
     * matching items the player still holds. No payout — gear kept, policy spent.
     * Returns the number of policies consumed.
     */
    public int consumeMatchingRetained(UUID uuid, ItemStack[] retained) {
        List<InsuredItem> list = insured.get(uuid);
        if (list == null || list.isEmpty()) return 0;
        purgeExpired(uuid);
        list = insured.get(uuid);
        if (list == null || list.isEmpty()) return 0;

        List<InsuredItem> matched = new ArrayList<>();
        for (InsuredItem insuredItem : list) {
            for (ItemStack content : retained) {
                if (content != null && content.isSimilar(insuredItem.rawItem())) {
                    matched.add(insuredItem);
                    break;
                }
            }
        }
        if (!matched.isEmpty()) {
            list.removeAll(matched);
            if (list.isEmpty()) {
                insured.remove(uuid);
                deleteFile(uuid);
            } else {
                saveInsurance(uuid);
            }
        }
        return matched.size();
    }

    private void purgeExpired(UUID uuid) {
        List<InsuredItem> list = insured.get(uuid);
        if (list == null) return;
        long now = System.currentTimeMillis();
        boolean removed = list.removeIf(item -> now > item.expiresAt());
        if (removed) {
            if (list.isEmpty()) {
                insured.remove(uuid);
                deleteFile(uuid);
            } else {
                saveInsurance(uuid);
            }
        }
    }

    public boolean clear(UUID uuid) {
        boolean had = insured.remove(uuid) != null;
        cooldowns.remove(uuid);
        deleteFile(uuid);
        return had;
    }

    private void loadAll() {
        if (!Files.exists(dataDir)) return;
        try (var stream = Files.list(dataDir)) {
            stream.filter(p -> p.toString().endsWith(".yml")).forEach(path -> {
                try {
                    UUID uuid = UUID.fromString(path.getFileName().toString().replace(".yml", ""));
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
                    List<?> list = yaml.getList("insured");
                    if (list == null) return;
                    List<InsuredItem> items = new ArrayList<>();
                    for (Object obj : list) {
                        if (!(obj instanceof Map)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) obj;
                        String encoded = (String) map.get("item");
                        long insuredAt = toLong(map.get("insuredAt"));
                        long expiresAt = toLong(map.get("expiresAt"));
                        String itemName = (String) map.getOrDefault("itemName", "item");
                        if (encoded == null) continue;
                        ItemStack stack = deserializeItemStack(encoded);
                        if (stack == null) continue;
                        // Skip already expired on load
                        if (System.currentTimeMillis() > expiresAt) continue;
                        items.add(new InsuredItem(stack, insuredAt, expiresAt, itemName));
                    }
                    if (!items.isEmpty()) {
                        insured.put(uuid, items);
                    }
                    Long cd = null;
                    if (yaml.contains("cooldown")) {
                        cd = yaml.getLong("cooldown");
                        cooldowns.put(uuid, cd);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load insurance: " + path.getFileName(), e);
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to list insurance files", e);
        }
        plugin.getLogger().info("Loaded " + insured.size() + " insured players.");
    }

    private long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    private void saveInsurance(UUID uuid) {
        List<InsuredItem> list = insured.get(uuid);
        // If null, delete file (should not happen via this path, but handle)
        if (list == null) {
            deleteFile(uuid);
            return;
        }
        // Snapshot to avoid concurrent modification
        List<InsuredItem> snapshot = new ArrayList<>(list);
        Long cd = cooldowns.get(uuid);
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (InsuredItem it : snapshot) {
            String encoded = serializeItemStack(it.rawItem());
            if (encoded == null) continue;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("item", encoded);
            map.put("insuredAt", it.insuredAt());
            map.put("expiresAt", it.expiresAt());
            map.put("itemName", it.itemName());
            serialized.add(map);
        }
        yaml.set("insured", serialized);
        if (cd != null) yaml.set("cooldown", cd);
        yaml.set("uuid", uuid.toString());

        // Serialize on the calling (main) thread at mutation time
        String payload = yaml.saveToString();
        Path file = dataDir.resolve(uuid.toString() + ".yml");
        AtomicLong generation = saveGenerations.computeIfAbsent(uuid, k -> new AtomicLong());
        Object lock = saveLocks.computeIfAbsent(uuid, k -> new Object());
        long gen = generation.incrementAndGet();
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                synchronized (lock) {
                    // Write-latest-wins: skip if a newer generation or tombstone is pending
                    if (generation.get() != gen) return;
                    atomicSave(payload, file);
                }
            });
        } catch (Throwable t) {
            try {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    synchronized (lock) {
                        if (generation.get() != gen) return;
                        atomicSave(payload, file);
                    }
                });
            } catch (Throwable t2) {
                atomicSave(payload, file);
                plugin.getLogger().log(Level.WARNING, "Async scheduler unavailable, saved synchronously for " + uuid, t2);
            }
        }
    }

    private void saveInsuranceSync(UUID uuid, List<InsuredItem> list) {
        Path file = dataDir.resolve(uuid.toString() + ".yml");
        if (list == null || list.isEmpty()) {
            try { Files.deleteIfExists(file); } catch (IOException e) { plugin.getLogger().log(Level.WARNING, "Failed to delete insurance file for " + uuid, e); }
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (InsuredItem it : list) {
            String encoded = serializeItemStack(it.rawItem());
            if (encoded == null) continue;
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("item", encoded);
            map.put("insuredAt", it.insuredAt());
            map.put("expiresAt", it.expiresAt());
            map.put("itemName", it.itemName());
            serialized.add(map);
        }
        yaml.set("insured", serialized);
        Long cd = cooldowns.get(uuid);
        if (cd != null) yaml.set("cooldown", cd);
        yaml.set("uuid", uuid.toString());
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
            plugin.getLogger().log(Level.WARNING, "Failed to save insurance for " + uuid, e);
        }
    }

    static void atomicSave(String yamlPayload, Path target) {
        atomicSave(yamlPayload, target, Bukkit.getLogger());
    }

    static void atomicSave(String yamlPayload, Path target, java.util.logging.Logger logger) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, target.getFileName().toString() + "-", ".tmp");
            try {
                Files.writeString(tmp, yamlPayload);
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
        Object lock = saveLocks.get(uuid);
        if (lock == null) {
            // No async write was ever scheduled — plain delete is safe
            deleteFileNow(uuid);
            return;
        }
        // Tombstone under the write lock so in-flight tasks cannot resurrect the file
        synchronized (lock) {
            AtomicLong gen = saveGenerations.get(uuid);
            if (gen != null) gen.set(TOMBSTONE);
            deleteFileNow(uuid);
        }
    }

    private void deleteFileNow(UUID uuid) {
        Path file = dataDir.resolve(uuid.toString() + ".yml");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete insurance file for " + uuid, e);
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, List<InsuredItem>> e : insured.entrySet()) {
            UUID uuid = e.getKey();
            Object lock = saveLocks.get(uuid);
            if (lock == null) {
                saveInsuranceSync(uuid, e.getValue());
                continue;
            }
            // Bump the generation under the write lock so no late async task
            // can regress this final sync save
            synchronized (lock) {
                AtomicLong gen = saveGenerations.get(uuid);
                if (gen != null) gen.incrementAndGet();
                saveInsuranceSync(uuid, e.getValue());
            }
        }
    }

    public void shutdown() {
        saveAll();
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

    private static String displayName(ItemStack stack) {
        if (stack == null) return "AIR";
        var meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            // Use plain text fallback
            try {
                var comp = meta.displayName();
                if (comp != null) {
                    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp);
                }
            } catch (Throwable ignored) {}
            // Fallback to legacy
            String d = meta.getDisplayName();
            if (d != null && !d.isBlank()) return d;
        }
        // Use material name
        String mat = stack.getType().name().toLowerCase().replace('_', ' ');
        return mat;
    }
}
