package com.theglitch.glitchinsurance;

import com.theglitch.common.AtomicFiles;
import com.theglitch.common.ItemCodec;
import com.theglitch.common.Worlds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
    /** How long a bought policy protects the item (independent of the old claim window). */
    private volatile int policyDurationSeconds = 3600;
    private volatile int cooldownSeconds = 60;
    private volatile Set<String> enabledWorlds = Worlds.GAME_WORLDS;

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
            policyDurationSeconds = Math.max(60, plugin.getConfig().getInt("insurance.policy-duration-seconds", 3600));
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
            if (samePolicyItem(existing.rawItem(), stack)) {
                return InsureResult.ALREADY_INSURED;
            }
        }

        int premium = premiumPerItem;
        Economy economy = plugin.getEconomy();
        if (premium > 0) {
            if (economy == null) return InsureResult.NO_ECONOMY;
            if (!economy.has(player, premium)) return InsureResult.NOT_ENOUGH_SHARDS;
            if (!economy.withdrawPlayer(player, premium).transactionSuccess()) return InsureResult.NOT_ENOUGH_SHARDS;
        }

        long now = System.currentTimeMillis();
        long expires = now + (long) policyDurationSeconds * 1000L;
        String name = displayName(stack);
        InsuredItem insuredItem = new InsuredItem(stack.clone(), now, expires, name);
        list.add(insuredItem);
        cooldowns.put(uuid, now);
        saveInsurance(uuid);
        return InsureResult.SUCCESS;
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
                if (samePolicyItem(drop, insuredItem.rawItem())) {
                    // A policy covers the amount that was insured, not the whole stack
                    int covered = Math.max(1, insuredItem.rawItem().getAmount());
                    if (drop.getAmount() > covered) {
                        ItemStack keep = drop.clone();
                        keep.setAmount(covered);
                        drop.setAmount(drop.getAmount() - covered);
                        itemsToKeep.add(keep);
                    } else {
                        dropIter.remove();
                        itemsToKeep.add(drop);
                    }
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
                if (content != null && samePolicyItem(content, insuredItem.rawItem())) {
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

    /**
     * Same item for insurance purposes: identical apart from stack size and
     * durability. isSimilar() alone failed as soon as the gear took damage in
     * the raid, so the policy never paid out.
     */
    static boolean samePolicyItem(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getType() != b.getType()) return false;
        return normalized(a).isSimilar(normalized(b));
    }

    private static ItemStack normalized(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        if (copy.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.hasDamage()) {
            dmg.setDamage(0);
            copy.setItemMeta(dmg);
        }
        return copy;
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
                        ItemStack stack = ItemCodec.decode(encoded);
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
            String encoded = ItemCodec.encode(it.rawItem());
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
                    AtomicFiles.write(payload, file, plugin.getLogger());
                }
            });
        } catch (Throwable t) {
            try {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    synchronized (lock) {
                        if (generation.get() != gen) return;
                        AtomicFiles.write(payload, file, plugin.getLogger());
                    }
                });
            } catch (Throwable t2) {
                AtomicFiles.write(payload, file, plugin.getLogger());
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
            String encoded = ItemCodec.encode(it.rawItem());
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
        AtomicFiles.save(yaml, file, plugin.getLogger());
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

    /** Plain-text item name for messages: the custom display name, else the material name. */
    static String displayName(ItemStack stack) {
        if (stack == null) return "AIR";
        ItemMeta meta = stack.getItemMeta();
        Component name = meta == null || !meta.hasDisplayName() ? null : meta.displayName();
        if (name != null) return PlainTextComponentSerializer.plainText().serialize(name);
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
