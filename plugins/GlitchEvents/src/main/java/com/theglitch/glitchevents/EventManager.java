package com.theglitch.glitchevents;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schedules and runs dynamic world events: supply drops, roaming bosses,
 * and (future) extraction windows. Auto-events fire on a random interval.
 */
public final class EventManager {

    private enum EventType {
        SUPPLY_DROP,
        ROAMING_BOSS
    }

    private static final int SUPPLY_RANGE = 30;

    private final GlitchEvents plugin;
    private final Random random = new Random();
    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    /** Roaming bosses tracked for despawn, keyed by event id. */
    private final Map<UUID, LivingEntity> spawnedBosses = new ConcurrentHashMap<>();
    /** Persisted supply-drop barrel locations ("world;x;y;z") — swept/cleared on enable. */
    private final Set<String> pendingBarrels = ConcurrentHashMap.newKeySet();
    private final File barrelStoreFile;

    private volatile BukkitTask pendingAutoTask;

    // Cached config values
    private volatile Set<String> enabledWorlds = Set.of();
    private volatile boolean autoEventsEnabled = true;
    private volatile boolean supplyDropEnabled = true;
    private volatile boolean roamingBossEnabled = true;
    private volatile boolean extractionWindowEnabled = false;
    private volatile int minIntervalMinutes = 20;
    private volatile int maxIntervalMinutes = 45;
    private volatile int announcementRadiusBlocks = 100;
    private volatile int supplyDurationSeconds = 300;
    private volatile List<String> supplyItems = List.of();
    private volatile int shardsMin = 10;
    private volatile int shardsMax = 40;
    private volatile String bossMob = "GlitchSentinel";
    private volatile String bossAnnounce = "";
    private volatile int bossDespawnSeconds = 180;
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();

    private volatile long nextEventAtMillis = 0L;
    private volatile boolean warnedNoMythic = false;

    public EventManager(GlitchEvents plugin) {
        this.plugin = plugin;
        this.barrelStoreFile = new File(plugin.getDataFolder(), "placed-supply-barrels.yml");
        reload();
        sweepOrphanedBarrels();
    }

    public void reload() {
        var cfg = plugin.getConfig();

        Set<String> worlds = new HashSet<>();
        for (String name : cfg.getStringList("events.enabled-worlds")) {
            if (name != null && !name.isBlank()) {
                worlds.add(name.toLowerCase(Locale.ROOT));
            }
        }
        enabledWorlds = Set.copyOf(worlds);

        autoEventsEnabled = cfg.getBoolean("events.auto-events-enabled", true);
        minIntervalMinutes = Math.max(1, cfg.getInt("events.min-interval-minutes", 20));
        maxIntervalMinutes = Math.max(minIntervalMinutes, cfg.getInt("events.max-interval-minutes", 45));
        announcementRadiusBlocks = Math.max(0, cfg.getInt("events.announcement-radius-blocks", 100));

        supplyDropEnabled = cfg.getBoolean("supply-drop.enabled", true);
        supplyDurationSeconds = Math.max(10, cfg.getInt("supply-drop.duration-seconds", 300));
        supplyItems = List.copyOf(cfg.getStringList("supply-drop.items"));
        shardsMin = Math.max(0, cfg.getInt("supply-drop.shards-min", 10));
        shardsMax = Math.max(shardsMin, cfg.getInt("supply-drop.shards-max", 40));

        roamingBossEnabled = cfg.getBoolean("roaming-boss.enabled", true);
        bossMob = cfg.getString("roaming-boss.mob", "GlitchSentinel");
        bossAnnounce = cfg.getString("roaming-boss.announce", "");
        bossDespawnSeconds = Math.max(10, cfg.getInt("roaming-boss.despawn-seconds", 180));

        extractionWindowEnabled = cfg.getBoolean("extraction-window.enabled", false);

        messageCache.clear();
        ConfigurationSection messages = cfg.getConfigurationSection("messages");
        if (messages != null) {
            for (String key : messages.getKeys(false)) {
                String raw = messages.getString(key);
                if (raw != null) {
                    messageCache.put(key, raw);
                }
            }
        }

        scheduleNextEvent();
    }

    public void scheduleNextEvent() {
        cancelPendingAutoTask();
        nextEventAtMillis = 0L;
        if (!autoEventsEnabled || (!supplyDropEnabled && !roamingBossEnabled) || enabledWorlds.isEmpty()) {
            return;
        }
        int span = maxIntervalMinutes - minIntervalMinutes + 1;
        int minutes = minIntervalMinutes + random.nextInt(span);
        long ticks = minutes * 60L * 20L;
        nextEventAtMillis = System.currentTimeMillis() + minutes * 60_000L;
        pendingAutoTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingAutoTask = null;
            runRandomEvent();
            scheduleNextEvent();
        }, ticks);
    }

    private void runRandomEvent() {
        World world = pickEnabledWorld();
        if (world == null) {
            return;
        }
        EventType type = pickEventType();
        switch (type) {
            case SUPPLY_DROP -> startSupplyDrop(world);
            case ROAMING_BOSS -> {
                Player anchor = randomPlayerIn(world);
                if (anchor != null) {
                    startRoamingBoss(anchor);
                }
            }
        }
    }

    private EventType pickEventType() {
        if (supplyDropEnabled && roamingBossEnabled) {
            return random.nextBoolean() ? EventType.SUPPLY_DROP : EventType.ROAMING_BOSS;
        }
        return supplyDropEnabled ? EventType.SUPPLY_DROP : EventType.ROAMING_BOSS;
    }

    public boolean startSupplyDrop(World world) {
        Player anchor = randomPlayerIn(world);
        if (anchor == null) {
            return false;
        }
        Location base = anchor.getLocation();
        Block target = null;
        Location spot = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            int dx = random.nextInt(SUPPLY_RANGE * 2 + 1) - SUPPLY_RANGE;
            int dz = random.nextInt(SUPPLY_RANGE * 2 + 1) - SUPPLY_RANGE;
            int x = base.getBlockX() + dx;
            int z = base.getBlockZ() + dz;
            int y = world.getHighestBlockYAt(x, z) + 1;
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isAir()) {
                target = block;
                spot = new Location(world, x + 0.5, y, z + 0.5);
                break;
            }
        }
        if (target == null || !target.getType().isAir()) {
            plugin.getLogger().warning("Supply drop skipped — no air spot found near a player in " + world.getName() + ".");
            return false;
        }

        target.setType(Material.BARREL);
        fillBarrel(target);
        // Persist so a stop/restart cannot orphan the barrel — swept on next enable
        persistBarrelKey(barrelKey(target), true);

        String coords = spot.getBlockX() + ", " + spot.getBlockY() + ", " + spot.getBlockZ();
        broadcastNear(spot, Messages.msg(plugin, "supply-drop-start",
                "world", world.getName(), "x", String.valueOf(spot.getBlockX()),
                "y", String.valueOf(spot.getBlockY()), "z", String.valueOf(spot.getBlockZ())));
        plugin.getLogger().info("Supply drop placed at " + coords + " (" + world.getName() + ").");

        UUID eventId = UUID.randomUUID();
        // Lambdas capture only effectively-final locals — snapshot the mutable
        // Block/Location references before scheduling the removal task.
        final Block dropBlock = target;
        final Location dropSpot = spot;
        final World dropWorld = world;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeTasks.remove(eventId);
            persistBarrelKey(barrelKey(dropBlock), false);
            if (dropBlock.getType() == Material.BARREL) {
                dropBlock.setType(Material.AIR);
            }
            broadcastNear(dropSpot, Messages.msg(plugin, "supply-drop-end",
                    "world", dropWorld.getName(), "x", String.valueOf(dropSpot.getBlockX()),
                    "y", String.valueOf(dropSpot.getBlockY()), "z", String.valueOf(dropSpot.getBlockZ())));
        }, supplyDurationSeconds * 20L);
        activeTasks.put(eventId, task);
        return true;
    }

    // ---- Supply-drop barrel persistence ------------------------------------------

    private String barrelKey(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    /** Adds/removes a barrel location from the persisted store file. */
    private void persistBarrelKey(String key, boolean add) {
        if (add) {
            pendingBarrels.add(key);
        } else {
            pendingBarrels.remove(key);
        }
        try {
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("barrels", new ArrayList<>(pendingBarrels));
            yml.save(barrelStoreFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist supply barrel store: " + e.getMessage());
        }
    }

    /**
     * Removes any supply-drop barrels left over from a previous run (no content drops),
     * then clears the store. Called on enable.
     */
    public void sweepOrphanedBarrels() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(barrelStoreFile);
        List<String> stored = yml.getStringList("barrels");
        pendingBarrels.clear();
        int removed = 0;
        for (String key : stored) {
            String[] parts = key.split(";");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                // World not loaded yet — keep the entry for the next sweep
                pendingBarrels.add(key);
                continue;
            }
            try {
                Block block = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (block.getType() == Material.BARREL) {
                    block.setType(Material.AIR);
                    removed++;
                }
            } catch (Exception ignored) {}
        }
        try {
            YamlConfiguration save = new YamlConfiguration();
            save.set("barrels", new ArrayList<>(pendingBarrels));
            save.save(barrelStoreFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to clear supply barrel store: " + e.getMessage());
        }
        if (removed > 0) {
            plugin.getLogger().info("Swept " + removed + " orphaned supply-drop barrel(s) from a previous run.");
        }
    }

    private void fillBarrel(Block barrelBlock) {
        if (!(barrelBlock.getState() instanceof Barrel barrel)) {
            return;
        }
        Inventory inv = barrel.getInventory();
        for (String itemName : supplyItems) {
            ItemStack stack = null;
            // 1) Try Oraxen (custom items like unstable_rift_common, rune_fragment)
            try {
                Class<?> oraxenItems = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
                java.lang.reflect.Method getById = oraxenItems.getMethod("getItemById", String.class);
                Object builder = getById.invoke(null, itemName);
                if (builder != null) {
                    java.lang.reflect.Method build = builder.getClass().getMethod("build");
                    Object result = build.invoke(builder);
                    if (result instanceof ItemStack s) stack = s;
                }
            } catch (Exception ignored) {}
            // 2) Fallback: try GlitchCommon OraxenUtil reflectively
            if (stack == null) {
                try {
                    Class<?> util = Class.forName("com.theglitch.common.OraxenUtil");
                    java.lang.reflect.Method build = util.getMethod("build", String.class);
                    Object res = build.invoke(null, itemName);
                    if (res instanceof ItemStack s) stack = s;
                } catch (Exception ignored2) {}
            }
            // 3) Fallback: vanilla material
            if (stack == null) {
                Material mat = Material.matchMaterial(itemName);
                if (mat == null || !mat.isItem()) {
                    plugin.getLogger().warning("Supply drop: unknown item '" + itemName + "' — skipping (not Oraxen nor vanilla).");
                    continue;
                }
                stack = new ItemStack(mat);
            }
            int slot = inv.firstEmpty();
            if (slot < 0) break;
            inv.setItem(slot, stack);
        }
        int shards = shardsMin + random.nextInt(shardsMax - shardsMin + 1);
        int slot = inv.firstEmpty();
        if (slot >= 0) {
            inv.setItem(slot, new ItemStack(Material.AMETHYST_SHARD, Math.min(shards, 64)));
        }
        // Ensure barrel state is saved — use forced update for Folia/Paper
        try { barrel.update(true, false); } catch (Exception e) { try { barrel.update(); } catch (Exception ignored) {} }
        plugin.getLogger().info("Supply drop filled barrel at " + barrelBlock.getX() + "," + barrelBlock.getY() + "," + barrelBlock.getZ() + " with " + inv.getContents().length + " slots (" + supplyItems + " + " + shards + " shards).");
    }

    public boolean startRoamingBoss(Player near) {
        World world = near.getWorld();
        Location base = near.getLocation();
        int dx = random.nextInt(SUPPLY_RANGE * 2 + 1) - SUPPLY_RANGE;
        int dz = random.nextInt(SUPPLY_RANGE * 2 + 1) - SUPPLY_RANGE;
        int x = base.getBlockX() + dx;
        int z = base.getBlockZ() + dz;
        int y = world.getHighestBlockYAt(x, z) + 1;

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            if (!warnedNoMythic) {
                warnedNoMythic = true;
                plugin.getLogger().warning("MythicMobs not installed — roaming boss '" + bossMob + "' spawn skipped.");
            }
            return false;
        }

        UUID eventId = UUID.randomUUID();
        // Snapshot nearby living entities BEFORE spawning so we can identify the new boss
        final Location spawnCenter = new Location(world, x + 0.5, y, z + 0.5);
        Set<UUID> before = new HashSet<>();
        for (Entity e : world.getNearbyEntities(spawnCenter, 32, 32, 32)) {
            if (e instanceof LivingEntity) {
                before.add(e.getUniqueId());
            }
        }

        String command = "mm mobs spawn " + bossMob + " " + world.getName() + " " + x + " " + y + " " + z;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to dispatch MythicMobs spawn: " + e.getMessage());
            return false;
        }

        String mob = bossMob;
        broadcastAll(Messages.deserializeRaw(bossAnnounce));
        Component spawnedMsg = Messages.msg(plugin, "boss-spawned",
                "mob", mob, "world", world.getName(),
                "x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z));
        Bukkit.getConsoleSender().sendMessage(spawnedMsg);

        // Short delayed scan to capture the spawned boss so the despawn task can remove it
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            LivingEntity boss = null;
            for (Entity e : world.getNearbyEntities(spawnCenter, 32, 32, 32)) {
                if (e instanceof LivingEntity living && !before.contains(living.getUniqueId())) {
                    boss = living;
                    break;
                }
            }
            if (boss != null) {
                spawnedBosses.put(eventId, boss);
                plugin.getLogger().fine("Roaming boss tracked for despawn (" + boss.getType() + ") in " + world.getName());
            } else {
                plugin.getLogger().fine("Roaming boss scan found no new living entity near " + world.getName() + " " + x + "," + y + "," + z + " — despawn will not remove anything.");
            }
        }, 40L);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeTasks.remove(eventId);
            // Kill the tracked boss so bosses do not accumulate past the despawn timer
            LivingEntity boss = spawnedBosses.remove(eventId);
            if (boss != null && boss.isValid()) {
                boss.remove();
            }
            broadcastAll(Messages.msg(plugin, "boss-despawned", "mob", mob));
        }, bossDespawnSeconds * 20L);
        activeTasks.put(eventId, task);
        return true;
    }

    public void cancelAll() {
        cancelPendingAutoTask();
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        spawnedBosses.clear();
        nextEventAtMillis = 0L;
    }

    private void cancelPendingAutoTask() {
        BukkitTask task = pendingAutoTask;
        pendingAutoTask = null;
        if (task != null) {
            task.cancel();
        }
    }

    public World pickEnabledWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (enabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT))) {
                return world;
            }
        }
        return null;
    }

    private Player randomPlayerIn(World world) {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && !player.isDead()) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return null;
        }
        return players.get(random.nextInt(players.size()));
    }

    private void broadcastNear(Location origin, Component component) {
        int radius = announcementRadiusBlocks;
        double radiusSq = (double) radius * (double) radius;
        World originWorld = origin.getWorld();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(originWorld)) {
                continue;
            }
            if (radius > 0) {
                Location playerLoc = player.getLocation();
                if (playerLoc.distanceSquared(origin) > radiusSq) {
                    continue;
                }
            }
            player.sendMessage(component);
        }
    }

    private void broadcastAll(Component component) {
        Bukkit.getConsoleSender().sendMessage(component);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    public String getMessageRaw(String key) {
        return messageCache.get(key);
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    public long getNextEventAtMillis() {
        return nextEventAtMillis;
    }

    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public boolean isAutoEventsEnabled() {
        return autoEventsEnabled;
    }

    public boolean isSupplyDropEnabled() {
        return supplyDropEnabled;
    }

    public boolean isRoamingBossEnabled() {
        return roamingBossEnabled;
    }

    public boolean isExtractionWindowEnabled() {
        return extractionWindowEnabled;
    }

    public int getMinIntervalMinutes() {
        return minIntervalMinutes;
    }

    public int getMaxIntervalMinutes() {
        return maxIntervalMinutes;
    }
}
