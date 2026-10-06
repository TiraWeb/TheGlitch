package com.theglitch.glitchstash;

import com.theglitch.common.FoliaScheduler;
import com.theglitch.common.Worlds;
import com.theglitch.glitchitems.GlitchItems;
import com.theglitch.glitchstash.extract.DynamicExtractionManager;
import com.theglitch.glitchstash.extract.QuietKoth;
import dev.velmax.velkoth.VelKothPlugin;
import dev.velmax.velkoth.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Automated extraction scheduler — every {@code intervalMinutes} (default 31)
 * starts <b>all</b> VelKoth arenas even if the world is empty. The cycle is:
 * <ul>
 *   <li><b>t0</b>: discover and start every arena via VelKoth API or
 *       {@code /koth start <arena>} fallback; log every attempt</li>
 *   <li><b>t0 + raidDuration</b> (default 30 min): RED-world timeout. GlitchRaid
 *       owns the kill; without GlitchRaid a local fallback kills red-world players</li>
 *   <li><b>t0 + raidDuration + 5s</b>: scatter loot — fire
 *       {@link AutoExtractCycleEndEvent} and ask GlitchItems to re-scatter
 *       its containers</li>
 *   <li><b>t0 + interval</b> (default 31 min): next cycle (handled by the
 *       fixed-rate scheduler with a 1-minute buffer between scatter and
 *       restart)</li>
 * </ul>
 * <p>
 * Each cycle first tries {@link DynamicExtractionManager} (random validated
 * spots). Only when it declines does the legacy path start VelKoth's own
 * arenas, filtered by the {@code auto-extract.arenas} allow-list when set.
 * <p>
 * Folia-safe: all scheduling goes through {@link FoliaScheduler}
 * ({@code runAtFixedRateGlobal / runLaterGlobal}) so the task runs on the
 * global region on both Paper/Purpur and Folia.
 */
public final class AutoExtractScheduler {

    private static final String VELKOTH_PLUGIN_NAME = "VelKoth";
    private static final String CONFIG_SECTION = "auto-extract";
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static final long SCATTER_DELAY_TICKS = 5L * 20L; // +5s after raid duration

    private final GlitchStash plugin;
    private final DynamicExtractionManager dynamicManager; // nullable — null = legacy path only
    // Cached PluginManager — Bukkit returns the same instance for server lifetime,
    // so one lookup avoids repeated Bukkit.getPluginManager() on hot paths.
    private final org.bukkit.plugin.PluginManager pluginManager;

    // Cached config — volatile for safe cross-thread reads (Folia may call from global region)
    private volatile boolean enabled = true;
    private volatile int intervalMinutes = 31;
    private volatile int raidDurationMinutes = 30;
    private volatile int bufferMinutes = 1;
    private volatile List<String> configuredArenas = List.of(); // empty = all
    // Authoritative — set once at construction, one instance per configured red world.
    // Not re-read from config on reload() (that field is now a list shared across instances).
    private final String redWorld;

    private volatile FoliaScheduler.Cancellable fixedRateTask;
    private final List<FoliaScheduler.Cancellable> pendingBufferTasks = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger cycleCounter = new AtomicInteger(0);
    private volatile long lastCycleStartMillis = 0L;

    public AutoExtractScheduler(GlitchStash plugin, String world) {
        this(plugin, null, world);
    }

    public AutoExtractScheduler(GlitchStash plugin, DynamicExtractionManager dynamicManager, String world) {
        this.plugin = plugin;
        this.dynamicManager = dynamicManager;
        this.redWorld = (world == null || world.isBlank()) ? Worlds.GLITCH_RED : world.trim();
        this.pluginManager = Bukkit.getPluginManager();
        reload();
    }

    // ------------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------------

    /**
     * Reloads cached values from {@code config.yml#auto-extract}. Validates
     * ranges and logs warnings instead of throwing. Safe to call from
     * {@link GlitchStash#reloadPlugin()}.
     */
    public void reload() {
        synchronized (this) {
            // Cancel pending per-cycle buffer tasks on reload to avoid stale timings
            for (FoliaScheduler.Cancellable c : pendingBufferTasks) {
                try { c.cancel(); } catch (Exception ignored) {}
            }
            pendingBufferTasks.clear();
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_SECTION);
        if (section == null) {
            plugin.getLogger().warning("[AutoExtract] Missing '" + CONFIG_SECTION + "' section — using defaults (enabled=true, interval=31m, raid=30m).");
            enabled = true;
            intervalMinutes = 31;
            raidDurationMinutes = 30;
            bufferMinutes = 1;
            configuredArenas = List.of();
            reloadDynamic();
            return;
        }

        enabled = section.getBoolean("enabled", true);

        int interval = section.getInt("interval-minutes", 31);
        if (interval < 1 || interval > 1440) {
            plugin.getLogger().warning("[AutoExtract] Invalid interval-minutes " + interval + " — clamped to 31.");
            interval = Math.max(1, Math.min(interval, 1440));
        }
        intervalMinutes = interval;

        int raidMin = section.getInt("raid-duration-minutes", 30);
        if (raidMin < 1 || raidMin > 1440) {
            plugin.getLogger().warning("[AutoExtract] Invalid raid-duration-minutes " + raidMin + " — clamped to 30.");
            raidMin = Math.max(1, Math.min(raidMin, 1440));
        }
        raidDurationMinutes = raidMin;

        int buffer = section.getInt("buffer-minutes", 1);
        if (buffer < 0 || buffer > 60) {
            plugin.getLogger().warning("[AutoExtract] Invalid buffer-minutes " + buffer + " — clamped to 1.");
            buffer = Math.max(0, Math.min(buffer, 60));
        }
        bufferMinutes = buffer;

        // Validate invariant: interval ≈ raid + buffer. Warn but don't enforce — operator may want overlap.
        if (intervalMinutes != raidDurationMinutes + bufferMinutes) {
            plugin.getLogger().info("[AutoExtract] interval (" + intervalMinutes + "m) != raidDuration (" + raidDurationMinutes + "m) + buffer (" + bufferMinutes + "m). Cycle is t0 start → +" + raidDurationMinutes + "m kill → +5s scatter → +" + intervalMinutes + "m restart.");
        }

        List<String> arenas = section.getStringList("arenas");
        if (arenas == null) arenas = List.of();
        // Normalize: trim, ignore blanks
        List<String> normalized = new ArrayList<>();
        for (String a : arenas) {
            if (a != null && !a.isBlank()) normalized.add(a.trim());
        }
        configuredArenas = List.copyOf(normalized);

        plugin.getLogger().info("[AutoExtract] Config reloaded — enabled=" + enabled + ", interval=" + intervalMinutes + "m, raidDuration=" + raidDurationMinutes + "m, buffer=" + bufferMinutes + "m, arenas=" + (configuredArenas.isEmpty() ? "<all discovered>" : configuredArenas) + ", redWorld=" + redWorld);
        reloadDynamic();
    }

    private void reloadDynamic() {
        if (dynamicManager == null) return;
        try {
            dynamicManager.reload();
        } catch (Exception e) {
            plugin.getLogger().warning("[AutoExtract] Dynamic extraction reload failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    /**
     * Starts the fixed-rate global task. If already running, cancels and
     * reschedules. Respects {@code enabled}. The first cycle fires after a
     * short warm-up delay (5 seconds) so VelKoth has finished loading.
     */
    public synchronized void start() {
        // Cancel existing
        if (fixedRateTask != null) {
            try { fixedRateTask.cancel(); } catch (Exception ignored) {}
            fixedRateTask = null;
        }
        if (!enabled) {
            plugin.getLogger().info("[AutoExtract] Disabled via config — scheduler not started.");
            return;
        }

        long periodTicks = intervalMinutes * TICKS_PER_MINUTE;
        long delayTicks = 5L * TICKS_PER_SECOND; // 5s warm-up

        plugin.getLogger().info("[AutoExtract] Scheduling automated extraction every " + intervalMinutes + "m (raidDuration=" + raidDurationMinutes + "m, buffer=" + bufferMinutes + "m, periodTicks=" + periodTicks + ") — first cycle in 5s.");

        // Folia-safe fixed-rate — runs on the global region thread
        fixedRateTask = FoliaScheduler.runAtFixedRateGlobal(plugin, this::runCycle, delayTicks, periodTicks);

        if (fixedRateTask == null) {
            plugin.getLogger().severe("[AutoExtract] Failed to schedule fixed-rate task — extraction will NOT auto-start!");
        }
    }

    /**
     * Cancels the fixed-rate task and all pending buffer tasks. Called from
     * {@link GlitchStash#onDisable()}.
     */
    public synchronized void shutdown() {
        if (fixedRateTask != null) {
            try { fixedRateTask.cancel(); } catch (Exception ignored) {}
            fixedRateTask = null;
        }
        for (FoliaScheduler.Cancellable c : pendingBufferTasks) {
            try { c.cancel(); } catch (Exception ignored) {}
        }
        pendingBufferTasks.clear();
        plugin.getLogger().info("[AutoExtract] Scheduler shut down (cycles fired: " + cycleCounter.get() + ").");
    }

    // ------------------------------------------------------------------------
    // Cycle
    // ------------------------------------------------------------------------

    /**
     * One full cycle: discover arenas, start each, then schedule the 30-minute
     * timeout + 5-second scatter hooks. Runs on the global region (via
     * FoliaScheduler). Never throws — all failures are logged.
     */
    private void runCycle() {
        if (!enabled) {
            plugin.getLogger().info("[AutoExtract] Cycle skipped — disabled.");
            return;
        }
        int cycle = cycleCounter.incrementAndGet();
        lastCycleStartMillis = System.currentTimeMillis();
        plugin.getLogger().info("[AutoExtract] === Cycle #" + cycle + " starting =========================================");

        // Dynamic path first: random validated spots each cycle. If it declines
        // (disabled, missing world, no spots validated) the legacy discovery loop runs.
        boolean dynamicRan = false;
        if (dynamicManager != null) {
            try {
                dynamicRan = dynamicManager.runCycle(cycle);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[AutoExtract] Dynamic extraction threw — falling back to legacy arenas for cycle #" + cycle, e);
                dynamicRan = false;
            }
            if (dynamicRan) {
                plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — dynamic extraction active; legacy arena discovery skipped.");
            }
        }

        if (!dynamicRan) {
            List<String> arenas;
            try {
                arenas = discoverArenas();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[AutoExtract] Arena discovery threw — skipping cycle #" + cycle, e);
                return;
            }

            if (arenas == null || arenas.isEmpty()) {
                plugin.getLogger().warning("[AutoExtract] No arenas discovered — cycle #" + cycle + " will not start any extraction. Check VelKoth arenas.yml or set auto-extract.arenas in GlitchStash config. VelKoth loaded: " + (pluginManager.getPlugin(VELKOTH_PLUGIN_NAME) != null));
                // Still schedule buffer hooks so the 1-minute spacing stays consistent
            } else {
                plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — starting " + arenas.size() + " arena(s): " + arenas);
                for (String arena : arenas) {
                    try {
                        startArena(arena, cycle);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "[AutoExtract] Failed to start arena '" + arena + "' in cycle #" + cycle, e);
                    }
                }
            }
        }

        // Drive GlitchRaid global extraction window so late joiners see correct remaining time
        try {
            boolean raidStarted = tryNotifyRaidStart();
            if (raidStarted) {
                plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — GlitchRaid global extraction started/anchored to this cycle.");
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[AutoExtract] GlitchRaid start probe failed: " + e.getMessage());
        }

        // Schedule intra-cycle buffer tasks: t0+30m kill, t0+30m+5s scatter
        scheduleBufferTasks(cycle, lastCycleStartMillis);
        plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " t0 complete — next cycle in " + intervalMinutes + "m (kill in " + raidDurationMinutes + "m, scatter +5s).");
    }

    /**
     * Ends the dynamic portion of the cycle (arena stops + marker clear).
     * Guarded — DynamicExtractionManager.endCycle is idempotent, so calling
     * this from both cycle-end paths is safe.
     */
    private void endDynamicCycle(int cycle) {
        if (dynamicManager == null) return;
        try {
            dynamicManager.endCycle();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[AutoExtract] Failed to end dynamic cycle #" + cycle, e);
        }
    }

    /**
     * Schedules the 30m timeout kill and 30m+5s scatter for the given cycle.
     */
    private void scheduleBufferTasks(int cycle, long cycleStartMillis) {
        long raidTicks = raidDurationMinutes * TICKS_PER_MINUTE;
        long scatterTicks = raidTicks + SCATTER_DELAY_TICKS;

        FoliaScheduler.Cancellable killTask = FoliaScheduler.runLaterGlobalCancellable(plugin, () -> handleCycleTimeout(cycle, cycleStartMillis), raidTicks);
        FoliaScheduler.Cancellable scatterTask = FoliaScheduler.runLaterGlobalCancellable(plugin, () -> handleCycleEndScatter(cycle, cycleStartMillis), scatterTicks);

        synchronized (this) {
            pendingBufferTasks.add(killTask);
            pendingBufferTasks.add(scatterTask);
        }

        plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — scheduled timeout kill in " + raidDurationMinutes + "m (" + raidTicks + " ticks) and scatter in +5s (" + scatterTicks + " ticks).");
    }

    /**
     * At t0+raidDuration: the RED-world kill. The GlitchRaid plugin owns the
     * authoritative kill now; we just log and broadcast so operators can see
     * the cycle progressed. If GlitchRaid is not present we perform a local
     * placeholder kill (kills only players in {@code redWorld}).
     */
    private void handleCycleTimeout(int cycle, long cycleStartMillis) {
        plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — t0+" + raidDurationMinutes + "m timeout reached. RED-world extraction window closed.");
        endDynamicCycle(cycle);
        World red = Bukkit.getWorld(redWorld);
        if (red == null) {
            red = Bukkit.getWorld(Worlds.GLITCH_RED);
        }

        // Notify GlitchRaid via reflection if available — it owns the real kill/timeout logic.
        boolean raidHandled = tryNotifyRaidTimeout();
        if (raidHandled) {
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — GlitchRaid timeout handler invoked (RED kill owned by GlitchRaid).");
            return;
        }

        // Placeholder local kill: only if GlitchRaid did not handle it. Kills only RED-world players.
        if (red == null) {
            plugin.getLogger().warning("[AutoExtract] RED world '" + redWorld + "' not found — skipping placeholder kill. Ensure GlitchRaid handles timeout.");
            return;
        }

        int killed = 0;
        for (Player player : new ArrayList<>(red.getPlayers())) {
            if (player == null || !player.isOnline()) continue;
            if (com.theglitch.common.Bots.isBot(player)) continue; // GlitchBots clears its own rogues
            // Never kill spectators/creatives — they are staff/legit exemptions
            try {
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }
            } catch (Exception ignored) {}
            // Only count as GlitchRaid would — but we do a placeholder kill
            try {
                plugin.getLogger().info("[AutoExtract] Placeholder RED kill for " + player.getName() + " (no GlitchRaid handler).");
                player.setHealth(0.0);
                killed++;
            } catch (Exception e) {
                try { player.damage(1000.0); killed++; } catch (Exception ignored) {}
                plugin.getLogger().log(Level.WARNING, "[AutoExtract] Failed to kill " + player.getName() + ": " + e.getMessage(), e);
            }
        }
        if (killed > 0) {
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — placeholder RED kill executed for " + killed + " player(s) in " + red.getName() + ".");
        } else {
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — no players in RED to kill (or GlitchRaid will handle).");
        }
    }

    /**
     * At t0+raidDuration+5s: scatter loot. Fires {@link AutoExtractCycleEndEvent}
     * and asks GlitchItems to re-scatter its containers directly.
     */
    private void handleCycleEndScatter(int cycle, long cycleStartMillis) {
        plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — t0+" + raidDurationMinutes + "m+5s scatter phase. Firing AutoExtractCycleEndEvent.");
        endDynamicCycle(cycle); // no-op if the timeout path already ended it

        // Primary hook — loot team listens for this
        try {
            AutoExtractCycleEndEvent event = new AutoExtractCycleEndEvent(cycleStartMillis, cycle);
            pluginManager.callEvent(event);
            int listeners = AutoExtractCycleEndEvent.getHandlerList().getRegisteredListeners().length;
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — AutoExtractCycleEndEvent fired (" + listeners + " listener(s) registered).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[AutoExtract] Failed to fire AutoExtractCycleEndEvent for cycle #" + cycle, e);
        }

        // Direct GlitchItems container scatter
        boolean scattered = tryDirectScatter();
        if (scattered) {
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — direct scatter manager invoked.");
        } else {
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — GlitchItems unavailable; relying on the cycle-end event.");
        }
    }

    // ------------------------------------------------------------------------
    // Cross-plugin hooks (best-effort, never throw)
    // ------------------------------------------------------------------------

    /**
     * GlitchRaid's RaidManager, or null when GlitchRaid is absent. GlitchRaid compiles
     * against GlitchStash (not the other way round), so this direction uses reflection.
     */
    private Object raidManager() {
        Plugin raid = pluginManager.getPlugin("GlitchRaid");
        if (raid == null || !raid.isEnabled()) return null;
        try {
            return raid.getClass().getMethod("getRaidManager").invoke(raid);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().fine("[AutoExtract] GlitchRaid manager lookup failed: " + e.getMessage());
            return null;
        }
    }

    /** Starts or re-anchors GlitchRaid's global raid for this world at t0. True when GlitchRaid accepted it. */
    private boolean tryNotifyRaidStart() {
        Object manager = raidManager();
        if (manager == null) return false;
        try {
            Object session = manager.getClass().getMethod("startGlobalRaid", String.class, boolean.class)
                    .invoke(manager, redWorld, true);
            return session != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().fine("[AutoExtract] GlitchRaid start hook failed: " + e.getMessage());
            return false;
        }
    }

    /** Hands the t0+raidDuration timeout to GlitchRaid, which owns the red-world kill. True when delivered. */
    private boolean tryNotifyRaidTimeout() {
        Object manager = raidManager();
        if (manager == null) return false;
        try {
            manager.getClass().getMethod("handleGlobalTimeout", String.class).invoke(manager, redWorld);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().fine("[AutoExtract] GlitchRaid timeout hook failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Re-scatters GlitchItems' loot containers. GlitchItems also listens for
     * {@link AutoExtractCycleEndEvent}; its scatter is debounced, so the double trigger is harmless.
     */
    private boolean tryDirectScatter() {
        Plugin items = pluginManager.getPlugin("GlitchItems");
        if (items == null || !items.isEnabled()) return false;
        try {
            GlitchItems.getInstance().getContainerManager().scatter();
            return true;
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().fine("[AutoExtract] GlitchItems scatter failed: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------------
    // Arena discovery & start (legacy path, used only when dynamic extraction declines)
    // ------------------------------------------------------------------------

    /**
     * Arenas to start this cycle: every VelKoth arena, filtered by the
     * {@code auto-extract.arenas} allow-list when it is set. Falls back to the
     * allow-list alone when VelKoth reports nothing. Never null.
     */
    private List<String> discoverArenas() {
        List<String> known = velKothArenaIds();
        List<String> config = getConfiguredArenas();

        if (known == null || known.isEmpty()) {
            if (!config.isEmpty()) {
                plugin.getLogger().info("[AutoExtract] Discovery fallback: using config arenas " + config);
                return new ArrayList<>(config);
            }
            plugin.getLogger().warning("[AutoExtract] No arenas discovered via VelKoth or config. Create one with /koth create <name>.");
            return List.of();
        }
        if (config.isEmpty()) {
            plugin.getLogger().info("[AutoExtract] Discovery via VelKoth API: " + known);
            return known;
        }
        List<String> filtered = new ArrayList<>();
        for (String name : known) {
            for (String allowed : config) {
                if (allowed.equalsIgnoreCase(name)) {
                    filtered.add(name);
                    break;
                }
            }
        }
        if (filtered.isEmpty()) {
            plugin.getLogger().warning("[AutoExtract] VelKoth arenas " + known + " match none of the config allow-list " + config + " — using the config list.");
            return new ArrayList<>(config);
        }
        plugin.getLogger().info("[AutoExtract] Discovery: " + known.size() + " via API, filtered to " + filtered.size() + " by config allow-list " + config);
        return filtered;
    }

    public List<String> getConfiguredArenas() {
        List<String> cfg = this.configuredArenas;
        return cfg == null ? List.of() : List.copyOf(cfg);
    }

    /** VelKoth's arena ids, or null when VelKoth is not loaded. */
    private List<String> velKothArenaIds() {
        if (pluginManager.getPlugin(VELKOTH_PLUGIN_NAME) == null) return null;
        try {
            VelKothPlugin velKoth = VelKothPlugin.getInstance();
            return velKoth == null ? null : new ArrayList<>(velKoth.getArenaManager().getArenaIds());
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().fine("[AutoExtract] VelKoth arena lookup failed: " + e.getMessage());
            return null;
        }
    }

    /** Starts one arena without VelKoth's server-wide announcement (see {@link QuietKoth}). */
    private void startArena(String arena, int cycle) {
        if (arena == null || arena.isBlank()) {
            plugin.getLogger().warning("[AutoExtract] Cycle #" + cycle + " — blank arena name skipped.");
            return;
        }
        String name = arena.trim();
        try {
            VelKothPlugin velKoth = VelKothPlugin.getInstance();
            Arena found = velKoth == null ? null : velKoth.getArenaManager().getArena(name);
            if (found == null) {
                plugin.getLogger().warning("[AutoExtract] Arena '" + name + "' not found in VelKoth (cycle #" + cycle + ").");
            } else if (QuietKoth.start(velKoth, found)) {
                plugin.getLogger().info("[AutoExtract] Started arena '" + name + "' (cycle #" + cycle + ").");
            }
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, "[AutoExtract] Failed to start arena '" + name + "' (cycle #" + cycle + ")", e);
        }
    }

    // ------------------------------------------------------------------------
    // Getters (for status/debug)
    // ------------------------------------------------------------------------

    public boolean isEnabled() { return enabled; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public int getRaidDurationMinutes() { return raidDurationMinutes; }
    public int getBufferMinutes() { return bufferMinutes; }
    public int getCycleCount() { return cycleCounter.get(); }
    public long getLastCycleStartMillis() { return lastCycleStartMillis; }

    /**
     * Remaining ticks until next cycle (for PlaceholderAPI/debug). -1 if not scheduled.
     */
    public long getMillisUntilNextCycle() {
        if (lastCycleStartMillis == 0L) return -1L;
        long next = lastCycleStartMillis + (intervalMinutes * 60L * 1000L);
        long remain = next - System.currentTimeMillis();
        return Math.max(0L, remain);
    }
}
