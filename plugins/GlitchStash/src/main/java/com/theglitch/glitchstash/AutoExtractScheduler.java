package com.theglitch.glitchstash;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Automated extraction scheduler — every {@code intervalMinutes} (default 31)
 * starts <b>all</b> VelKoth arenas even if the world is empty. The cycle is:
 * <ul>
 *   <li><b>t0</b>: discover and start every arena via VelKoth API or
 *       {@code /koth start <arena>} fallback; log every attempt</li>
 *   <li><b>t0 + raidDuration</b> (default 30 min): RED-world timeout kill.
 *       For now the GlitchRaid team owns the actual kill — we just log and
 *       fire a hook that GlitchRaid can listen for</li>
 *   <li><b>t0 + raidDuration + 5s</b>: scatter loot — fire
 *       {@link AutoExtractCycleEndEvent} and, if available, invoke
 *       GlitchLoot / container scatter via reflection</li>
 *   <li><b>t0 + interval</b> (default 31 min): next cycle (handled by the
 *       fixed-rate scheduler with a 1-minute buffer between scatter and
 *       restart)</li>
 * </ul>
 * <p>
 * Arena discovery tries, in order:
 * <ol>
 *   <li>VelKoth API via reflection (no hard compile-time dependency)</li>
 *   <li>{@code auto-extract.arenas} config list (empty = allow all discovered)</li>
 *   <li>{@code plugins/VelKoth/arenas.yml} file parse as last resort</li>
 * </ol>
 * If discovery yields multiple names, each is started independently. An
 * empty config list after failed discovery logs a warning and skips the cycle
 * rather than crashing.
 * <p>
 * Folia-safe: all scheduling goes through {@link FoliaScheduler}
 * ({@code runAtFixedRateGlobal / runLaterGlobal}) so the task runs on the
 * global region on both Paper/Purpur and Folia. See
 * {@link com.theglitch.glitchraid.FoliaScheduler} for the original pattern.
 */
public final class AutoExtractScheduler {

    private static final String VELKOTH_PLUGIN_NAME = "VelKoth";
    private static final String CONFIG_SECTION = "auto-extract";
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static final long SCATTER_DELAY_TICKS = 5L * 20L; // +5s after raid duration

    private final GlitchStash plugin;

    // Cached config — volatile for safe cross-thread reads (Folia may call from global region)
    private volatile boolean enabled = true;
    private volatile int intervalMinutes = 31;
    private volatile int raidDurationMinutes = 30;
    private volatile int bufferMinutes = 1;
    private volatile List<String> configuredArenas = List.of(); // empty = all
    private volatile String redWorld = "glitch_red";

    private volatile FoliaScheduler.Cancellable fixedRateTask;
    private final List<FoliaScheduler.Cancellable> pendingBufferTasks = new ArrayList<>();
    private final AtomicInteger cycleCounter = new AtomicInteger(0);
    private volatile long lastCycleStartMillis = 0L;

    public AutoExtractScheduler(GlitchStash plugin) {
        this.plugin = plugin;
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
            redWorld = "glitch_red";
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

        String world = section.getString("red-world", "glitch_red");
        if (world != null && !world.isBlank()) redWorld = world.trim();

        plugin.getLogger().info("[AutoExtract] Config reloaded — enabled=" + enabled + ", interval=" + intervalMinutes + "m, raidDuration=" + raidDurationMinutes + "m, buffer=" + bufferMinutes + "m, arenas=" + (configuredArenas.isEmpty() ? "<all discovered>" : configuredArenas) + ", redWorld=" + redWorld);
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

        List<String> arenas;
        try {
            arenas = discoverArenas();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[AutoExtract] Arena discovery threw — skipping cycle #" + cycle, e);
            return;
        }

        if (arenas == null || arenas.isEmpty()) {
            plugin.getLogger().warning("[AutoExtract] No arenas discovered — cycle #" + cycle + " will not start any extraction. Check VelKoth arenas.yml or set auto-extract.arenas in GlitchStash config. VelKoth loaded: " + (Bukkit.getPluginManager().getPlugin(VELKOTH_PLUGIN_NAME) != null));
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
        World red = Bukkit.getWorld(redWorld);
        if (red == null) {
            red = Bukkit.getWorld("glitch_red");
        }

        // Notify GlitchRaid via reflection if available — it owns the real kill/timeout logic.
        boolean raidHandled = tryNotifyRaidTimeout(cycle);
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
     * so the loot/container team can react, and tries to invoke a scatter manager
     * via reflection if GlitchLoot or GlitchItems is available.
     */
    private void handleCycleEndScatter(int cycle, long cycleStartMillis) {
        plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — t0+" + raidDurationMinutes + "m+5s scatter phase. Firing AutoExtractCycleEndEvent.");

        // Primary hook — loot team listens for this
        try {
            AutoExtractCycleEndEvent event = new AutoExtractCycleEndEvent(cycleStartMillis, cycle);
            Bukkit.getPluginManager().callEvent(event);
            int listeners = AutoExtractCycleEndEvent.getHandlerList().getRegisteredListeners().length;
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — AutoExtractCycleEndEvent fired (" + listeners + " listener(s) registered).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[AutoExtract] Failed to fire AutoExtractCycleEndEvent for cycle #" + cycle, e);
        }

        // Best-effort direct scatter via reflection (GlitchLoot / container manager)
        boolean scattered = tryDirectScatter(cycle);
        if (scattered) {
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — direct scatter manager invoked.");
        } else {
            plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — no direct scatter manager found; event hook is the integration point for loot team.");
        }
    }

    // ------------------------------------------------------------------------
    // Reflection hooks (best-effort, never throw)
    // ------------------------------------------------------------------------

    /**
     * Tries to notify GlitchRaid's RaidManager to start/anchor global extraction at t0.
     * Returns true if we successfully invoked startGlobalRaid reflectively.
     */
    private boolean tryNotifyRaidStart() {
        Plugin raidPlugin = Bukkit.getPluginManager().getPlugin("GlitchRaid");
        if (raidPlugin == null) return false;
        try {
            Object raidInstance = raidPlugin;
            try {
                Method getInstance = raidPlugin.getClass().getMethod("getInstance");
                Object maybe = getInstance.invoke(null);
                if (maybe != null) raidInstance = maybe;
            } catch (Exception ignored) {}
            Object manager = null;
            for (String m : new String[]{"getRaidManager", "getManager", "getRaidController"}) {
                try {
                    Method method = raidInstance.getClass().getMethod(m);
                    manager = method.invoke(raidInstance);
                    if (manager != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (manager == null) return false;
            // Preferred: startGlobalRaid(String world, boolean auto) with redWorld
            try {
                Method m = manager.getClass().getMethod("startGlobalRaid", String.class, boolean.class);
                Object result = m.invoke(manager, redWorld, true);
                return result != null;
            } catch (NoSuchMethodException ignored) {}
            try {
                Method m2 = manager.getClass().getMethod("startGlobalRaid", String.class);
                Object result = m2.invoke(manager, redWorld);
                return result != null;
            } catch (NoSuchMethodException ignored2) {}
            // Fallback: startGlobalRaid() no args
            try {
                Method m3 = manager.getClass().getMethod("startGlobalRaid");
                Object result = m3.invoke(manager);
                return result != null;
            } catch (NoSuchMethodException ignored3) {}
        } catch (Exception e) {
            plugin.getLogger().fine("[AutoExtract] GlitchRaid start probe failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Tries to notify GlitchRaid's RaidManager of the global timeout. Returns
     * true if we successfully invoked a handler reflectively.
     */
    private boolean tryNotifyRaidTimeout(int cycle) {
        Plugin raidPlugin = Bukkit.getPluginManager().getPlugin("GlitchRaid");
        if (raidPlugin == null) return false;
        try {
            Object raidInstance = raidPlugin;
            try {
                Method getInstance = raidPlugin.getClass().getMethod("getInstance");
                Object maybe = getInstance.invoke(null);
                if (maybe != null) raidInstance = maybe;
            } catch (Exception ignored) {}

            Object manager = null;
            for (String m : new String[]{"getRaidManager", "getManager", "getRaidController"}) {
                try {
                    Method method = raidInstance.getClass().getMethod(m);
                    manager = method.invoke(raidInstance);
                    if (manager != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (manager == null) return false;

            // Try handleGlobalTimeout with worldKey (now public) — most specific
            String targetWorld = redWorld != null && !redWorld.isBlank() ? redWorld : "glitch_red";
            try {
                Method method = manager.getClass().getMethod("handleGlobalTimeout", String.class);
                method.invoke(manager, targetWorld);
                return true;
            } catch (NoSuchMethodException ignored) {}
            try {
                Method method2 = manager.getClass().getMethod("handleGlobalTimeout");
                method2.invoke(manager);
                return true;
            } catch (NoSuchMethodException ignored2) {}
            // Fallback to auto-extract specific handlers
            for (String handler : new String[]{"handleAutoExtractTimeout", "onAutoExtractTimeout", "handleCycleTimeout", "globalTimeout"}) {
                try {
                    Method method = manager.getClass().getMethod(handler);
                    method.invoke(manager);
                    return true;
                } catch (NoSuchMethodException ignored) {}
                try {
                    Method method2 = manager.getClass().getMethod(handler, int.class);
                    method2.invoke(manager, cycle);
                    return true;
                } catch (NoSuchMethodException ignored2) {}
                try {
                    Method method3 = manager.getClass().getMethod(handler, String.class);
                    method3.invoke(manager, targetWorld);
                    return true;
                } catch (NoSuchMethodException ignored3) {}
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[AutoExtract] GlitchRaid notify probe failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Best-effort direct scatter: tries GlitchLoot scatter / container reset via reflection.
     */
    private boolean tryDirectScatter(int cycle) {
        // Try GlitchLoot
        Plugin lootPlugin = Bukkit.getPluginManager().getPlugin("GlitchLoot");
        if (lootPlugin != null) {
            try {
                for (String m : new String[]{"getScatterManager", "getLootEngine", "getManager", "getContainerManager"}) {
                    try {
                        Method method = lootPlugin.getClass().getMethod(m);
                        Object manager = method.invoke(lootPlugin);
                        if (manager == null) continue;
                        for (String scatter : new String[]{"scatter", "scatterLoot", "onCycleEnd", "handleCycleEnd", "doScatter"}) {
                            try {
                                Method sm = manager.getClass().getMethod(scatter);
                                sm.invoke(manager);
                                return true;
                            } catch (NoSuchMethodException ignored) {}
                            try {
                                Method sm2 = manager.getClass().getMethod(scatter, int.class);
                                sm2.invoke(manager, cycle);
                                return true;
                            } catch (NoSuchMethodException ignored2) {}
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Exception e) {
                plugin.getLogger().fine("[AutoExtract] GlitchLoot scatter probe failed: " + e.getMessage());
            }
        }
        // Try GlitchItems containers
        Plugin itemsPlugin = Bukkit.getPluginManager().getPlugin("GlitchItems");
        if (itemsPlugin != null) {
            try {
                for (String m : new String[]{"getContainerManager", "getLootManager", "getManager"}) {
                    try {
                        Method method = itemsPlugin.getClass().getMethod(m);
                        Object manager = method.invoke(itemsPlugin);
                        if (manager == null) continue;
                        for (String scatter : new String[]{"scatter", "resetContainers", "onCycleEnd"}) {
                            try {
                                Method sm = manager.getClass().getMethod(scatter);
                                sm.invoke(manager);
                                return true;
                            } catch (NoSuchMethodException ignored) {}
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Exception e) {
                plugin.getLogger().fine("[AutoExtract] GlitchItems scatter probe failed: " + e.getMessage());
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // Arena discovery & start
    // ------------------------------------------------------------------------

    /**
     * Discovers all VelKoth arena names. Order of attempts:
     * <ol>
     *   <li>VelKoth API via reflection</li>
     *   <li>Config {@code auto-extract.arenas} filtering</li>
     *   <li>Parse {@code plugins/VelKoth/arenas.yml} file directly</li>
     * </ol>
     * If reflection returns candidates and config list is non-empty, the list
     * is filtered to only those in the config (config = allow-list). If config
     * is empty, all discovered arenas are returned. If everything fails, returns
     * an empty list (never null when config fallback is empty).
     */
    private List<String> discoverArenas() {
        List<String> viaApi = discoverViaReflection();
        List<String> config = getConfiguredArenas();

        if (viaApi != null && !viaApi.isEmpty()) {
            if (config != null && !config.isEmpty()) {
                // Config is an allow-list — return intersection (or config if names differ only by case)
                List<String> filtered = new ArrayList<>();
                for (String name : viaApi) {
                    for (String allowed : config) {
                        if (allowed.equalsIgnoreCase(name)) {
                            filtered.add(name);
                            break;
                        }
                    }
                }
                if (!filtered.isEmpty()) {
                    plugin.getLogger().info("[AutoExtract] Discovery: " + viaApi.size() + " via API, filtered to " + filtered.size() + " by config allow-list " + config);
                    return filtered;
                }
                plugin.getLogger().warning("[AutoExtract] API discovered " + viaApi + " but none match config allow-list " + config + " — falling back to config list.");
                return new ArrayList<>(config);
            }
            plugin.getLogger().info("[AutoExtract] Discovery via VelKoth API: " + viaApi);
            return viaApi;
        }

        if (viaApi == null) {
            plugin.getLogger().fine("[AutoExtract] No VelKoth API result — trying file/config fallback.");
        } else {
            plugin.getLogger().fine("[AutoExtract] VelKoth API returned empty — trying file/config fallback.");
        }

        if (config != null && !config.isEmpty()) {
            plugin.getLogger().info("[AutoExtract] Discovery fallback: using config arenas " + config);
            return new ArrayList<>(config);
        }

        List<String> viaFile = loadFromArenasYml();
        if (viaFile != null && !viaFile.isEmpty()) {
            plugin.getLogger().info("[AutoExtract] Discovery via arenas.yml file: " + viaFile);
            return viaFile;
        }

        plugin.getLogger().warning("[AutoExtract] No arenas discovered via API, config, or arenas.yml. VelKoth may have no arenas yet — create one with /koth create <name>.");
        return List.of();
    }

    public List<String> getConfiguredArenas() {
        List<String> cfg = this.configuredArenas;
        return cfg == null ? List.of() : List.copyOf(cfg);
    }

    /**
     * Attempts to discover arenas via VelKoth's live plugin instance using
     * reflection. Probes multiple plausible API shapes to survive version
     * changes without a hard compile-time dependency.
     * <p>
     * Returns null on total probe failure (caller will try fallback), or a
     * possibly-empty list if probing succeeded but no arenas exist.
     */
    private List<String> discoverViaReflection() {
        Plugin velKoth = Bukkit.getPluginManager().getPlugin(VELKOTH_PLUGIN_NAME);
        if (velKoth == null) {
            plugin.getLogger().fine("[AutoExtract] VelKoth plugin not found — reflection discovery skipped.");
            return null;
        }

        Class<?> pluginClass = velKoth.getClass();

        // --- 1) Direct methods on the plugin instance (most common) ---
        String[] managerMethods = {
                "getArenaManager", "getKothManager", "getKothHandler",
                "getManager", "getKoths", "getArenas", "getKothMap", "getArenaMap",
                "getKothRegistry", "getArenaRegistry"
        };

        for (String methodName : managerMethods) {
            try {
                Method method = pluginClass.getMethod(methodName);
                Object result = method.invoke(velKoth);
                if (result == null) continue;
                List<String> names = extractArenaNames(result);
                if (names != null && !names.isEmpty()) {
                    plugin.getLogger().info("[AutoExtract] Discovered " + names.size() + " arenas via VelKoth." + methodName + ": " + names);
                    return names;
                }
                // Empty means probing succeeded but no arenas — return empty to signal "found but none"
                List<String> emptyCheck = extractArenaNames(result);
                if (emptyCheck != null) return emptyCheck; // empty list
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                plugin.getLogger().fine("[AutoExtract] Probe VelKoth." + methodName + " failed: " + e.getMessage());
            }
        }

        // --- 2) VelKothAPI singleton (dev.velmax.velkoth.api.VelKothAPI) ---
        try {
            Class<?> apiClass = Class.forName("dev.velmax.velkoth.api.VelKothAPI");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            if (api != null) {
                for (String methodName : managerMethods) {
                    try {
                        Method method = api.getClass().getMethod(methodName);
                        Object result = method.invoke(api);
                        List<String> names = extractArenaNames(result);
                        if (names != null && !names.isEmpty()) {
                            plugin.getLogger().info("[AutoExtract] Discovered via VelKothAPI." + methodName + ": " + names);
                            return names;
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
                // Generic scan: any member containing arena/koth
                for (Method m : api.getClass().getMethods()) {
                    if (m.getParameterCount() != 0) continue;
                    String n = m.getName().toLowerCase();
                    if (!n.contains("arena") && !n.contains("koth")) continue;
                    try {
                        Object result = m.invoke(api);
                        List<String> names = extractArenaNames(result);
                        if (names != null && !names.isEmpty()) {
                            plugin.getLogger().info("[AutoExtract] Discovered via VelKothAPI." + m.getName() + ": " + names);
                            return names;
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (ClassNotFoundException ignored) {
            // No VelKothAPI class — expected on older builds
        } catch (Exception e) {
            plugin.getLogger().fine("[AutoExtract] VelKothAPI probe failed: " + e.getMessage());
        }

        // --- 3) Generic scan of plugin class for any arena/koth accessor ---
        for (Method method : pluginClass.getMethods()) {
            if (method.getParameterCount() != 0) continue;
            String lower = method.getName().toLowerCase();
            if (!lower.contains("arena") && !lower.contains("koth")) continue;
            try {
                Object result = method.invoke(velKoth);
                List<String> names = extractArenaNames(result);
                if (names != null && !names.isEmpty()) {
                    plugin.getLogger().info("[AutoExtract] Discovered via generic scan VelKoth." + method.getName() + ": " + names);
                    return names;
                }
            } catch (Exception ignored) {}
        }

        // --- 4) Fields on plugin class ---
        for (java.lang.reflect.Field field : pluginClass.getDeclaredFields()) {
            String fname = field.getName().toLowerCase();
            if (!fname.contains("arena") && !fname.contains("koth") && !fname.contains("map")) continue;
            try {
                field.setAccessible(true);
                Object result = field.get(velKoth);
                List<String> names = extractArenaNames(result);
                if (names != null && !names.isEmpty()) {
                    plugin.getLogger().info("[AutoExtract] Discovered via VelKoth field '" + field.getName() + "': " + names);
                    return names;
                }
            } catch (Exception ignored) {}
        }

        return null; // total miss — caller will fallback
    }

    /**
     * Tries to extract arena names from a manager result: Collection, Map, or
     * an object whose methods/fields contain the arena collection/map.
     */
    private List<String> extractArenaNames(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Collection<?> col) {
            return tryNamesFromCollection(col);
        }
        if (obj instanceof Map<?, ?> map) {
            // Prefer keys if they are strings, else values
            if (!map.isEmpty()) {
                Object firstKey = map.keySet().iterator().next();
                if (firstKey instanceof String) {
                    List<String> out = new ArrayList<>(map.size());
                    for (Object k : map.keySet()) out.add(k.toString());
                    return out;
                }
            }
            List<String> fromValues = tryNamesFromCollection(map.values());
            if (fromValues != null && !fromValues.isEmpty()) return fromValues;
            List<String> out = new ArrayList<>(map.size());
            for (Object k : map.keySet()) out.add(k.toString());
            return out.isEmpty() ? null : out;
        }

        // Probe manager object for nested accessors
        String[] nested = {"getArenas", "getKoths", "getAllArenas", "getAllKoths", "getKothMap", "getArenaMap", "getRegistry", "values", "keySet", "entrySet"};
        for (String m : nested) {
            try {
                Method method = obj.getClass().getMethod(m);
                if (method.getParameterCount() != 0) continue;
                Object result = method.invoke(obj);
                if (result instanceof Collection<?> col) {
                    List<String> names = tryNamesFromCollection(col);
                    if (names != null && !names.isEmpty()) return names;
                }
                if (result instanceof Map<?, ?> map) {
                    List<String> names = tryNamesFromMap(map);
                    if (names != null && !names.isEmpty()) return names;
                }
            } catch (Exception ignored) {}
        }

        // Field scan on manager
        for (java.lang.reflect.Field field : obj.getClass().getDeclaredFields()) {
            String fname = field.getName().toLowerCase();
            if (!fname.contains("arena") && !fname.contains("koth") && !fname.contains("map") && !fname.contains("registry")) continue;
            try {
                field.setAccessible(true);
                Object val = field.get(obj);
                if (val instanceof Collection<?> col) {
                    List<String> names = tryNamesFromCollection(col);
                    if (names != null && !names.isEmpty()) return names;
                }
                if (val instanceof Map<?, ?> map) {
                    List<String> names = tryNamesFromMap(map);
                    if (names != null && !names.isEmpty()) return names;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<String> tryNamesFromCollection(Collection<?> col) {
        if (col == null || col.isEmpty()) return List.of(); // empty but valid probe — return empty to short-circuit fallback
        List<String> out = new ArrayList<>(col.size());
        for (Object o : col) {
            if (o == null) continue;
            if (o instanceof String s) {
                if (!s.isBlank()) out.add(s);
                continue;
            }
            if (o instanceof Map.Entry<?, ?> entry) {
                Object k = entry.getKey();
                if (k != null) { out.add(k.toString()); continue; }
            }
            // Reflective getName / getId
            String name = reflectiveName(o);
            if (name != null && !name.isBlank()) out.add(name);
            else out.add(o.toString());
        }
        return out.isEmpty() ? List.of() : out;
    }

    private List<String> tryNamesFromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return List.of();
        // Prefer string keys
        boolean allStringKeys = true;
        for (Object k : map.keySet()) {
            if (!(k instanceof String)) { allStringKeys = false; break; }
        }
        if (allStringKeys) {
            List<String> out = new ArrayList<>(map.size());
            for (Object k : map.keySet()) out.add(k.toString());
            return out;
        }
        List<String> viaValues = tryNamesFromCollection(map.values());
        if (viaValues != null && !viaValues.isEmpty()) return viaValues;
        List<String> out = new ArrayList<>(map.size());
        for (Object k : map.keySet()) if (k != null) out.add(k.toString());
        return out.isEmpty() ? List.of() : out;
    }

    private String reflectiveName(Object o) {
        for (String m : new String[]{"getName", "getId", "getArenaName", "getKothName", "name", "id"}) {
            try {
                Method method = o.getClass().getMethod(m);
                Object val = method.invoke(o);
                if (val != null) return val.toString();
            } catch (Exception ignored) {}
        }
        // Try field 'name'
        try {
            java.lang.reflect.Field f = o.getClass().getDeclaredField("name");
            f.setAccessible(true);
            Object val = f.get(o);
            if (val != null) return val.toString();
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Last-resort file parse: reads {@code plugins/VelKoth/arenas.yml} and
     * returns top-level keys as arena names. Handles both possible server
     * layouts: {@code plugins/VelKoth/arenas.yml} relative to CWD, and via
     * {@code Bukkit.getWorldContainer()}.
     */
    private List<String> loadFromArenasYml() {
        File file = null;
        File[] candidates = {
                new File("plugins" + File.separator + "VelKoth" + File.separator + "arenas.yml"),
                new File(Bukkit.getWorldContainer(), "plugins" + File.separator + "VelKoth" + File.separator + "arenas.yml"),
                new File(plugin.getDataFolder().getParentFile(), "VelKoth" + File.separator + "arenas.yml")
        };
        for (File c : candidates) {
            if (c != null && c.exists() && c.isFile()) { file = c; break; }
        }
        if (file == null) {
            plugin.getLogger().fine("[AutoExtract] arenas.yml not found in candidates.");
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.getKeys(false).isEmpty()) {
                plugin.getLogger().info("[AutoExtract] arenas.yml exists but has no arenas at " + file.getPath());
                return List.of();
            }
            List<String> out = new ArrayList<>(yaml.getKeys(false));
            plugin.getLogger().info("[AutoExtract] Parsed " + out.size() + " arena(s) from " + file.getPath() + ": " + out);
            return out;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[AutoExtract] Failed to parse " + file.getPath(), e);
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Start single arena
    // ------------------------------------------------------------------------

    private void startArena(String arena, int cycle) {
        if (arena == null || arena.isBlank()) {
            plugin.getLogger().warning("[AutoExtract] Cycle #" + cycle + " — blank arena name skipped.");
            return;
        }
        String name = arena.trim();
        plugin.getLogger().info("[AutoExtract] Cycle #" + cycle + " — starting arena '" + name + "' (even if empty)...");

        // 1) Try direct API start (no player check, no nearby-players gate)
        boolean viaApi = tryStartViaApi(name, cycle);
        if (viaApi) {
            plugin.getLogger().info("[AutoExtract] Arena '" + name + "' started via VelKoth API (cycle #" + cycle + ").");
            return;
        }

        // 2) Fallback: dispatch console command "koth start <arena>" — VelKoth
        //    registers /koth start <arena> and does not require a player sender.
        try {
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "koth start " + name);
            // dispatchCommand returns false only if command is unknown; still warn on false
            if (dispatched) {
                plugin.getLogger().info("[AutoExtract] Dispatched 'koth start " + name + "' (cycle #" + cycle + ") — dispatched=true");
            } else {
                plugin.getLogger().warning("[AutoExtract] Dispatched 'koth start " + name + "' but Bukkit returned false — command may be unknown. VelKoth loaded: " + (Bukkit.getPluginManager().getPlugin(VELKOTH_PLUGIN_NAME) != null));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[AutoExtract] dispatchCommand 'koth start " + name + "' failed (cycle #" + cycle + ")", e);
        }
    }

    /**
     * Tries to start the arena via VelKoth's internal API to avoid the command
     * path. Probes multiple plausible signatures. Returns true if any probe
     * reported success (we treat any non-exceptional reflection invoke as success
     * unless it explicitly returns Boolean.FALSE).
     */
    private boolean tryStartViaApi(String arena, int cycle) {
        Plugin velKoth = Bukkit.getPluginManager().getPlugin(VELKOTH_PLUGIN_NAME);
        if (velKoth == null) return false;

        // Probe plugin instance methods like startArena/startKoth/activate/forceStart
        String[] startMethods = {"startArena", "startKoth", "start", "activate", "activateKoth", "forceStart", "trigger", "begin"};
        for (String m : startMethods) {
            try {
                Method method = velKoth.getClass().getMethod(m, String.class);
                Object result = method.invoke(velKoth, arena);
                if (result instanceof Boolean b) {
                    if (b) return true;
                    plugin.getLogger().fine("[AutoExtract] VelKoth." + m + "(\"" + arena + "\") returned false — trying fallback.");
                    continue;
                }
                return true; // void or non-boolean = assume started
            } catch (NoSuchMethodException ignored) {}
            catch (Exception e) {
                plugin.getLogger().fine("[AutoExtract] Probe VelKoth." + m + " reflection failed: " + e.getMessage());
            }
        }

        // Probe manager.start(...)
        for (String mm : new String[]{"getArenaManager", "getKothManager", "getKothHandler", "getManager"}) {
            try {
                Method gm = velKoth.getClass().getMethod(mm);
                Object manager = gm.invoke(velKoth);
                if (manager == null) continue;
                for (String sm : startMethods) {
                    try {
                        Method method = manager.getClass().getMethod(sm, String.class);
                        Object result = method.invoke(manager, arena);
                        if (result instanceof Boolean b) {
                            if (b) return true;
                            continue;
                        }
                        return true;
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        // Try arena object form: manager.getArena(name).start()
                        Method getOne = manager.getClass().getMethod("getArena", String.class);
                        Object arenaObj = getOne.invoke(manager, arena);
                        if (arenaObj == null) {
                            getOne = manager.getClass().getMethod("getKoth", String.class);
                            arenaObj = getOne.invoke(manager, arena);
                        }
                        if (arenaObj != null) {
                            for (String am : new String[]{"start", "activate", "begin", "forceStart"}) {
                                try {
                                    Method amMethod = arenaObj.getClass().getMethod(am);
                                    amMethod.invoke(arenaObj);
                                    return true;
                                } catch (NoSuchMethodException ignored2) {}
                            }
                        }
                    } catch (Exception ignored2) {}
                }
            } catch (Exception ignored) {}
        }

        // Try Koth/Arena objects directly
        List<String> candidates = List.of(arena);
        for (String probeName : candidates) {
            try {
                World w = Bukkit.getWorld(redWorld);
                // No arena object available without discovery — skip
                break;
            } catch (Exception ignored) {}
        }

        return false;
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
