package com.theglitch.glitchitems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nexomc.nexo.api.NexoFurniture;
import com.theglitch.common.NexoUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * In-world loot containers (design GAME_DESIGN.md §3, ITEM_SYSTEM.md §9):
 * Debris Pile (free), Loot Cache (Cache Key), Vault (Vault Key),
 * Rift Vault (Rift Key).
 * <p>
 * Two visual backends per {@link ContainerType}, selected by whether
 * {@code furniture-id} is configured:
 * <ul>
 *   <li><b>Legacy block</b> (no furniture-id, e.g. Vault): a real block
 *       ({@code type.material()}) at the location, unchanged since 2026-08.</li>
 *   <li><b>Nexo furniture</b> (furniture-id set, e.g. Debris/Cache/Rift Vault
 *       as of 2026-09-21 — see docs/MODELS.md "Loot crate furniture"): a
 *       {@link NexoFurniture#place} entity. Nexo furniture is not a tile
 *       entity, so it cannot carry a {@code PersistentDataContainer} the way
 *       the old block-only design did — container identity/regen state for
 *       <b>both</b> backends now lives in {@link #byLocation}, a location-keyed
 *       map persisted to {@code data/containers.json} (same pattern as
 *       {@link ScatterManager}'s own {@code scattered.json}), not on the block.
 * </ul>
 */
public final class ContainerManager {

    private static final NamespacedKey NEXO_KEY = new NamespacedKey("nexo", "id");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Must match AbilityListener.SCAVENGE_TAG — scoreboard tag that grants bonus rolls. */
    public static final String SCAVENGE_TAG = "specter_scavenge";

    // Cached GlitchRaid bridge reflection — avoids per-open getMethod scans on the loot path.
    // Keyed by runtime class so plugin reloads (new classloaders) re-resolve instead of reusing stale Methods.
    private static final Map<Class<?>, java.lang.reflect.Method> RAID_GET_MANAGER_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Method> RAID_IS_IN_RAID_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, java.lang.reflect.Method> RAID_ADD_LOOT_FROM_ITEMS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<Class<?>> RAID_ADD_LOOT_FROM_ITEMS_MISSING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<Class<?>, java.lang.reflect.Method> RAID_ADD_LOOT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static java.lang.reflect.Method cachedRaidMethod(Object target, String name, int slot)
            throws NoSuchMethodException {
        Class<?> clazz = target.getClass();
        switch (slot) {
            case 0: {
                java.lang.reflect.Method cached = RAID_GET_MANAGER_CACHE.get(clazz);
                if (cached == null) {
                    cached = clazz.getMethod(name);
                    RAID_GET_MANAGER_CACHE.put(clazz, cached);
                }
                return cached;
            }
            case 1: {
                java.lang.reflect.Method cached = RAID_IS_IN_RAID_CACHE.get(clazz);
                if (cached == null) {
                    cached = clazz.getMethod(name, java.util.UUID.class);
                    RAID_IS_IN_RAID_CACHE.put(clazz, cached);
                }
                return cached;
            }
            case 2: {
                if (RAID_ADD_LOOT_FROM_ITEMS_MISSING.contains(clazz)) {
                    throw new NoSuchMethodException(name);
                }
                java.lang.reflect.Method cached = RAID_ADD_LOOT_FROM_ITEMS_CACHE.get(clazz);
                if (cached == null) {
                    try {
                        cached = clazz.getMethod(name, Player.class, java.util.Collection.class);
                    } catch (NoSuchMethodException e) {
                        RAID_ADD_LOOT_FROM_ITEMS_MISSING.add(clazz);
                        throw e;
                    }
                    RAID_ADD_LOOT_FROM_ITEMS_CACHE.put(clazz, cached);
                }
                return cached;
            }
            default: {
                java.lang.reflect.Method cached = RAID_ADD_LOOT_CACHE.get(clazz);
                if (cached == null) {
                    cached = clazz.getMethod(name, java.util.UUID.class, int.class);
                    RAID_ADD_LOOT_CACHE.put(clazz, cached);
                }
                return cached;
            }
        }
    }

    private static final Map<String, Material> MATERIAL_MATERIALS = Map.of(
            "rune_fragment", Material.PAPER,
            "aether_shard", Material.GLOWSTONE_DUST,
            "rift_crystal", Material.AMETHYST_SHARD,
            "void_essence", Material.BLACK_DYE,
            "legendary_relic", Material.NETHER_STAR);

    public record ContainerType(
            String name,
            String display,
            Material material,
            String furnitureId,
            String keyId,
            String keyMaterial,
            Material keyMaterialResolved,
            String keyName,
            String keyNameLower,
            long regenSeconds,
            int maxRolls,
            Map<Rarity, Integer> rarityWeights,
            int nothingWeight,
            Map<String, Integer> materialWeights,
            int shardsMin,
            int shardsMax) {

        boolean requiresKey() {
            return keyId != null && !keyId.isEmpty();
        }

        public boolean isFurniture() {
            return furnitureId != null && !furnitureId.isEmpty();
        }
    }

    /** One tracked container's location + regen state. Persisted as JSON (see {@link #byLocation}). */
    private static final class ContainerRecord {
        String world;
        int x;
        int y;
        int z;
        String type;
        long lastOpened;
        /**
         * Furniture-backed containers only — the placed {@link ItemDisplay}'s
         * UUID. Nexo's {@code FurnitureMechanic#place} applies its own
         * spawn-location correction (hitbox height, solid-ground snap) after
         * we hand it a target Location, so the entity's actual Location can
         * end up in a different block than the one this record is keyed under
         * in {@link #byLocation}. Interact lookups resolve through
         * {@link #byEntity} (this UUID) instead, which is exact regardless of
         * that correction — see ContainerFurnitureListener (2026-09-21 fix for
         * "this is not a glitch container" on the first click).
         */
        String entityUuid;

        ContainerRecord() {}

        ContainerRecord(String world, int x, int y, int z, String type, long lastOpened) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.lastOpened = lastOpened;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type RECORD_LIST_TYPE = new com.google.gson.reflect.TypeToken<List<ContainerRecord>>() {}.getType();

    private final GlitchItems plugin;
    private volatile Map<String, ContainerType> types = new HashMap<>();
    private volatile Map<String, ContainerType> furnitureTypes = new HashMap<>();

    // Cached config
    private volatile Set<String> enabledWorlds = Set.of("glitch_red", "glitch_pve");
    private volatile int scavengeBonusRolls = 1;
    private volatile Map<String, String> messagesRaw = new HashMap<>();

    // Location-keyed container state (replaces per-block PersistentDataContainer —
    // Nexo furniture is entity-based and cannot carry block PDC; see class javadoc).
    private final File dataFile;
    private final Map<String, ContainerRecord> byLocation = new ConcurrentHashMap<>();
    /** Furniture-only secondary index — see {@link ContainerRecord#entityUuid}. */
    private final Map<UUID, ContainerRecord> byEntity = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public ContainerManager(GlitchItems plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "data");
        this.dataFile = new File(dir, "containers.json");
        reload();
        loadContainers();
    }

    public void reload() {
        Map<String, ContainerType> loaded = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("containers.types");
        if (section == null) {
            plugin.getLogger().warning("No 'containers.types' section in config.yml — "
                    + "container types are empty. Restore the default config or add the section.");
        } else {
            for (String name : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(name);
                if (t == null) continue;
                Material material = Material.matchMaterial(t.getString("material", "CHEST"));
                if (material == null) material = Material.CHEST;
                String furnitureId = t.getString("furniture-id", "").trim();

                Map<Rarity, Integer> rarityWeights = new LinkedHashMap<>();
                int nothingWeight = 0;
                ConfigurationSection drops = t.getConfigurationSection("drops");
                if (drops != null) {
                    for (String rarityId : drops.getKeys(false)) {
                        if (rarityId.equals("nothing")) {
                            nothingWeight = Math.max(0, drops.getInt("nothing"));
                            continue;
                        }
                        Rarity rarity = Rarity.fromId(rarityId);
                        if (rarity != null) {
                            rarityWeights.put(rarity, Math.max(0, drops.getInt(rarityId)));
                        }
                    }
                }
                Map<String, Integer> materialWeights = new HashMap<>();
                ConfigurationSection materials = t.getConfigurationSection("materials");
                if (materials != null) {
                    for (String id : materials.getKeys(false)) {
                        materialWeights.put(id, Math.max(0, materials.getInt(id)));
                    }
                }
                String keyId = t.getString("key-id", "");
                String keyMatStr = t.getString("key-material", "");
                Material keyMatResolved = null;
                if (!keyMatStr.isEmpty()) {
                    try {
                        keyMatResolved = Material.valueOf(keyMatStr.toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Unknown key-material '" + keyMatStr + "' for container " + name);
                    }
                }
                String keyName = t.getString("key-name", "");
                String keyNameLower = keyName.toLowerCase(java.util.Locale.ROOT);

                loaded.put(name, new ContainerType(
                        name,
                        t.getString("display", name),
                        material,
                        furnitureId.isEmpty() ? null : furnitureId,
                        keyId,
                        keyMatStr,
                        keyMatResolved,
                        keyName,
                        keyNameLower,
                        t.getLong("regen-seconds", 600),
                        Math.max(1, t.getInt("max-rolls", 3)),
                        rarityWeights,
                        nothingWeight,
                        materialWeights,
                        t.getInt("shards-min", 0),
                        t.getInt("shards-max", 0)));
            }
        }
        types = loaded;
        Map<String, ContainerType> furnIndex = new HashMap<>();
        for (ContainerType t : loaded.values()) {
            if (t.isFurniture()) furnIndex.put(t.furnitureId(), t);
        }
        furnitureTypes = furnIndex;
        enabledWorlds = Set.copyOf(plugin.getConfig().getStringList("containers.enabled-worlds"));
        if (enabledWorlds.isEmpty()) enabledWorlds = Set.of("glitch_red", "glitch_pve");
        scavengeBonusRolls = plugin.getConfig().getInt("containers.scavenge-bonus-rolls", 1);
        Map<String, String> msgs = new HashMap<>();
        ConfigurationSection msgSec = plugin.getConfig().getConfigurationSection("containers.messages");
        if (msgSec != null) {
            for (String k : msgSec.getKeys(false)) msgs.put(k, msgSec.getString(k, "<gray>" + k + "</gray>"));
        }
        messagesRaw = msgs;
        plugin.getLogger().info("Containers loaded: " + loaded.size() + " types (" + furnIndex.size() + " furniture-backed)");
    }

    public List<ContainerType> getTypes() {
        return new ArrayList<>(types.values());
    }

    public ContainerType getType(String name) {
        return name == null ? null : types.get(name);
    }

    /** Reverse lookup for {@code NexoFurnitureInteractEvent} handling. */
    public ContainerType typeForFurniture(String furnitureId) {
        return furnitureId == null ? null : furnitureTypes.get(furnitureId);
    }

    // ---- Location-keyed state (authoritative for both backends) ----

    private static String locKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private static String locKey(Location loc) {
        return locKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public ContainerType typeOf(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        ContainerRecord record = byLocation.get(locKey(loc));
        return record == null ? null : types.get(record.type);
    }

    public boolean isContainer(Location loc) {
        return typeOf(loc) != null;
    }

    public ContainerType typeOf(Block block) {
        return block == null ? null : typeOf(block.getLocation());
    }

    public boolean isContainer(Block block) {
        return typeOf(block) != null;
    }

    /**
     * Marks {@code loc} as a container of {@code type}. For furniture-backed
     * types this places a Nexo furniture entity (loc must be an air block with
     * solid ground below — same placement rule {@link ScatterManager} already
     * enforces); for legacy types it sets the block material directly.
     */
    public boolean mark(Location loc, ContainerType type) {
        if (loc == null || loc.getWorld() == null || type == null) return false;
        UUID entityUuid = null;
        if (type.isFurniture()) {
            if (!NexoUtil.available()) {
                plugin.getLogger().warning("[Containers] Nexo not available — cannot place furniture container " + type.furnitureId());
                return false;
            }
            ItemDisplay placed;
            try {
                placed = NexoFurniture.place(type.furnitureId(), loc, Rotation.NONE, BlockFace.UP);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Containers] Failed to place furniture '" + type.furnitureId() + "' at " + locKey(loc), e);
                return false;
            }
            if (placed == null) return false;
            entityUuid = placed.getUniqueId();
        } else {
            loc.getBlock().setType(type.material());
        }
        ContainerRecord record = new ContainerRecord(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), type.name(), 0L);
        record.entityUuid = entityUuid == null ? null : entityUuid.toString();
        byLocation.put(locKey(loc), record);
        if (entityUuid != null) byEntity.put(entityUuid, record);
        dirty.set(true);
        return true;
    }

    /** Legacy block overload — {@code block}'s own location becomes the container. */
    public boolean mark(Block block, ContainerType type) {
        return block != null && mark(block.getLocation(), type);
    }

    /**
     * Clears the container record at {@code loc} and removes its visual
     * (furniture entity, or resets the block to AIR for legacy types).
     */
    public void clear(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String key = locKey(loc);
        ContainerRecord record = byLocation.remove(key);
        dirty.set(true);
        ContainerType type = record == null ? null : types.get(record.type);
        if (type != null && type.isFurniture()) {
            UUID entityUuid = null;
            if (record.entityUuid != null) {
                try {
                    entityUuid = UUID.fromString(record.entityUuid);
                    byEntity.remove(entityUuid);
                } catch (IllegalArgumentException ignored) {
                }
            }
            // Prefer removing the exact tracked entity — NexoFurniture.remove(Location)
            // does a positional search that can miss if the entity's actual
            // Location drifted from the one it was placed/tracked under (see
            // ContainerRecord#entityUuid javadoc), leaving a stale, untracked
            // furniture entity behind that never gets cleared on later scatter
            // cycles (2026-09-21 bug report: loot piling up in the same spot).
            Entity entity = entityUuid == null ? null : Bukkit.getEntity(entityUuid);
            try {
                if (entity != null) {
                    NexoFurniture.remove(entity);
                } else {
                    NexoFurniture.remove(loc);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "[Containers] Failed to remove furniture at " + key, e);
            }
        } else {
            try {
                loc.getBlock().setType(Material.AIR, false);
            } catch (Exception ignored) {
            }
        }
    }

    public void clear(Block block) {
        if (block != null) clear(block.getLocation());
    }

    // ---- Persistence (mirrors ScatterManager's Gson atomic-write pattern) ----

    private void loadContainers() {
        if (!dataFile.exists()) {
            plugin.getLogger().info("[Containers] No prior containers.json — starting fresh.");
            return;
        }
        try (Reader r = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
            List<ContainerRecord> loaded = GSON.fromJson(r, RECORD_LIST_TYPE);
            if (loaded != null) {
                for (ContainerRecord rec : loaded) {
                    if (rec == null || rec.world == null || rec.world.isBlank() || rec.type == null) continue;
                    byLocation.put(locKey(rec.world, rec.x, rec.y, rec.z), rec);
                    if (rec.entityUuid != null) {
                        try {
                            byEntity.put(UUID.fromString(rec.entityUuid), rec);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            plugin.getLogger().info("[Containers] Loaded " + byLocation.size() + " tracked container(s) from " + dataFile.getPath());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Containers] Failed to load " + dataFile.getPath() + " — starting fresh.", e);
        }
    }

    /** Persists {@link #byLocation} if it has changed since the last flush. No-op otherwise. */
    public void flush() {
        if (!dirty.compareAndSet(true, false)) return;
        List<ContainerRecord> snapshot = new ArrayList<>(byLocation.values());
        try {
            File dir = dataFile.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs() && !dir.exists()) {
                plugin.getLogger().warning("[Containers] Could not create data dir " + dir.getPath());
                return;
            }
            File tmp = new File(dir, dataFile.getName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, w);
            }
            try {
                Files.move(tmp.toPath(), dataFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Containers] Failed to save " + dataFile.getPath(), e);
        }
    }

    // ---- Scatter bridge (for GlitchStash AutoExtractScheduler reflection) ----
    // GlitchStash probes GlitchItems#getContainerManager() for scatter entry points.
    // These aliases delegate to ScatterManager so extracted cycles can trigger
    // scatter even if the event hook is not yet wired.
    public void scatter() { try { GlitchItems.getInstance().getScatterManager().scatterNow(); } catch (Exception ignored) {} }
    public void resetContainers() { scatter(); }
    public void onCycleEnd() { scatter(); }
    public void handleCycleEnd() { scatter(); }
    public void doScatter() { scatter(); }

    public boolean open(Player player, Block block) {
        return block != null && open(player, block.getLocation());
    }

    public boolean open(Player player, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            player.sendMessage(msg("not-container"));
            return false;
        }
        ContainerRecord record = byLocation.get(locKey(loc));
        return openRecord(player, record);
    }

    /**
     * Furniture interact path — looks up by the entity's own UUID first
     * (exact, see {@link ContainerRecord#entityUuid}), falling back to its
     * current Location only for records tracked before this index existed.
     */
    public boolean open(Player player, Entity entity) {
        if (entity == null) {
            player.sendMessage(msg("not-container"));
            return false;
        }
        ContainerRecord record = byEntity.get(entity.getUniqueId());
        if (record == null) {
            record = byLocation.get(locKey(entity.getLocation()));
        }
        return openRecord(player, record);
    }

    private boolean openRecord(Player player, ContainerRecord record) {
        ContainerType type = record == null ? null : types.get(record.type);
        if (type == null) {
            player.sendMessage(msg("not-container"));
            return false;
        }
        if (!enabledWorlds.contains(record.world)) {
            player.sendMessage(msg("disabled-world"));
            return false;
        }
        Location loc = new Location(Bukkit.getWorld(record.world), record.x, record.y, record.z);

        long now = System.currentTimeMillis();
        long remaining = record.lastOpened + type.regenSeconds() * 1000L - now;
        if (remaining > 0) {
            player.sendMessage(msg("not-ready", "<container>", type.display(),
                    "<time>", String.valueOf((remaining + 999L) / 1000L)));
            return false;
        }

        if (type.requiresKey() && !hasKey(player, type)) {
            player.sendMessage(msg("need-key", "<container>", type.display(), "<key>", keyDisplayName(type)));
            return false;
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int luck = plugin.getGlitchManager().lootLuckBonus(player);

        List<ItemStack> loot = new ArrayList<>();
        boolean surged = false;

        int rolls = type.maxRolls();
        if (player.getScoreboardTags().contains(SCAVENGE_TAG)) {
            rolls += scavengeBonusRolls;
        }
        for (int i = 0; i < rolls; i++) {
            Rarity rarity = rollRarity(type, rand);
            if (rarity == null) continue;
            if (luck > 0 && rand.nextInt(100) < luck) {
                rarity = upgrade(rarity);
            }
            loot.add(buildRift(rarity));
        }
        if (luck > 0 && rand.nextInt(100) < luck) {
            Rarity surge = rollRarity(type, rand);
            if (surge != null) {
                loot.add(buildRift(upgrade(surge)));
                surged = true;
            }
        }

        for (Map.Entry<String, Integer> entry : type.materialWeights().entrySet()) {
            if (entry.getValue() > 0 && rand.nextInt(100) < entry.getValue()) {
                loot.add(buildMaterial(entry.getKey()));
            }
        }

        boolean emptied = loot.isEmpty();
        if (type.requiresKey() && !emptied) {
            consumeKey(player, type);
        }
        // Hook: count loot toward active GlitchRaid (if installed) — fixes raid loot not ticking for containers
        if (!loot.isEmpty()) {
            try {
                org.bukkit.plugin.Plugin raidPlugin = Bukkit.getPluginManager().getPlugin("GlitchRaid");
                if (raidPlugin != null && raidPlugin.isEnabled()) {
                    Object raidMgr = cachedRaidMethod(raidPlugin, "getRaidManager", 0).invoke(raidPlugin);
                    if (raidMgr != null) {
                        java.util.UUID pid = player.getUniqueId();
                        Boolean inRaid = (Boolean) cachedRaidMethod(raidMgr, "isInRaid", 1).invoke(raidMgr, pid);
                        if (Boolean.TRUE.equals(inRaid)) {
                            try {
                                cachedRaidMethod(raidMgr, "addLootFromItems", 2).invoke(raidMgr, player, loot);
                            } catch (NoSuchMethodException nsme) {
                                int est = 0;
                                for (ItemStack s : loot) {
                                    if (s != null && !s.getType().isAir()) est += s.getAmount() * 10;
                                }
                                cachedRaidMethod(raidMgr, "addLoot", 3).invoke(raidMgr, pid, est);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        giveLoot(player, loc, loot);

        if (type.shardsMin() > 0 && type.shardsMax() >= type.shardsMin()) {
            int shards = rand.nextInt(type.shardsMin(), type.shardsMax() + 1);
            if (shards > 0 && depositShards(player, shards)) {
                player.sendMessage(msg("shards", "<amount>", String.valueOf(shards)));
            }
        }

        record.lastOpened = now;
        dirty.set(true);

        if (emptied && !surged) {
            player.sendMessage(msg("emptied", "<container>", type.display()));
            return false;
        }
        player.sendMessage(msg("looted", "<container>", type.display()));
        if (surged) {
            player.sendMessage(msg("surge"));
        }
        return true;
    }

    private Rarity rollRarity(ContainerType type, ThreadLocalRandom rand) {
        int total = type.nothingWeight();
        for (int weight : type.rarityWeights().values()) {
            total += weight;
        }
        if (total <= 0) return null;
        int pick = rand.nextInt(total);
        for (Map.Entry<Rarity, Integer> entry : type.rarityWeights().entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Rarity upgrade(Rarity rarity) {
        if (rarity == Rarity.LEGENDARY) return rarity;
        return Rarity.values()[rarity.getTier() + 1];
    }

    private boolean hasKey(Player player, ContainerType type) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && isKey(stack, type)) return true;
        }
        return isKey(inv.getItemInOffHand(), type);
    }

    private void consumeKey(Player player, ContainerType type) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && isKey(stack, type)) {
                if (stack.getAmount() > 1) stack.setAmount(stack.getAmount() - 1);
                else inv.setItem(i, null);
                return;
            }
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && isKey(offhand, type)) {
            if (offhand.getAmount() > 1) offhand.setAmount(offhand.getAmount() - 1);
            else inv.setItemInOffHand(null);
        }
    }

    private boolean isKey(ItemStack stack, ContainerType type) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!type.keyId().isEmpty()) {
            String id = NexoUtil.idOf(stack);
            if (type.keyId().equalsIgnoreCase(id)) return true;
        }
        if (!type.keyMaterial().isEmpty()) {
            if (type.keyMaterialResolved() == null) return false;
            if (stack.getType() != type.keyMaterialResolved()) return false;
            if (!type.keyName().isEmpty()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta == null || !meta.hasCustomName()) return false;
                String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(meta.customName());
                return name != null && name.toLowerCase(java.util.Locale.ROOT).contains(type.keyNameLower());
            }
            return true;
        }
        return false;
    }

    private String keyDisplayName(ContainerType type) {
        String name = type.keyName() != null && !type.keyName().isEmpty() ? type.keyName() : type.keyId();
        return type.requiresKey() && !type.keyId().isEmpty() && !name.equals(type.keyId())
                ? name + " (" + type.keyId() + ")" : name;
    }

    private ItemStack buildRift(Rarity rarity) {
        String riftId = "unstable_rift_" + rarity.getId();
        ItemStack item = NexoUtil.build(riftId);
        if (item != null) return item;
        ItemStack fallback = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = fallback.getItemMeta();
        meta.customName(MM.deserialize("<white>Unstable Rift (" + rarity.getDisplayName() + ")</white>"));
        meta.lore(List.of(
                MM.deserialize("<gray>An unstable piece of the Glitch.</gray>"),
                MM.deserialize("<gray>Identify it at the hub.</gray>")));
        meta.getPersistentDataContainer().set(NEXO_KEY, PersistentDataType.STRING, riftId);
        fallback.setItemMeta(meta);
        return fallback;
    }

    private ItemStack buildMaterial(String id) {
        ItemStack item = NexoUtil.build(id);
        if (item != null) return item;
        ItemStack fallback = new ItemStack(MATERIAL_MATERIALS.getOrDefault(id, Material.PAPER), 1);
        ItemMeta meta = fallback.getItemMeta();
        String label = id.replace('_', ' ');
        label = label.substring(0, 1).toUpperCase() + label.substring(1);
        meta.customName(MM.deserialize("<white>" + label + "</white>"));
        meta.getPersistentDataContainer().set(NEXO_KEY, PersistentDataType.STRING, id);
        fallback.setItemMeta(meta);
        return fallback;
    }

    private void giveLoot(Player player, Location loc, List<ItemStack> loot) {
        if (loot.isEmpty()) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(loot.toArray(new ItemStack[0]));
        if (!leftovers.isEmpty()) {
            Location dropLoc = loc.clone().add(0.5, 0.5, 0.5);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(dropLoc, left));
        }
    }

    private boolean depositShards(Player player, int amount) {
        var economy = plugin.getEconomy();
        if (economy == null) return false;
        economy.depositPlayer(player, amount);
        return true;
    }

    private Component msg(String key) {
        return deserializeMsg(messagesRaw.getOrDefault(key, "<gray>" + key + "</gray>"), key);
    }

    private Component msg(String key, String ph1, String v1) {
        return deserializeMsg(messagesRaw.getOrDefault(key, "<gray>" + key + "</gray>").replace(ph1, v1), key);
    }

    private Component msg(String key, String ph1, String v1, String ph2, String v2) {
        return deserializeMsg(
                messagesRaw.getOrDefault(key, "<gray>" + key + "</gray>").replace(ph1, v1).replace(ph2, v2), key);
    }

    private static Component deserializeMsg(String raw, String key) {
        try { return MM.deserialize(raw); } catch (Exception e) { return Component.text(key); }
    }
}
