package com.theglitch.glitchitems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Automatic loot scatter for RED WORLD only.
 * <p>
 * Runs every {@code scatter.interval-minutes} (default 30) on the global region
 * and also hooks to {@code AutoExtractCycleEndEvent} (fired by GlitchStash at
 * t0+30m+5s) for immediate scatter right after extraction ends. The hook is
 * registered reflectively so GlitchStash is a soft-depend.
 * </p>
 * <p>
 * Each cycle:
 * <ol>
 *   <li>Clear previous scattered containers — only those we placed (persisted
 *       positions in {@code plugins/GlitchItems/data/scattered.json}). For each
 *       entry: if the block is still a Glitch container ({@link ContainerManager#typeOf(Block)}),
 *       clear its PDC and set to {@link Material#AIR}. Removal is scheduled on
 *       the owning region on Folia.</li>
 *   <li>Place new loot sparse — {@code 1 per 5-10 chunks} interpreted as
 *       {@code ~36 total} (debris 18, cache 10, vault 6, rift_vault 2) to avoid
 *       flooding a 2000x2000 border (15625 chunks). Counts are config-driven;
 *       falls back to {@code chunks-per-container} density if counts empty.</li>
 *   <li>Only on top of solid ground — target block must be {@code AIR}, block
 *       above must be {@code AIR}, ground must be {@code isSolid()}, not
 *       {@code LEAVES}/{@code LOGS}, not liquid, not already a container, and
 *       not inside a WorldGuard protected region (except {@code __global__}).</li>
 * </ol>
 * <p>
 * Folia-safe: block reads/writes go through {@link FoliaScheduler#runAtLocation(org.bukkit.plugin.Plugin, Location, Runnable)}
 * on Folia; chunk handling checks {@link World#isChunkLoaded(int, int)} and
 * prefers {@link World#getChunkAtAsync(int, int, boolean)} when available. On
 * Purpur the same code runs synchronously on the global/Bukkit scheduler.
 * </p>
 * <p>
 * Persistence is atomic: write to {@code .tmp} then move. Nulls are guarded;
 * malformed config falls back to defaults with warnings.
 * </p>
 */
public final class ScatterManager {

    // ------------------------------------------------------------------------
    // Persisted model
    // ------------------------------------------------------------------------

    /**
     * One placed scattered container. Stored as JSON so clearing is precise
     * (only our placements, never player builds).
     */
    public static final class ScatteredPos {
        public String world;
        public int x;
        public int y;
        public int z;
        public String type; // ContainerType.name
        public long placedAt;

        // For Gson
        public ScatteredPos() {}

        public ScatteredPos(String world, int x, int y, int z, String type, long placedAt) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.placedAt = placedAt;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ScatteredPos>>() {}.getType();

    // ------------------------------------------------------------------------
    // Config defaults
    // ------------------------------------------------------------------------

    private static final int DEFAULT_INTERVAL_MINUTES = 31;
    private static final int DEFAULT_BORDER_RADIUS = 1000; // 2000x2000 border → 1000 radius from 0,0
    private static final int DEFAULT_CHUNKS_PER_CONTAINER = 8;
    private static final int DEFAULT_MAX_ATTEMPTS = 50;
    private static final boolean DEFAULT_CLEAR_PREVIOUS = true;
    private static final boolean DEFAULT_ON_TOP_ONLY = true;
    private static final Map<String, Integer> DEFAULT_COUNTS;
    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        // 2026-08-24: increased density per operator request "2/3 per 5-10 chunks" —
        // previous 36 total (~1/430 chunks) was too sparse. New 145 total (~1/107 chunks)
        // is ~4x denser, playable without TPS collapse (2 per 5 chunks naive would be 6250).
        m.put("debris", 70);
        m.put("cache", 40);
        m.put("vault", 25);
        m.put("rift_vault", 10);
        DEFAULT_COUNTS = Collections.unmodifiableMap(m);
    }

    private final GlitchItems plugin;
    private final ContainerManager containers;

    // Cached config (volatile for cross-thread reads on Folia)
    private volatile boolean enabled = true;
    private volatile int intervalMinutes = DEFAULT_INTERVAL_MINUTES;
    private volatile Set<String> enabledWorlds = Set.of("glitch_red");
    private volatile boolean clearPrevious = DEFAULT_CLEAR_PREVIOUS;
    private volatile boolean onTopOnly = DEFAULT_ON_TOP_ONLY;
    private volatile int borderRadius = DEFAULT_BORDER_RADIUS;
    private volatile int chunksPerContainer = DEFAULT_CHUNKS_PER_CONTAINER;
    private volatile int maxAttemptsPerContainer = DEFAULT_MAX_ATTEMPTS;
    private volatile Map<String, Integer> counts = new LinkedHashMap<>(DEFAULT_COUNTS);
    private volatile String broadcastMessage = "<gray>Glitch energy coalesces — <white>{total}</white> caches scattered across the Red World.";
    private volatile boolean broadcastEnabled = true;

    // Runtime
    private final File dataFile;
    private final List<ScatteredPos> scattered = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean scatterLock = new AtomicBoolean(false);
    private final AtomicBoolean wgWarned = new AtomicBoolean(false);
    private volatile FoliaScheduler.Cancellable scheduledTask;
    private volatile Listener cycleListener; // reflectively registered AutoExtractCycleEndEvent hook

    // Folia detection cached
    private static final boolean HAS_CHUNK_ASYNC;
    static {
        boolean has = false;
        try {
            World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            has = true;
        } catch (NoSuchMethodException ignored) {
            try {
                World.class.getMethod("getChunkAtAsync", int.class, int.class);
                has = true;
            } catch (NoSuchMethodException ignored2) {
                has = false;
            }
        }
        HAS_CHUNK_ASYNC = has;
    }

    public ScatterManager(GlitchItems plugin, ContainerManager containers) {
        this.plugin = plugin;
        this.containers = containers;
        File dir = new File(plugin.getDataFolder(), "data");
        // Ensure data dir exists lazily on load/save
        this.dataFile = new File(dir, "scattered.json");
        reload();
        loadData();
        startScheduler();
        registerCycleHook();
    }

    // ------------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------------

    /**
     * Reload cached scatter config from {@code config.yml#scatter}. Validates
     * and clamps values, logs warnings on bad input, falls back to defaults.
     */
    public void reload() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("scatter");
        if (sec == null) {
            plugin.getLogger().warning("[Scatter] Missing 'scatter' section — using defaults (enabled=true, 30m, glitch_red, 36 containers).");
            applyDefaults();
            return;
        }

        enabled = sec.getBoolean("enabled", true);

        int interval = sec.getInt("interval-minutes", DEFAULT_INTERVAL_MINUTES);
        if (interval < 1 || interval > 1440) {
            plugin.getLogger().warning("[Scatter] Invalid interval-minutes " + interval + " — clamped to " + DEFAULT_INTERVAL_MINUTES + ".");
            interval = Math.max(1, Math.min(interval, 1440));
        }
        intervalMinutes = interval;

        List<String> worlds = sec.getStringList("enabled-worlds");
        if (worlds == null || worlds.isEmpty()) {
            plugin.getLogger().warning("[Scatter] enabled-worlds empty — defaulting to [glitch_red].");
            enabledWorlds = Set.of("glitch_red");
        } else {
            // Requirement: RED WORLD only — honor the allow-list but normalize
            Set<String> norm = new java.util.HashSet<>();
            for (String w : worlds) {
                if (w != null && !w.isBlank()) norm.add(w.trim());
            }
            if (norm.isEmpty()) norm.add("glitch_red");
            enabledWorlds = Set.copyOf(norm);
        }

        clearPrevious = sec.getBoolean("clear-previous", DEFAULT_CLEAR_PREVIOUS);
        onTopOnly = sec.getBoolean("on-top-only", DEFAULT_ON_TOP_ONLY);

        int radius = sec.getInt("border-radius", DEFAULT_BORDER_RADIUS);
        if (radius < 100 || radius > 10000) {
            plugin.getLogger().warning("[Scatter] Invalid border-radius " + radius + " — clamped to " + DEFAULT_BORDER_RADIUS + ".");
            radius = Math.max(100, Math.min(radius, 10000));
        }
        borderRadius = radius;

        int cpc = sec.getInt("chunks-per-container", DEFAULT_CHUNKS_PER_CONTAINER);
        if (cpc < 1 || cpc > 1000) {
            plugin.getLogger().warning("[Scatter] Invalid chunks-per-container " + cpc + " — clamped to " + DEFAULT_CHUNKS_PER_CONTAINER + ".");
            cpc = Math.max(1, Math.min(cpc, 1000));
        }
        chunksPerContainer = cpc;

        int attempts = sec.getInt("max-attempts-per-container", DEFAULT_MAX_ATTEMPTS);
        if (attempts < 5 || attempts > 500) {
            plugin.getLogger().warning("[Scatter] Invalid max-attempts-per-container " + attempts + " — clamped to " + DEFAULT_MAX_ATTEMPTS + ".");
            attempts = Math.max(5, Math.min(attempts, 500));
        }
        maxAttemptsPerContainer = attempts;

        ConfigurationSection countsSec = sec.getConfigurationSection("counts");
        if (countsSec != null && !countsSec.getKeys(false).isEmpty()) {
            Map<String, Integer> parsed = new LinkedHashMap<>();
            for (String key : countsSec.getKeys(false)) {
                String id = key.toLowerCase(java.util.Locale.ROOT).trim();
                // Validate against known container types if available
                if (containers.getType(id) == null) {
                    plugin.getLogger().warning("[Scatter] Unknown container type '" + id + "' in scatter.counts — still tracking but will skip at place time unless type exists.");
                }
                int count = countsSec.getInt(key, 0);
                if (count < 0) count = 0;
                if (count > 500) {
                    plugin.getLogger().warning("[Scatter] Capped scatter.counts." + id + " " + count + " → 500.");
                    count = 500;
                }
                parsed.put(id, count);
            }
            if (parsed.isEmpty()) {
                plugin.getLogger().warning("[Scatter] scatter.counts empty — falling back to density.");
                counts = new LinkedHashMap<>(DEFAULT_COUNTS);
            } else {
                counts = parsed;
            }
        } else {
            // If no explicit counts, compute from density or use defaults
            if (sec.contains("counts")) {
                plugin.getLogger().warning("[Scatter] scatter.counts section present but empty — using defaults.");
            }
            // Keep defaults; density fallback is computed at scatter time if counts empty
            // If defaults would exceed border, log
            counts = new LinkedHashMap<>(DEFAULT_COUNTS);
        }

        ConfigurationSection msgSec = sec.getConfigurationSection("messages");
        if (msgSec != null) {
            String broadcast = msgSec.getString("scatter-broadcast", broadcastMessage);
            if (broadcast != null && !broadcast.isBlank()) broadcastMessage = broadcast;
            broadcastEnabled = msgSec.getBoolean("broadcast-enabled", true);
        }

        plugin.getLogger().info("[Scatter] Config reloaded — enabled=" + enabled
                + " interval=" + intervalMinutes + "m worlds=" + enabledWorlds
                + " borderRadius=" + borderRadius + " clearPrevious=" + clearPrevious
                + " onTopOnly=" + onTopOnly + " counts=" + counts
                + " maxAttempts=" + maxAttemptsPerContainer + ".");
    }

    private void applyDefaults() {
        enabled = true;
        intervalMinutes = DEFAULT_INTERVAL_MINUTES;
        enabledWorlds = Set.of("glitch_red");
        clearPrevious = DEFAULT_CLEAR_PREVIOUS;
        onTopOnly = DEFAULT_ON_TOP_ONLY;
        borderRadius = DEFAULT_BORDER_RADIUS;
        chunksPerContainer = DEFAULT_CHUNKS_PER_CONTAINER;
        maxAttemptsPerContainer = DEFAULT_MAX_ATTEMPTS;
        counts = new LinkedHashMap<>(DEFAULT_COUNTS);
        broadcastMessage = "<gray>Glitch energy coalesces — <white>{total}</white> caches scattered across the Red World.";
        broadcastEnabled = true;
    }

    // ------------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------------

    private void loadData() {
        synchronized (scattered) {
            scattered.clear();
            if (!dataFile.exists()) {
                plugin.getLogger().info("[Scatter] No prior scattered.json — starting fresh.");
                return;
            }
            try (Reader r = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
                Type type = LIST_TYPE;
                List<ScatteredPos> loaded = GSON.fromJson(r, type);
                if (loaded != null) {
                    // Filter nulls / malformed entries
                    for (ScatteredPos p : loaded) {
                        if (p == null || p.world == null || p.world.isBlank() || p.type == null || p.type.isBlank()) {
                            plugin.getLogger().warning("[Scatter] Skipping malformed scattered entry: " + (p == null ? "null" : p.world + ":" + p.x + "," + p.y + "," + p.z));
                            continue;
                        }
                        scattered.add(p);
                    }
                }
                plugin.getLogger().info("[Scatter] Loaded " + scattered.size() + " previous scattered positions from " + dataFile.getPath() + ".");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Scatter] Failed to load " + dataFile.getPath() + " — starting fresh.", e);
                // Preserve corrupt file for debugging
                try {
                    File bak = new File(dataFile.getParentFile(), "scattered.json.corrupt." + System.currentTimeMillis());
                    Files.move(dataFile.toPath(), bak.toPath());
                    plugin.getLogger().warning("[Scatter] Corrupt file moved to " + bak.getName());
                } catch (Exception ignored) {}
            }
        }
    }

    private void saveData() {
        List<ScatteredPos> snapshot;
        synchronized (scattered) {
            snapshot = new ArrayList<>(scattered);
        }
        try {
            File dir = dataFile.getParentFile();
            if (dir != null && !dir.exists()) {
                if (!dir.mkdirs() && !dir.exists()) {
                    plugin.getLogger().warning("[Scatter] Could not create data dir " + dir.getPath());
                    return;
                }
            }
            File tmp = new File(dir, dataFile.getName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, w);
            }
            // Atomic replace
            try {
                Files.move(tmp.toPath(), dataFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Scatter] Failed to save " + dataFile.getPath(), e);
        }
    }

    // ------------------------------------------------------------------------
    // Scheduler + Cycle hook
    // ------------------------------------------------------------------------

    /**
     * (Re)starts the fixed-rate global scatter. Called from ctor and reload.
     * Uses {@link FoliaScheduler} so it works on both Paper/Purpur and Folia.
     */
    public synchronized void startScheduler() {
        // Cancel existing
        if (scheduledTask != null) {
            try { scheduledTask.cancel(); } catch (Exception ignored) {}
            scheduledTask = null;
        }
        if (!enabled) {
            plugin.getLogger().info("[Scatter] Scheduler disabled via config — not scheduling.");
            return;
        }
        // Single source of truth: when GlitchStash AutoExtract is present, its 31m cycle
        // (30m raid +1m buffer) owns timing at t0+30m+5s via AutoExtractCycleEndEvent.
        // Running our own 30m timer in parallel would drift by 1m per cycle, so we disable
        // the fixed-rate timer and rely solely on the event hook.
        Plugin stash = Bukkit.getPluginManager().getPlugin("GlitchStash");
        if (stash != null && stash.isEnabled()) {
            plugin.getLogger().info("[Scatter] GlitchStash detected — fixed-rate timer disabled (event-driven via AutoExtractCycleEndEvent at t0+30m+5s). Interval " + intervalMinutes + "m is fallback only.");
            return;
        }
        long periodTicks = intervalMinutes * 60L * 20L;
        long delayTicks = periodTicks; // first scatter after one interval; immediate scatter is via AutoExtractCycleEndEvent
        plugin.getLogger().info("[Scatter] Scheduling automatic loot scatter every " + intervalMinutes + "m (periodTicks=" + periodTicks + ") — first in " + intervalMinutes + "m (also on AutoExtractCycleEndEvent if Stash later appears).");
        try {
            scheduledTask = FoliaScheduler.runAtFixedRateGlobal(plugin, this::runScheduledScatter, delayTicks, periodTicks);
            if (scheduledTask == null) {
                plugin.getLogger().severe("[Scatter] Failed to schedule fixed-rate scatter task!");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Scatter] Exception scheduling scatter task", e);
        }
    }

    private void runScheduledScatter() {
        try {
            plugin.getLogger().info("[Scatter] Timer fired — running scheduled scatter.");
            scatterNow();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Scatter] Scheduled scatter threw", e);
        }
    }

    /**
     * Hook to GlitchStash's {@code AutoExtractCycleEndEvent} (t0+30m+5s) via
     * reflection so GlitchStash can remain a soft-depend. If GlitchStash is not
     * installed the hook is a no-op. Idempotent — safe to call multiple times
     * (e.g., on {@code PluginEnableEvent} for late-loaded GlitchStash).
     */
    public synchronized void registerCycleHook() {
        if (cycleListener != null) {
            // Already hooked
            return;
        }
        Plugin stash = Bukkit.getPluginManager().getPlugin("GlitchStash");
        if (stash == null) {
            plugin.getLogger().info("[Scatter] GlitchStash not found — cycle-end hook not registered (timer only).");
            return;
        }
        Class<?> eventClass;
        try {
            eventClass = Class.forName("com.theglitch.glitchstash.AutoExtractCycleEndEvent");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[Scatter] GlitchStash present but AutoExtractCycleEndEvent class not found — hook not registered.");
            return;
        }
        try {
            // Create anonymous Listener
            Listener listener = new Listener() {};
            @SuppressWarnings("unchecked")
            Class<? extends Event> evt = (Class<? extends Event>) eventClass;
            // Register with MONITOR so we run after extraction logic
            Bukkit.getPluginManager().registerEvent(evt, listener, EventPriority.MONITOR,
                    (l, ev) -> {
                        if (!ev.getClass().getName().equals(eventClass.getName())) return;
                        plugin.getLogger().info("[Scatter] AutoExtractCycleEndEvent received — scattering now (post-extraction).");
                        // Ensure we run on correct thread — events fire on global region
                        scatterNow();
                    }, plugin);
            this.cycleListener = listener;
            plugin.getLogger().info("[Scatter] Hooked AutoExtractCycleEndEvent — will scatter right after extraction ends.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Scatter] Failed to register cycle hook", e);
        }
    }

    /**
     * Stop scheduler and unregister hook. Called from {@link GlitchItems#onDisable()}.
     */
    public synchronized void shutdown() {
        if (scheduledTask != null) {
            try { scheduledTask.cancel(); } catch (Exception ignored) {}
            scheduledTask = null;
        }
        // Persist current state so reload keeps positions
        saveData();
        plugin.getLogger().info("[Scatter] Shutdown — persisted " + scattered.size() + " positions.");
    }

    // ------------------------------------------------------------------------
    // Public API — scatterNow()
    // ------------------------------------------------------------------------

    /**
     * Public entry for manual/command or event-driven scatter. Clears previous
     * then places new sparse loot. Thread-safe — concurrent calls are coalesced.
     * <p>
     * This is the integration point for the extraction team: call
     * {@code GlitchItems.getInstance().getScatterManager().scatterNow()} from
     * {@code AutoExtractCycleEndEvent} if the reflective hook is not desired.
     * </p>
     */
    public void scatterNow() {
        if (!enabled) {
            plugin.getLogger().info("[Scatter] scatterNow() called but scatter is disabled — ignoring.");
            return;
        }
        if (!scatterLock.compareAndSet(false, true)) {
            plugin.getLogger().warning("[Scatter] scatterNow() already running — skipping concurrent invocation.");
            return;
        }
        try {
            long start = System.currentTimeMillis();
            World world = pickWorld();
            if (world == null) {
                plugin.getLogger().warning("[Scatter] No enabled world found among " + enabledWorlds + " — aborting scatter.");
                return;
            }

            int cleared = 0;
            if (clearPrevious) {
                cleared = clearPrevious();
                plugin.getLogger().info("[Scatter] Cleared " + cleared + " previous containers.");
            } else {
                plugin.getLogger().info("[Scatter] clearPrevious=false — keeping " + scattered.size() + " previous.");
                // Still empty in-memory? We keep them but don't clear blocks
            }

            int placed = placeNew(world);

            long elapsed = System.currentTimeMillis() - start;
            plugin.getLogger().info("[Scatter] Scatter complete in " + elapsed + "ms — cleared=" + cleared + " placed=" + placed + " totalTracked=" + scattered.size() + " world=" + world.getName() + ".");

            if (broadcastEnabled && placed > 0) {
                broadcastScatter(world, placed);
            }
            // Persist after each full cycle
            saveData();
        } finally {
            scatterLock.set(false);
        }
    }

    /**
     * Broadcast scatter completion near RED world players (or globally if
     * preferred). Respects MiniMessage-like raw string — we use Bukkit broadcast.
     */
    private void broadcastScatter(World world, int total) {
        String raw = broadcastMessage.replace("{total}", String.valueOf(total));
        // Try MiniMessage if available; fall back to legacy
        net.kyori.adventure.text.Component comp;
        try {
            comp = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(raw);
        } catch (Exception e) {
            comp = net.kyori.adventure.text.Component.text(raw);
        }
        // Send to all players in the world (or globally if worlds empty)
        for (org.bukkit.entity.Player p : world.getPlayers()) {
            try { p.sendMessage(comp); } catch (Exception ignored) {}
        }
        // Also log to console
        try { Bukkit.getConsoleSender().sendMessage(comp); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------------
    // Clear previous
    // ------------------------------------------------------------------------

    /**
     * Clears blocks at persisted scattered positions. Only removes blocks that
     * are still Glitch containers (so player builds are never touched). On Folia,
     * each removal is scheduled on the owning region.
     *
     * @return number of blocks actually cleared
     */
    private int clearPrevious() {
        List<ScatteredPos> snapshot;
        synchronized (scattered) {
            if (scattered.isEmpty()) return 0;
            snapshot = new ArrayList<>(scattered);
            scattered.clear();
        }
        // We persist empty immediately so a crash mid-clear doesn't re-clear
        // stale positions on next boot; but we also track cleared count
        // and only save again at end of scatterNow()
        int cleared = 0;
        for (ScatteredPos pos : snapshot) {
            if (pos == null || pos.world == null) continue;
            World w = Bukkit.getWorld(pos.world);
            if (w == null) {
                // World not loaded (maybe unloaded) — count as cleared since entry is dropped
                continue;
            }
            // Validate coordinates are within world limits (avoid OOB)
            if (pos.y < w.getMinHeight() || pos.y >= w.getMaxHeight()) continue;

            // Chunk check: if not loaded, try to handle
            int cx = pos.x >> 4;
            int cz = pos.z >> 4;
            boolean chunkLoaded = w.isChunkLoaded(cx, cz);
            // On Folia, isChunkLoaded may be region-specific; we still attempt
            // If chunk not loaded and we are not in a position to force-load, skip
            // the block clear (chunk will load later and container will still be there,
            // but we have already dropped the tracking entry — next scatter will
            // overwrite with new placements, so stale container may remain until
            // chunk loads and a future clear can see it). To avoid leak, try to
            // schedule an async clear if chunk async API is available.
            if (!chunkLoaded && HAS_CHUNK_ASYNC && FoliaScheduler.isFolia()) {
                // Schedule async chunk load then clear on region
                scheduleAsyncClear(w, pos);
                // Don't count synchronously; async will handle
                continue;
            }
            // For sync path (Purpur or chunk already loaded)
            try {
                Block block = w.getBlockAt(pos.x, pos.y, pos.z);
                // Only clear if it's still a container we placed (any type counts)
                if (!containers.isContainer(block)) {
                    // Already air / broken / replaced — still count as handled
                    continue;
                }
                // Double-check type matches stored type if available
                // (optional: if stored type mismatches, still clear as it's our tracked spot)
                cleared += clearBlock(block);
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "[Scatter] Failed to clear at " + pos.world + " " + pos.x + "," + pos.y + "," + pos.z, e);
            }
        }
        // We do not save here; caller saves after placement
        return cleared;
    }

    /**
     * Schedule a clear for an unloaded chunk on Folia via getChunkAtAsync. The
     * actual block clear runs on the region thread that owns the chunk.
     */
    private void scheduleAsyncClear(World world, ScatteredPos pos) {
        try {
            // Reflective async path to avoid hard compile dep on async API shape
            // World#getChunkAtAsync(int,int,boolean) is Paper 1.19+
            Method async = null;
            try {
                async = world.getClass().getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            } catch (NoSuchMethodException e) {
                try {
                    async = world.getClass().getMethod("getChunkAtAsync", int.class, int.class);
                } catch (NoSuchMethodException ignored) {}
            }
            if (async != null) {
                int cx = pos.x >> 4;
                int cz = pos.z >> 4;
                Object future;
                if (async.getParameterCount() == 3) {
                    future = async.invoke(world, cx, cz, true);
                } else {
                    future = async.invoke(world, cx, cz);
                }
                if (future instanceof java.util.concurrent.CompletableFuture<?> cf) {
                    @SuppressWarnings("unchecked")
                    java.util.concurrent.CompletableFuture<org.bukkit.Chunk> f = (java.util.concurrent.CompletableFuture<org.bukkit.Chunk>) cf;
                    f.thenAccept(chunk -> {
                        // Now on async thread; schedule region-aware clear
                        Location loc = new Location(world, pos.x, pos.y, pos.z);
                        FoliaScheduler.runAtLocation(plugin, loc, () -> {
                            try {
                                Block b = world.getBlockAt(pos.x, pos.y, pos.z);
                                if (containers.isContainer(b)) {
                                    clearBlock(b);
                                }
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.FINE, "[Scatter] Async clear failed at " + pos.x + "," + pos.y + "," + pos.z, e);
                            }
                        });
                    }).exceptionally(ex -> {
                        plugin.getLogger().log(Level.FINE, "[Scatter] Async chunk load failed for clear at " + pos.x + "," + pos.z, ex);
                        return null;
                    });
                    return;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[Scatter] scheduleAsyncClear reflection failed", e);
        }
        // Fallback: try sync clear anyway (may load chunk)
        try {
            Block b = world.getBlockAt(pos.x, pos.y, pos.z);
            if (containers.isContainer(b)) clearBlock(b);
        } catch (Exception ignored) {}
    }

    /**
     * Clears a single container block: removes PDC flag and sets to AIR.
     * Must be called on the region thread owning the block on Folia.
     *
     * @return 1 if cleared, 0 otherwise
     */
    private int clearBlock(Block block) {
        if (block == null) return 0;
        try {
            // Remove PDC first so ContainerManager doesn't think it's still valid
            containers.clear(block);
            // Then set to air. Use false to avoid physics updates where possible.
            // On Folia we are already on the region thread.
            block.setType(Material.AIR, false);
            return 1;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[Scatter] clearBlock failed at " + block.getX() + "," + block.getY() + "," + block.getZ(), e);
            return 0;
        }
    }

    // ------------------------------------------------------------------------
    // Placement
    // ------------------------------------------------------------------------

    /**
     * Places new containers sparsely in {@code world}. Respects
     * {@code onTopOnly}: target and above must be air, ground must be solid.
     *
     * @return number of containers successfully placed
     */
    private int placeNew(World world) {
        if (world == null) return 0;

        // Resolve counts: if explicit counts present use them, else compute from density
        Map<String, Integer> toPlace = resolveCounts(world);
        if (toPlace.isEmpty()) {
            plugin.getLogger().warning("[Scatter] No containers to place (counts empty).");
            return 0;
        }

        int totalPlaced = 0;
        // Track positions added this cycle for persistence
        List<ScatteredPos> newlyPlaced = new ArrayList<>();

        // Diagnostics: where do rejected attempts come from? (one INFO line per cycle)
        int diagAttempts = 0;
        int diagChunkFail = 0;
        int diagYNull = 0;
        int diagNotAir = 0;
        int diagWgReject = 0;
        final int[] diagYNullQuad = new int[4]; // [x<0,z<0],[x>=0,z<0],[x<0,z>=0],[x>=0,z>=0]
        String diagSampleYNull = "";
        String diagSampleNotAir = "";

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (Map.Entry<String, Integer> entry : toPlace.entrySet()) {
            String typeId = entry.getKey();
            int needed = entry.getValue();
            if (needed <= 0) continue;
            ContainerManager.ContainerType type = containers.getType(typeId);
            if (type == null) {
                plugin.getLogger().warning("[Scatter] Skipping unknown container type '" + typeId + "' — no ContainerType found.");
                continue;
            }
            int placedForType = 0;
            int attempts = 0;
            int maxAttempts = needed * Math.max(1, maxAttemptsPerContainer);
            // Also cap total attempts to avoid infinite loop on bad terrain
            while (placedForType < needed && attempts < maxAttempts) {
                attempts++;
                // Pick random x,z within border square
                int x = rand.nextInt(-borderRadius, borderRadius + 1);
                int z = rand.nextInt(-borderRadius, borderRadius + 1);

                // Optional: enforce sparse stride? The spec "every 5-10 chunks" suggests
                // we could quantize to chunk centers every N chunks, but random within
                // border already yields sparse (≈36 over 4000x4000 area).
                // We keep pure random for simplicity + natural spread.

                // Chunk handling: ensure chunk is at least considered loaded or loadable
                int cx = x >> 4;
                int cz = z >> 4;
                boolean isLoaded = world.isChunkLoaded(cx, cz);
                // On Folia with many unloaded chunks, we skip unloaded to avoid sync load lag.
                // Instead we probabilistically skip and pick another spot.
                // If we really want to fill unloaded areas, we could use async placement,
                // but for sparse loot it's fine to place only near loaded terrain.
                // For coverage we still attempt to load if HAS_CHUNK_ASYNC and not Folia.
                if (!isLoaded) {
                    // Try to load synchronously on Purpur (fast); on Folia skip or async
                    if (FoliaScheduler.isFolia()) {
                        // On Folia, attempt async placement for this x,z
                        // We handle via synchronous fallback: skip this attempt if chunk not loaded
                        // to avoid stalling global thread. Sparse placement will still find
                        // enough spots in loaded chunks.
                        if (HAS_CHUNK_ASYNC) {
                            // We could schedule async placement for this single container
                            // but to keep the loop simple and bounded, just skip.
                            diagAttempts++;
                            diagChunkFail++;
                            continue;
                        }
                        // Fall through to try sync load if no async
                    }
                    // Purpur path: try to load chunk synchronously if not loaded
                    // This may cause sync chunk generation but scatter runs rarely (30m)
                    // so it's acceptable for up to 36 containers.
                    try {
                        // This will generate/load the chunk if needed
                        world.getChunkAt(cx, cz);
                        // Re-check loaded after?
                    } catch (Exception e) {
                        diagAttempts++;
                        diagChunkFail++;
                        continue;
                    }
                }
                diagAttempts++;

                // Find valid Y at this column
                Integer targetY = findValidTargetY(world, x, z);
                if (targetY == null) {
                    diagYNull++;
                    int qi = (x < 0 ? 0 : 1) + (z < 0 ? 0 : 2);
                    diagYNullQuad[qi]++;
                    if (diagSampleYNull.isEmpty()) diagSampleYNull = "(" + x + "," + z + ")";
                    continue;
                }

                Location loc = new Location(world, x + 0.5, targetY, z + 0.5);
                Block target = world.getBlockAt(x, targetY, z);

                // Double-check target is still air and not already container (race)
                if (!target.getType().isAir()) {
                    diagNotAir++;
                    if (diagSampleNotAir.isEmpty()) diagSampleNotAir = "(" + x + "," + targetY + "," + z + ")=" + target.getType();
                    continue;
                }
                if (containers.isContainer(target)) continue;

                // WorldGuard protection check (except __global__)
                if (isProtectedRegion(loc)) {
                    diagWgReject++;
                    continue;
                }

                // All validations passed — place container
                // On Folia, block modification must be on region thread
                boolean placed;
                if (FoliaScheduler.isFolia()) {
                    // Schedule region-aware placement synchronously? For scatter
                    // frequency (30m) we can do it synchronously via runAtLocation
                    // but we need to wait for completion to count. For simplicity,
                    // do direct placement on global if isFolia is false; if true,
                    // still place via FoliaScheduler but block on immediate?
                    // To keep counts accurate, we attempt direct placement and
                    // fall back to scheduling if it throws.
                    try {
                        placed = placeBlock(target, type);
                    } catch (Exception ex) {
                        // Try region-scheduled placement (async-ish)
                        // Since we are counting, we schedule and assume success
                        // but track separately
                        FoliaScheduler.runAtLocation(plugin, loc, () -> {
                            try {
                                Block b = world.getBlockAt(x, targetY, z);
                                if (b.getType().isAir() && !containers.isContainer(b) && !isProtectedRegion(loc)) {
                                    if (placeBlock(b, type)) {
                                        // Add to persisted list from async context — need synchronization
                                        synchronized (scattered) {
                                            scattered.add(new ScatteredPos(world.getName(), x, targetY, z, type.name(), System.currentTimeMillis()));
                                        }
                                        saveData();
                                    }
                                }
                            } catch (Exception ignored2) {}
                        });
                        // Count as pending, not immediate
                        continue;
                    }
                } else {
                    placed = placeBlock(target, type);
                }

                if (placed) {
                    placedForType++;
                    totalPlaced++;
                    newlyPlaced.add(new ScatteredPos(world.getName(), x, targetY, z, type.name(), System.currentTimeMillis()));
                }
            }

            if (placedForType < needed) {
                plugin.getLogger().warning("[Scatter] Could only place " + placedForType + "/" + needed + " of type '" + type.name() + "' after " + attempts + " attempts (terrain/worldguard may be dense).");
            } else {
                plugin.getLogger().info("[Scatter] Placed " + placedForType + " x '" + type.name() + "' in " + attempts + " attempts.");
            }
        }

        // Persist newly placed together with any async additions already in scattered
        synchronized (scattered) {
            scattered.addAll(newlyPlaced);
        }
        plugin.getLogger().info(String.format(
                "[Scatter] Diag: attempts=%d placed=%d chunkFail=%d yNull=%d notAir=%d wgReject=%d | yNull quads [--]=%d [+-]=%d [-+]=%d [++]=%d | samples: yNull@%s notAir@%s",
                diagAttempts, totalPlaced, diagChunkFail, diagYNull, diagNotAir, diagWgReject,
                diagYNullQuad[0], diagYNullQuad[1], diagYNullQuad[2], diagYNullQuad[3],
                diagSampleYNull, diagSampleNotAir));
        return totalPlaced;
    }

    /**
     * Resolves how many of each container to place.
     * <p>
     * If {@code counts} is non-empty use it directly. Otherwise compute from
     * {@code chunks-per-container} density across the border area.
     * </p>
     */
    private Map<String, Integer> resolveCounts(World world) {
        if (counts != null && !counts.isEmpty()) {
            // Return a copy so caller can mutate
            return new LinkedHashMap<>(counts);
        }
        // Fallback density: total chunks = (borderRadius*2/16)^2
        // total containers = chunks / chunksPerContainer
        // Then distribute proportionally by DEFAULT_COUNTS ratios
        long diameter = (long) borderRadius * 2L;
        long chunksPerSide = diameter / 16L;
        long totalChunks = chunksPerSide * chunksPerSide;
        int totalContainers = (int) Math.max(1, totalChunks / Math.max(1, chunksPerContainer));
        // Cap to avoid explosion on tiny chunks-per-container
        totalContainers = Math.min(totalContainers, 60);
        // Distribute by default ratios (debris 50%, cache 27%, vault 16%, rift 5% approx from 18/10/6/2)
        Map<String, Integer> out = new LinkedHashMap<>();
        int debris = Math.max(1, (int) Math.round(totalContainers * 0.50));
        int cache = Math.max(1, (int) Math.round(totalContainers * 0.28));
        int vault = Math.max(1, (int) Math.round(totalContainers * 0.16));
        int rift = Math.max(0, totalContainers - debris - cache - vault);
        out.put("debris", debris);
        out.put("cache", cache);
        out.put("vault", vault);
        out.put("rift_vault", rift);
        plugin.getLogger().info("[Scatter] Counts empty — density fallback: " + totalChunks + " chunks / " + chunksPerContainer + " = " + totalContainers + " containers " + out);
        return out;
    }

    /**
     * Attempt to place a single container block at {@code target} (must be AIR).
     * Wraps {@link ContainerManager#mark(Block, ContainerManager.ContainerType)}.
     *
     * @return true if the block was marked and persisted
     */
    private boolean placeBlock(Block target, ContainerManager.ContainerType type) {
        if (target == null || type == null) return false;
        if (!target.getType().isAir()) return false;
        // Guard: onTopOnly requires ground validation already done, but double-check
        if (onTopOnly) {
            Block ground = target.getWorld().getBlockAt(target.getX(), target.getY() - 1, target.getZ());
            if (!isSolidGround(ground)) return false;
        }
        // Delegate to ContainerManager — handles PDC typeKey and material
        boolean ok = containers.mark(target, type);
        if (!ok) {
            plugin.getLogger().warning("[Scatter] ContainerManager.mark failed at " + target.getX() + "," + target.getY() + "," + target.getZ() + " for " + type.name());
        }
        return ok;
    }

    // ------------------------------------------------------------------------
    // Ground validation (spec: on top of solid ground, not air/water/lava/trees)
    // ------------------------------------------------------------------------

    /**
     * Finds a valid target Y at column (x,z) that satisfies ground checks.
     * Scans downward up to 10 blocks from the highest block to skip foliage/
     * water surface and find the first solid ground with air above.
     *
     * @return target Y (air block where container would be placed) or null if none valid
     */
    private Integer findValidTargetY(World world, int x, int z) {
        if (world == null) return null;
        // Fast reject: if x,z outside world border (Paper's border), getHighest may be weird
        // We already clamp to borderRadius, so skip.

        int highest;
        try {
            highest = world.getHighestBlockYAt(x, z);
        } catch (Exception e) {
            return null;
        }
        // World height guard
        int minH = world.getMinHeight();
        int maxH = world.getMaxHeight(); // exclusive? In Bukkit max is 319, but get block at max is inclusive
        // Spec: target must be AIR, above must be AIR, ground solid
        // We scan down up to 10 blocks to get below leaves/logs/water surface
        int scanDepth = 10;
        for (int offset = 0; offset <= scanDepth; offset++) {
            int groundY = highest - offset;
            int targetY = groundY + 1;
            int aboveY = groundY + 2;

            if (groundY < minH || aboveY >= maxH) continue;
            // Need chunk loaded for this Y column (already checked for x,z)
            Block ground;
            Block target;
            Block above;
            try {
                ground = world.getBlockAt(x, groundY, z);
                target = world.getBlockAt(x, targetY, z);
                above = world.getBlockAt(x, aboveY, z);
            } catch (Exception e) {
                continue;
            }
            if (ground == null || target == null || above == null) continue;

            // Target and above must be AIR (or at least not solid / not liquid / not container)
            // Spec says "target must be air above solid" — enforce strict isAir()
            if (!target.getType().isAir()) continue;
            if (!above.getType().isAir()) continue;

            // Ground must be solid, not liquid, not leaves/logs, not already container
            if (!isSolidGround(ground)) continue;

            // Also ensure ground is not water/lava (isSolid already false for those, but double)
            Material gm = ground.getType();
            if (gm == Material.WATER || gm == Material.LAVA) continue;
            if (ground.isLiquid()) continue;

            // Not already a container block (shouldn't happen for air target, but check ground not container)
            if (containers.isContainer(ground)) continue;

            // On-top-only already satisfied by the above two-air check
            if (onTopOnly) {
                // Already validated target is air above solid; nothing else
            }

            // All good — this Y works
            return targetY;
        }
        return null;
    }

    /**
     * Checks if a ground block is suitable to place a container on top of.
     * Requirements: solid, not leaves/log, not liquid, not container material.
     */
    private boolean isSolidGround(Block ground) {
        if (ground == null) return false;
        Material m = ground.getType();
        if (m.isAir()) return false;
        if (!m.isSolid()) return false;
        if (m.isAir() || m == Material.WATER || m == Material.LAVA) return false;
        // Leaves / logs tags — not solid ground (tree canopy / trunk)
        try {
            if (Tag.LEAVES.isTagged(m)) return false;
            if (Tag.LOGS.isTagged(m)) return false;
        } catch (Exception ignored) {
            // Tag API may be unavailable on some versions; fall back to name check
            String n = m.name();
            if (n.contains("LEAVES") || n.contains("LOG")) return false;
        }
        // Also reject other foliage that is technically solid? e.g., MANGROVE_ROOTS? But isSolid is false.
        // Reject container materials themselves (avoid stacking)
        if (m == Material.BARREL || m == Material.CHEST || m == Material.BLUE_SHULKER_BOX || m == Material.DECORATED_POT) return false;
        // Additional: not bamboo, not scaffolding etc. But isSolid handles.
        return true;
    }

    // ------------------------------------------------------------------------
    // WorldGuard
    // ------------------------------------------------------------------------

    /**
     * Checks if {@code loc} is inside a WorldGuard protected region other than
     * {@code __global__}. If WorldGuard is not installed or the check fails,
     * returns false (allow placement). Uses reflection to avoid hard dependency.
     */
    private boolean isProtectedRegion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) return false;
        try {
            return isProtectedReflective(loc);
        } catch (Throwable t) {
            if (wgWarned.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.WARNING, "[Scatter] WorldGuard check failed — allowing placement (error logged once)", t);
            } else {
                plugin.getLogger().fine("[Scatter] WG check failed: " + t.getMessage());
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isProtectedReflective(Location loc) throws Exception {
        // Try modern WorldGuard 7+ API: WorldGuard.getInstance().getPlatform().getRegionContainer()
        Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
        Method getInstance = wgClass.getMethod("getInstance");
        Object wgInstance = getInstance.invoke(null);
        Method getPlatform = wgInstance.getClass().getMethod("getPlatform");
        Object platform = getPlatform.invoke(wgInstance);
        Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
        Object container = getRegionContainer.invoke(platform);
        if (container == null) return false;

        // Adapt Bukkit world to WorldEdit world
        Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Method adaptWorld = bukkitAdapter.getMethod("adapt", World.class);
        Object weWorld = adaptWorld.invoke(null, loc.getWorld());

        Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
        Method get = container.getClass().getMethod("get", weWorldClass);
        Object regionManager = get.invoke(container, weWorld);
        if (regionManager == null) return false;

        // Build BlockVector3 at target
        Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
        Method at = bv3.getMethod("at", int.class, int.class, int.class);
        Object vec = at.invoke(null, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        Method getApplicable = regionManager.getClass().getMethod("getApplicableRegions", bv3);
        Object regionSet = getApplicable.invoke(regionManager, vec);
        if (regionSet == null) return false;

        // Check size
        Method sizeM = regionSet.getClass().getMethod("size");
        int size = (int) sizeM.invoke(regionSet);
        if (size == 0) return false;

        // Inspect regions — if only __global__, allow
        try {
            Method getRegions = regionSet.getClass().getMethod("getRegions");
            Object regions = getRegions.invoke(regionSet);
            if (regions instanceof Collection<?> col) {
                if (col.isEmpty()) return false;
                if (col.size() == 1) {
                    Object r = col.iterator().next();
                    try {
                        Method getId = r.getClass().getMethod("getId");
                        String id = (String) getId.invoke(r);
                        if ("__global__".equalsIgnoreCase(id)) return false;
                    } catch (Exception ignored) {
                        // If we can't get id, assume protected
                    }
                }
                // More than one region, or single non-global → protected
                return true;
            }
        } catch (NoSuchMethodException ignored) {
            // Fallback: if size >0 and not just global, consider protected
        }
        // If we couldn't introspect regions, be conservative: if size >0, treat as protected
        // But allow if size==1 and we couldn't check — assume not protected to avoid false positives
        return size > 1;
    }

    // ------------------------------------------------------------------------
    // World picker
    // ------------------------------------------------------------------------

    private World pickWorld() {
        // Prefer first enabled world that exists and is loaded
        for (String name : enabledWorlds) {
            if (name == null || name.isBlank()) continue;
            World w = Bukkit.getWorld(name);
            if (w != null) return w;
            // Try case-insensitive
            for (World bw : Bukkit.getWorlds()) {
                if (bw.getName().equalsIgnoreCase(name)) return bw;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // Getters for commands/debug
    // ------------------------------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public Set<String> getEnabledWorlds() { return enabledWorlds; }
    public Map<String, Integer> getCounts() { return Collections.unmodifiableMap(counts); }
    public int getTrackedCount() { synchronized (scattered) { return scattered.size(); } }
    public List<ScatteredPos> getScatteredSnapshot() { synchronized (scattered) { return List.copyOf(scattered); } }
    public File getDataFile() { return dataFile; }
    public int getBorderRadius() { return borderRadius; }
    public boolean isClearPrevious() { return clearPrevious; }
    public boolean isOnTopOnly() { return onTopOnly; }
}
