package com.theglitch.glitchraid;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active raid sessions, bossbars, and timers.
 * Each player UUID maps to a shared RaidSession instance (party support).
 * Loot and deaths are per-player (not shared) as requested.
 */
public final class RaidManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;
    private final PartyManager partyManager;

    private final Map<UUID, RaidSession> activeRaids = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, FoliaScheduler.Cancellable> timers = new ConcurrentHashMap<>();
    private final Set<UUID> timeoutVictims = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastDeathMillis = new ConcurrentHashMap<>();

    // --- Global session support: ONE shared 30m extraction per world (not per player) ---
    // Global auto-cycle is 31m (30m extraction + 1m scatter buffer). All players who
    // enter glitch_red mid-raid must see the *remaining* time of the running global
    // extraction, not a fresh 30m. The scheduler (or first entrant fallback) calls
    // startGlobalRaid(world); late joiners call addToGlobalSession(player).
    private final Map<String, RaidSession> globalSessions = new ConcurrentHashMap<>();
    private final Map<String, BossBar> globalBossBars = new ConcurrentHashMap<>();
    private final Map<String, FoliaScheduler.Cancellable> globalTimers = new ConcurrentHashMap<>();

    // Cached config values
    private volatile int durationSeconds = 1800;
    private volatile int summaryDelayTicks = 40;
    private volatile int partyMaxSize = 4;
    private volatile double payoutMultiplier = 1.0;
    private volatile String hubWorld = "hub";
    private volatile String autoStartWorld = "glitch_red";
    private volatile String joinMode = "global-remaining";

    public RaidManager(GlitchRaid plugin) {
        this.plugin = plugin;
        cacheConfig();
        this.partyManager = new PartyManager(plugin);
    }

    public void cacheConfig() {
        try {
            int duration = plugin.getConfig().getInt("raid.duration-seconds", 1800);
            if (duration < 10 || duration > 86400) {
                plugin.getLogger().warning("Invalid raid.duration-seconds " + duration + " — clamped to 1800.");
                duration = Math.max(10, Math.min(duration, 86400));
            }
            durationSeconds = duration;

            int delay = plugin.getConfig().getInt("raid.summary-delay-ticks", 40);
            if (delay < 0 || delay > 600) {
                plugin.getLogger().warning("Invalid raid.summary-delay-ticks " + delay + " — clamped to 40.");
                delay = Math.max(0, Math.min(delay, 600));
            }
            summaryDelayTicks = delay;

            int maxSize = plugin.getConfig().getInt("raid.party-max-size", 4);
            if (maxSize < 1 || maxSize > 8) {
                plugin.getLogger().warning("Invalid raid.party-max-size " + maxSize + " — clamped to 4.");
                maxSize = Math.max(1, Math.min(maxSize, 8));
            }
            partyMaxSize = maxSize;

            double payout = plugin.getConfig().getDouble("raid.payout-multiplier", 1.0);
            if (payout < 0.0 || payout > 100.0) {
                plugin.getLogger().warning("Invalid raid.payout-multiplier " + payout + " — clamped to 1.0.");
                payout = Math.max(0.0, Math.min(payout, 100.0));
            }
            payoutMultiplier = payout;

            String hub = plugin.getConfig().getString("raid.hub-world", "hub");
            if (hub != null && !hub.isBlank()) hubWorld = hub;
            String auto = plugin.getConfig().getString("raid.auto-start-world", "glitch_red");
            if (auto != null && !auto.isBlank()) autoStartWorld = auto;

            String jm = plugin.getConfig().getString("raid.join-mode", "global-remaining");
            if (jm != null && !jm.isBlank()) {
                jm = jm.trim().toLowerCase(java.util.Locale.ROOT);
                if (!jm.equals("global-remaining") && !jm.equals("solo-new")) {
                    plugin.getLogger().warning("Invalid raid.join-mode " + jm + " — clamped to global-remaining.");
                    jm = "global-remaining";
                }
                joinMode = jm;
            } else {
                joinMode = "global-remaining";
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to cache GlitchRaid config: " + e.getMessage());
        }
    }

    public void reload() {
        cacheConfig();
        if (partyManager != null) partyManager.reload();
        plugin.getLogger().info("RaidManager reloaded (duration=" + durationSeconds + "s, payout=" + payoutMultiplier + ", partyMax=" + partyMaxSize + ", hub=" + hubWorld + ", autoWorld=" + autoStartWorld + ", joinMode=" + joinMode + ").");
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public boolean isInRaid(UUID uuid) {
        return activeRaids.containsKey(uuid);
    }

    public RaidSession getSession(UUID uuid) {
        return activeRaids.get(uuid);
    }

    public Collection<RaidSession> getAllSessions() {
        Set<RaidSession> all = new HashSet<>(activeRaids.values());
        all.addAll(globalSessions.values());
        return all;
    }

    public int getActiveCount() {
        return getAllSessions().size();
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getSummaryDelayTicks() {
        return summaryDelayTicks;
    }

    public int getPartyMaxSize() {
        return partyMaxSize;
    }

    public double getPayoutMultiplier() {
        return payoutMultiplier;
    }

    public String getHubWorld() {
        return hubWorld;
    }

    public String getAutoStartWorld() {
        return autoStartWorld;
    }

    public String getJoinMode() {
        return joinMode;
    }

    public boolean isGlobalRemainingMode() {
        return "global-remaining".equalsIgnoreCase(joinMode);
    }

    // ---- Global session helpers ------------------------------------------------

    private String normalizeWorldKey(String world) {
        if (world == null || world.isBlank()) return autoStartWorld.toLowerCase(java.util.Locale.ROOT);
        return world.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private UUID globalLeaderId(String worldKey) {
        // Deterministic UUID per world so the global session has a stable "leader" key
        return UUID.nameUUIDFromBytes(("global:" + worldKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private boolean isGlobalSession(RaidSession session) {
        if (session == null) return false;
        String key = normalizeWorldKey(autoStartWorld);
        RaidSession global = globalSessions.get(key);
        if (global != null && global == session) return true;
        // Fallback: check any global session by leader UUID
        for (Map.Entry<String, RaidSession> e : globalSessions.entrySet()) {
            if (e.getValue() == session) return true;
        }
        // Also check by leaderId equality for synthetic global leader
        for (String k : globalSessions.keySet()) {
            if (session.getLeader().equals(globalLeaderId(k))) return true;
        }
        return false;
    }

    /** Returns the active global session for world, or null if none / expired. Folia-safe. */
    public RaidSession findActiveGlobalSession(String world) {
        String key = normalizeWorldKey(world);
        RaidSession session = globalSessions.get(key);
        if (session == null) return null;
        // Treat expired (remaining <=0) as inactive for joiners — timeout will handle cleanup
        if (session.getRemainingSeconds() <= 0) return null;
        return session;
    }

    /** Raw getter (may be expired) — use {@link #findActiveGlobalSession(String)} to check liveness. */
    public RaidSession getGlobalSession(String world) {
        return globalSessions.get(normalizeWorldKey(world));
    }

    public boolean isGlobalRaidActive(String world) {
        return findActiveGlobalSession(world) != null;
    }

    /**
     * Starts a new global extraction for world (31m cycle: 30m active + 1m scatter buffer).
     * If a non-expired global already exists, returns it without creating a duplicate.
     * Scheduler calls this every 31m; the first solo entrant also falls back to it.
     */
    public synchronized RaidSession startGlobalRaid(String world) {
        return startGlobalRaid(world, true);
    }

    /**
     * Starts a global raid in world with auto flag. Synchronized to prevent double-create.
     * Folia-safe: BossBar + timer are GlobalRegionScheduler based.
     * <p>
     * Syncs to GlitchStash's AutoExtract cycle if available: if the 31m scheduler has a
     * recent cycle start (within last interval), the global's end is anchored to
     * {@code cycleStart + raidDuration} so late joiners see the correct remaining
     * time (e.g. join at 14m remaining → 14m, not fresh 30m).
     * </p>
     */
    public synchronized RaidSession startGlobalRaid(String world, boolean auto) {
        if (world == null || world.isBlank()) world = autoStartWorld;
        String key = normalizeWorldKey(world);
        RaidSession existing = globalSessions.get(key);
        if (existing != null && existing.getRemainingSeconds() > 0) {
            return existing; // already running
        }
        if (existing != null) {
            // stale expired session — clean before recreating
            try { endGlobalRaid(world, RaidEndReason.TIMEOUT); } catch (Exception ignored) {}
        }
        // Buffer check: if we are in the 1m scatter buffer, do NOT start a fresh raid — wait for next t0
        if (isInBufferPeriod()) {
            long remainMs = getMillisUntilNextCycle();
            plugin.getLogger().info("Global raid start suppressed — in 1m buffer, next cycle in " + formatTime((int) Math.max(0, remainMs / 1000)) + " (world=" + world + ")");
            return null;
        }
        long now = System.currentTimeMillis();
        long end = now + (durationSeconds * 1000L);
        // Try to anchor to GlitchStash AutoExtract cycle so extraction remaining is authoritative
        try {
            long[] stashInfo = getStashCycleInfo(); // [cycleStartMillis, raidDurationMinutes]
            if (stashInfo != null && stashInfo[0] > 0 && stashInfo[1] > 0) {
                long stashStart = stashInfo[0];
                long stashRaidMs = stashInfo[1] * 60_000L;
                long stashEnd = stashStart + stashRaidMs;
                long stashNext = stashStart + (stashInfo.length > 2 ? stashInfo[2] * 60_000L : stashRaidMs + 60_000L);
                // If we are inside the active raid window (t0 .. t0+30m) use stashEnd
                if (stashEnd > now && stashStart <= now && stashEnd < end) {
                    end = stashEnd;
                    plugin.getLogger().info("Global raid anchored to Stash cycle: stashStart=" + stashStart + " stashEnd=" + stashEnd + " remaining=" + formatTime((int)((stashEnd - now)/1000)));
                } else if (stashStart > now) {
                    // Clock skew — ignore
                } else if (stashEnd <= now && stashNext > now) {
                    // In 1m buffer — should have been caught above, but double-guard
                    plugin.getLogger().info("In buffer per stash timing — suppressing fresh global (remaining buffer " + formatTime((int)((stashNext - now)/1000)) + ")");
                    return null;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Could not anchor global to Stash cycle: " + e.getMessage());
        }
        int initialSeconds = (int) Math.max(0, (end - now) / 1000);
        if (initialSeconds <= 0) initialSeconds = durationSeconds;
        Set<UUID> members = ConcurrentHashMap.newKeySet();
        UUID leaderId = globalLeaderId(key);
        RaidSession session = new RaidSession(leaderId, members, now, end);
        globalSessions.put(key, session);

        String timeLeftRaw = plugin.getConfig().getString("messages.raid-time-left", "<aqua>Time left: <white><time></white></aqua>");
        String formatted = formatTime(initialSeconds);
        Component initialName = MM.deserialize(timeLeftRaw.replace("<time>", formatted));
        BossBar bar = BossBar.bossBar(initialName, 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        globalBossBars.put(key, bar);

        FoliaScheduler.Cancellable task = FoliaScheduler.runAtFixedRateGlobal(plugin, () -> tickGlobal(key), 20L, 20L);
        globalTimers.put(key, task);

        plugin.getLogger().info("Global raid started for world " + world + " (key=" + key + ", duration=" + initialSeconds + "s (requested " + durationSeconds + "s), auto=" + auto + ", leader=" + leaderId + ")");
        return session;
    }

    /**
     * Tries to get Stash cycle info via reflection: [lastCycleStartMillis, raidDurationMinutes, intervalMinutes].
     * Returns null if Stash not present or reflection fails.
     */
    private long[] getStashCycleInfo() {
        try {
            Plugin stashPlugin = Bukkit.getPluginManager().getPlugin("GlitchStash");
            if (stashPlugin == null || !stashPlugin.isEnabled()) return null;
            Object stashInstance = stashPlugin;
            try {
                java.lang.reflect.Method getInstance = stashPlugin.getClass().getMethod("getInstance");
                Object maybe = getInstance.invoke(null);
                if (maybe != null) stashInstance = maybe;
            } catch (Exception ignored) {}
            Object scheduler = null;
            for (String m : new String[]{"getAutoExtractScheduler", "getScheduler", "getExtractScheduler"}) {
                try {
                    java.lang.reflect.Method method = stashInstance.getClass().getMethod(m);
                    scheduler = method.invoke(stashInstance);
                    if (scheduler != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (scheduler == null) return null;
            java.lang.reflect.Method getLast = scheduler.getClass().getMethod("getLastCycleStartMillis");
            java.lang.reflect.Method getRaidDur = scheduler.getClass().getMethod("getRaidDurationMinutes");
            java.lang.reflect.Method getInterval = scheduler.getClass().getMethod("getIntervalMinutes");
            long last = (long) getLast.invoke(scheduler);
            int raidDur = (int) getRaidDur.invoke(scheduler);
            int interval = (int) getInterval.invoke(scheduler);
            return new long[]{last, raidDur, interval};
        } catch (Exception e) {
            return null;
        }
    }

    /** Public accessor for remaining seconds of global in world, or -1 if none. */
    public int getGlobalRemainingSeconds(String world) {
        RaidSession s = findActiveGlobalSession(world);
        return s == null ? -1 : s.getRemainingSeconds();
    }

    /** Whether we are inside the 1m buffer between raid end and next cycle. */
    public boolean isInBufferPeriod() {
        long[] info = getStashCycleInfo();
        if (info == null) return false;
        long start = info[0];
        int raidMins = (int) info[1];
        int intervalMins = (int) info[2];
        if (start <= 0 || raidMins <= 0 || intervalMins <= 0) return false;
        long raidEnd = start + raidMins * 60_000L;
        long nextStart = start + intervalMins * 60_000L;
        long now = System.currentTimeMillis();
        return now >= raidEnd && now < nextStart;
    }

    public long getMillisUntilNextCycle() {
        long[] info = getStashCycleInfo();
        if (info == null) return -1;
        long start = info[0];
        int intervalMins = (int) info[2];
        if (start <= 0 || intervalMins <= 0) return -1;
        long next = start + intervalMins * 60_000L;
        long remain = next - System.currentTimeMillis();
        return Math.max(0, remain);
    }

    /** Public hook for AutoExtractScheduler to force timeout kill (t0+30m). */
    public void handleAutoExtractTimeout() {
        String key = normalizeWorldKey(autoStartWorld);
        handleGlobalTimeout(key);
    }

    public void handleAutoExtractTimeout(int cycle) {
        handleAutoExtractTimeout();
    }

    /**
     * Adds a solo player to the ongoing global extraction with *remaining* time.
     * If no global is active and joinMode is global-remaining, a new global is started
     * (fallback so the first entrant after the 1m buffer gets a proper 30m).
     * If joinMode is solo-new, delegates to {@link #startRaid(Player, boolean)}.
     *
     * @return true if added/shown bossbar, false if already in raid or failed
     */
    public boolean addToGlobalSession(Player player) {
        if (player == null) return false;
        return addToGlobalSession(player, player.getWorld().getName());
    }

    /**
     * Adds player to global session in world, showing bossbar with remaining time.
     * Folia-safe, null-safe.
     */
    public boolean addToGlobalSession(Player player, String world) {
        if (player == null || world == null || world.isBlank()) return false;
        String key = normalizeWorldKey(world);
        RaidSession session = findActiveGlobalSession(world);
        if (session == null) {
            if (isGlobalRemainingMode()) {
                if (isInBufferPeriod()) {
                    long remainMs = getMillisUntilNextCycle();
                    String remain = formatTime((int) Math.max(0, remainMs / 1000));
                    try {
                        player.sendMessage(MM.deserialize("<yellow>Extraction is between cycles — <gray>next extraction in <white>" + remain + "</white>. Wait for the next 30m window.</gray>"));
                        player.sendActionBar(MM.deserialize("<gray>Next extraction: <white>" + remain + "</white></gray>"));
                    } catch (Exception ignored) {}
                    plugin.getLogger().info("Player " + player.getName() + " entered " + world + " during 1m buffer — not added to raid (next in " + remain + ")");
                    return false;
                }
                // No active global — fallback: start one now in the target world so the joiner
                // contributes to the shared timer rather than getting a fresh per-player timer.
                // This is used when scheduler hasn't yet created the 31m cycle.
                session = startGlobalRaid(world, true);
                if (session == null) {
                    // start suppressed due to buffer — already messaged above, but double-guard
                    if (isInBufferPeriod()) {
                        long remainMs = getMillisUntilNextCycle();
                        String remain = formatTime((int) Math.max(0, remainMs / 1000));
                        try { player.sendMessage(MM.deserialize("<yellow>Buffer — next extraction in <white>" + remain + "</white>.</yellow>")); } catch (Exception ignored) {}
                    }
                    return false;
                }
            } else {
                // Legacy solo-new mode — create a fresh per-player session
                return startRaid(player, true);
            }
        }
        UUID uuid = player.getUniqueId();
        if (activeRaids.containsKey(uuid)) {
            RaidSession current = activeRaids.get(uuid);
            if (current == session) {
                BossBar existingBar = globalBossBars.get(key);
                if (existingBar != null) {
                    try { player.showBossBar(existingBar); } catch (Exception ignored) {}
                }
                return true;
            }
            return false; // already in a different raid
        }
        session.getMembers().add(uuid);
        activeRaids.put(uuid, session);
        BossBar bar = globalBossBars.get(key);
        if (bar != null) {
            try { player.showBossBar(bar); } catch (Exception ignored) {}
            // Ensure all existing members keep seeing the bar (fix for bossbar lost on relog)
            for (UUID mid : session.getMembers()) {
                if (mid.equals(uuid)) continue;
                Player member = Bukkit.getPlayer(mid);
                if (member != null && member.isOnline()) {
                    try { member.showBossBar(bar); } catch (Exception ignored) {}
                }
            }
        }
        String timeLeft = formatTime(session.getRemainingSeconds());
        try {
            player.sendMessage(MM.deserialize("<green>Joined ongoing raid! <gray>Time left: <white>" + timeLeft + "</white></gray>"));
            player.sendActionBar(MM.deserialize("<gray>Extract before <white>" + timeLeft + "</white> or lose everything!</gray>"));
        } catch (Exception ignored) {}
        plugin.getLogger().info("Player " + player.getName() + " joined global raid in " + world + " (remaining=" + timeLeft + ", members=" + session.getMembers().size() + ")");
        return true;
    }

    /**
     * Ends the global session for world, hiding bossbar from all members and online players.
     * Used by the 30m timeout handler and at the end of the 31m cycle.
     */
    public void endGlobalRaid(String world, RaidEndReason reason) {
        if (world == null || world.isBlank()) world = autoStartWorld;
        String key = normalizeWorldKey(world);
        RaidSession session = globalSessions.remove(key);
        if (session == null) return;
        BossBar bar = globalBossBars.remove(key);
        FoliaScheduler.Cancellable task = globalTimers.remove(key);
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        Set<UUID> membersSnapshot = new HashSet<>(session.getMembers());
        // Include synthetic leader in cleanup but don't message it
        for (UUID memberId : membersSnapshot) {
            activeRaids.remove(memberId);
            if (bar != null) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) {
                    try { p.hideBossBar(bar); } catch (Exception ignored) {}
                }
            }
            // Also clear per-leader bossBars mapping if it was mis-keyed
            bossBars.remove(memberId);
            FoliaScheduler.Cancellable t = timers.remove(memberId);
            if (t != null) try { t.cancel(); } catch (Exception ignored) {}
        }
        if (bar != null) {
            // Hide from anyone else who might have seen it (e.g., auto-joined via tickGlobal fallback)
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { p.hideBossBar(bar); } catch (Exception ignored) {}
            }
        }
        // Also clear synthetic leader mapping
        UUID leaderId = session.getLeader();
        activeRaids.remove(leaderId);
        BossBar leaderBar = bossBars.remove(leaderId);
        if (leaderBar != null) {
            for (UUID mid : membersSnapshot) {
                Player p = Bukkit.getPlayer(mid);
                if (p != null) try { p.hideBossBar(leaderBar); } catch (Exception ignored) {}
            }
        }
        FoliaScheduler.Cancellable leaderTask = timers.remove(leaderId);
        if (leaderTask != null) try { leaderTask.cancel(); } catch (Exception ignored) {}

        boolean lost = (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH);
        Component endedComp;
        if (reason == RaidEndReason.EXTRACTED) {
            String raw = plugin.getConfig().getString("messages.raid-extracted", "<green><bold>Extracted!</bold> <gray>Loot secured.</gray></green>");
            endedComp = MM.deserialize(raw);
        } else if (lost) {
            String raw = plugin.getConfig().getString("messages.raid-timeout-killed", "<dark_red><bold>The Glitch consumed you.</bold> <gray>Loot lost.</gray></dark_red>");
            endedComp = MM.deserialize(raw);
        } else {
            String endedRaw = plugin.getConfig().getString("messages.raid-ended", "<red>Raid ended <gray>(<reason>)</gray> — returning to hub...</red>");
            endedComp = MM.deserialize(endedRaw.replace("<reason>", reason.name().toLowerCase()));
        }
        for (UUID memberId : membersSnapshot) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                try { p.sendMessage(endedComp); } catch (Exception ignored) {}
            }
        }
        final RaidSession summarySession = session;
        final RaidEndReason summaryReason = reason;
        FoliaScheduler.runLaterGlobal(plugin, () -> sendSummary(summarySession, summaryReason), summaryDelayTicks);
        String worldLog = world;
        plugin.getLogger().info("Global raid ended for world " + worldLog + " reason=" + reason + " members=" + membersSnapshot.size());
    }

    public boolean isTimeoutVictim(UUID uuid) {
        return timeoutVictims.contains(uuid);
    }

    public void clearTimeoutVictim(UUID uuid) {
        timeoutVictims.remove(uuid);
    }

    public void recordDeath(UUID uuid) {
        lastDeathMillis.put(uuid, System.currentTimeMillis());
    }

    public boolean isRecentlyDead(UUID uuid, long withinMs) {
        Long t = lastDeathMillis.get(uuid);
        return t != null && (System.currentTimeMillis() - t) < withinMs;
    }

    /**
     * Starts a new raid for the given leader. If leader is in a party, the
     * entire party is pulled into the same raid session and party members
     * not yet in glitch_red are teleported to the leader.
     *
     * @return false if already in a raid
     */
    public boolean startRaid(Player leader) {
        return startRaid(leader, false);
    }

    public boolean startRaid(Player leader, boolean auto) {
        UUID uuid = leader.getUniqueId();
        if (isInRaid(uuid)) {
            return false;
        }
        // Global-remaining mode: RED world has ONE shared 30m extraction.
        // If a global is already running, join it with remaining time instead of creating a fresh solo timer.
        if (isGlobalRemainingMode()) {
            String playerWorld = leader.getWorld().getName();
            // Only apply global semantics to the extraction world (glitch_red)
            if (playerWorld.equalsIgnoreCase(autoStartWorld)) {
                if (isInBufferPeriod()) {
                    long remainMs = getMillisUntilNextCycle();
                    String remain = formatTime((int) Math.max(0, remainMs / 1000));
                    try {
                        leader.sendMessage(MM.deserialize("<yellow>Extraction is between cycles — <gray>next extraction in <white>" + remain + "</white>. Hold tight.</gray>"));
                        leader.sendActionBar(MM.deserialize("<gray>Next extraction: <white>" + remain + "</white></gray>"));
                    } catch (Exception ignored) {}
                    plugin.getLogger().info("startRaid suppressed for " + leader.getName() + " — in 1m buffer (next in " + remain + ")");
                    return false;
                }
                RaidSession global = findActiveGlobalSession(autoStartWorld);
                if (global != null) {
                    return addToGlobalSession(leader, autoStartWorld);
                }
                // No active global in RED — start a new global so ALL future joiners share this timer.
                // This will be suppressed during buffer above, so here we are outside buffer and safe to anchor.
                RaidSession newGlobal = startGlobalRaid(autoStartWorld, auto);
                if (newGlobal != null) {
                    newGlobal.getMembers().add(uuid);
                    activeRaids.put(uuid, newGlobal);
                    // Party pull: include whole party in the new global
                    Party p = partyManager.getParty(uuid);
                    if (p != null) {
                        for (UUID mid : p.getMembers()) {
                            if (mid.equals(uuid)) continue;
                            newGlobal.getMembers().add(mid);
                            activeRaids.put(mid, newGlobal);
                            Player mp = Bukkit.getPlayer(mid);
                            if (mp != null && mp.isOnline()) {
                                BossBar gBar = globalBossBars.get(normalizeWorldKey(autoStartWorld));
                                if (gBar != null) try { mp.showBossBar(gBar); } catch (Exception ignored) {}
                            }
                        }
                    }
                    BossBar gBar = globalBossBars.get(normalizeWorldKey(autoStartWorld));
                    if (gBar != null) {
                        try { leader.showBossBar(gBar); } catch (Exception ignored) {}
                        for (UUID mid : newGlobal.getMembers()) {
                            if (mid.equals(uuid)) continue;
                            Player mp = Bukkit.getPlayer(mid);
                            if (mp != null && mp.isOnline()) try { mp.showBossBar(gBar); } catch (Exception ignored) {}
                        }
                    }
                    String key = auto ? "messages.raid-auto-started" : "messages.raid-started";
                    String fallback = auto ? "<green><bold>Raid started!</bold> <gray>You entered the Glitch — 30:00 to extract!</gray>" : "<green><bold>Raid started!</bold> <gray>Good luck — the Glitch awaits.</gray>";
                    String startedRaw = plugin.getConfig().getString(key, fallback);
                    if (startedRaw == null) startedRaw = fallback;
                    String formatted = formatTime(durationSeconds);
                    for (UUID mid : newGlobal.getMembers()) {
                        Player pl = Bukkit.getPlayer(mid);
                        if (pl != null && pl.isOnline()) {
                            try {
                                pl.sendMessage(MM.deserialize(startedRaw));
                                pl.sendActionBar(MM.deserialize("<gray>Extract before <white>" + formatted + "</white> or lose everything!</gray>"));
                            } catch (Exception ignored) {}
                        }
                    }
                    plugin.getLogger().info("Raid start (global anchor) for " + leader.getName() + (auto ? " (auto)" : "") + " members=" + newGlobal.getMembers().size() + " in " + autoStartWorld);
                    return true;
                }
            }
            // If player not in RED, fall through to normal solo creation (e.g., /raid start in hub should still create solo if desired)
            // But we treat hub starts as global too if they will teleport to RED via party pull below.
        }
        long now = System.currentTimeMillis();
        long end = now + (durationSeconds * 1000L);
        Set<UUID> members = ConcurrentHashMap.newKeySet();

        // Include party members if leader has a party
        Party party = partyManager.getParty(uuid);
        if (party != null) {
            members.addAll(party.getMembers());
        } else {
            members.add(uuid);
            // Also ensure leader is in map even if solo party not created
        }

        RaidSession session = new RaidSession(uuid, members, now, end);

        // Map every member to the same session so they share timer but keep per-player loot
        for (UUID mid : members) {
            activeRaids.put(mid, session);
        }

        String timeLeftRaw = plugin.getConfig().getString("messages.raid-time-left", "<aqua>Time left: <white><time></white></aqua>");
        String formatted = formatTime(durationSeconds);
        Component initialName = MM.deserialize(timeLeftRaw.replace("<time>", formatted));
        BossBar bar = BossBar.bossBar(initialName, 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        bossBars.put(uuid, bar);
        leader.showBossBar(bar);
        // Also show to party members already online in same world
        for (UUID mid : members) {
            if (mid.equals(uuid)) continue;
            Player p = Bukkit.getPlayer(mid);
            if (p != null && p.isOnline()) {
                p.showBossBar(bar);
            }
        }

        String key = auto ? "messages.raid-auto-started" : "messages.raid-started";
        String fallback = auto ? "<green><bold>Raid started!</bold> <gray>You entered the Glitch — 30:00 to extract!</gray>" : "<green><bold>Raid started!</bold> <gray>Good luck — the Glitch awaits.</gray>";
        String startedRaw = plugin.getConfig().getString(key, fallback);
        if (startedRaw == null) startedRaw = fallback;
        for (UUID mid : members) {
            Player p = Bukkit.getPlayer(mid);
            if (p != null && p.isOnline()) {
                p.sendMessage(MM.deserialize(startedRaw));
                p.sendActionBar(MM.deserialize("<gray>Extract before <white>" + formatted + "</white> or lose everything!</gray>"));
            }
        }

        FoliaScheduler.Cancellable task = FoliaScheduler.runAtFixedRateGlobal(plugin, () -> tick(uuid), 20L, 20L);
        timers.put(uuid, task);

        // Teleport party members not yet in the raid world to the leader (Folia-safe)
        if (party != null) {
            for (UUID mid : members) {
                if (mid.equals(uuid)) continue;
                Player p = Bukkit.getPlayer(mid);
                if (p != null && p.isOnline() && !p.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                    try {
                        org.bukkit.Location dest = leader.getLocation();
                        FoliaScheduler.teleportEntity(p, plugin, dest);
                        p.sendMessage(MM.deserialize("<gray>Teleported to raid leader <white>" + leader.getName() + "</white> in <white>" + autoStartWorld + "</white>.</gray>"));
                        plugin.getLogger().info("Auto-teleported party member " + p.getName() + " to raid leader " + leader.getName() + " in " + autoStartWorld);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to teleport party member " + p.getName() + " to raid: " + e.getMessage());
                    }
                }
            }
        }

        plugin.getLogger().info("Raid started for " + leader.getName() + (auto ? " (auto)" : "") + " members=" + members.size() + " (duration=" + durationSeconds + "s)");
        return true;
    }

    /**
     * Ends the raid for the given player (leader or member).
     * Removes all members of that raid.
     * Global sessions: extraction is per-player/per-party (don't nuke entire global);
     * only timeout/manual global end collapses the whole 30m window.
     */
    public void endRaid(UUID playerUuid, RaidEndReason reason) {
        RaidSession session = activeRaids.get(playerUuid);
        if (session == null) {
            return;
        }
        // Global extraction/quit handling — don't disband the whole 31m cycle on single player action
        if (isGlobalSession(session)) {
            if (reason == RaidEndReason.EXTRACTED) {
                // Extract only caller (+ party members who share this global). Payout already handled
                // in handleExtraction (per-party). Here we just detach them from the shared timer.
                Set<UUID> toRemove = new HashSet<>();
                Party party = partyManager.getParty(playerUuid);
                if (party != null) {
                    for (UUID mid : party.getMembers()) {
                        if (session.getMembers().contains(mid)) toRemove.add(mid);
                    }
                }
                if (toRemove.isEmpty()) toRemove.add(playerUuid);
                // Ensure leader's own entry is covered if caller is not party leader but session leader is synthetic
                toRemove.add(playerUuid);
                // Filter to only those actually in this global
                toRemove.retainAll(new HashSet<>(session.getMembers()));
                if (toRemove.isEmpty()) toRemove.add(playerUuid);

                BossBar gBar = globalBossBars.get(normalizeWorldKey(autoStartWorld));
                if (gBar == null && !globalBossBars.isEmpty()) gBar = globalBossBars.values().iterator().next();
                for (UUID mid : new HashSet<>(toRemove)) {
                    session.getMembers().remove(mid);
                    activeRaids.remove(mid);
                    if (gBar != null) {
                        Player p = Bukkit.getPlayer(mid);
                        if (p != null) try { p.hideBossBar(gBar); } catch (Exception ignored) {}
                    }
                    bossBars.remove(mid);
                    FoliaScheduler.Cancellable t = timers.remove(mid);
                    if (t != null) try { t.cancel(); } catch (Exception ignored) {}
                }
                // Per-extractor summary (global stays alive)
                final Set<UUID> extracted = new HashSet<>(toRemove);
                final RaidSession parent = session;
                FoliaScheduler.runLaterGlobal(plugin, () -> {
                    String titleRaw = plugin.getConfig().getString("messages.raid-summary-title", "<gold><bold>Raid Summary</bold></gold>");
                    Component title = MM.deserialize(titleRaw);
                    int durationSec = parent.getElapsedSeconds();
                    String durationStr = formatTime(durationSec);
                    for (UUID mid : extracted) {
                        Player p = Bukkit.getPlayer(mid);
                        if (p == null) continue;
                        int myLoot = parent.getLootValue(mid);
                        int myDeaths = parent.getDeaths(mid);
                        int payout = (int) Math.round(myLoot * payoutMultiplier);
                        try {
                            p.sendMessage(Component.empty());
                            p.sendMessage(title);
                            p.sendMessage(MM.deserialize("<gray>Duration: <white>" + durationStr + "</white>"));
                            p.sendMessage(MM.deserialize("<gray>Reason: <white>EXTRACTED</white>"));
                            p.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>x" + payoutMultiplier + " = <green>" + payout + "</green>"));
                            p.sendMessage(MM.deserialize("<gray>Your deaths: <red>" + myDeaths + "</red>"));
                            p.sendMessage(Component.empty());
                            Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500));
                            p.showTitle(Title.title(title, MM.deserialize("<green>extracted • Loot " + payout + " • Deaths " + myDeaths + "</green>"), times));
                        } catch (Exception ignored) {}
                    }
                }, summaryDelayTicks);
                plugin.getLogger().info("Global extraction: " + playerUuid + " detached " + toRemove.size() + " player(s) from global (remaining=" + session.getMembers().size() + ")");
                return;
            }
            if (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH) {
                String worldKey = null;
                for (Map.Entry<String, RaidSession> e : globalSessions.entrySet()) {
                    if (e.getValue() == session) { worldKey = e.getKey(); break; }
                }
                if (worldKey == null) worldKey = normalizeWorldKey(autoStartWorld);
                endGlobalRaid(worldKey, reason);
                return;
            }
            // Manual/leader_quit/admin in global: just detach single player, don't collapse global
            if (reason == RaidEndReason.LEADER_QUIT || reason == RaidEndReason.MANUAL || reason == RaidEndReason.ADMIN) {
                session.getMembers().remove(playerUuid);
                activeRaids.remove(playerUuid);
                BossBar gBar = globalBossBars.get(normalizeWorldKey(autoStartWorld));
                if (gBar != null) {
                    Player p = Bukkit.getPlayer(playerUuid);
                    if (p != null) try { p.hideBossBar(gBar); } catch (Exception ignored) {}
                }
                bossBars.remove(playerUuid);
                FoliaScheduler.Cancellable t = timers.remove(playerUuid);
                if (t != null) try { t.cancel(); } catch (Exception ignored) {}
                plugin.getLogger().info("Globaldetach: " + playerUuid + " removed from global reason=" + reason + " remaining=" + session.getMembers().size());
                return;
            }
        }
        UUID leaderId = session.getLeader();
        Set<UUID> membersSnapshot = new HashSet<>(session.getMembers());

        for (UUID memberId : membersSnapshot) {
            activeRaids.remove(memberId);
            BossBar bar = bossBars.remove(memberId);
            if (bar != null) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
            FoliaScheduler.Cancellable task = timers.remove(memberId);
            if (task != null) {
                task.cancel();
            }
        }
        BossBar leaderBar = bossBars.remove(leaderId);
        if (leaderBar != null) {
            for (UUID memberId : membersSnapshot) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) {
                    p.hideBossBar(leaderBar);
                }
            }
            Player leaderPlayer = Bukkit.getPlayer(leaderId);
            if (leaderPlayer != null && !membersSnapshot.contains(leaderId)) {
                leaderPlayer.hideBossBar(leaderBar);
            }
        }
        FoliaScheduler.Cancellable leaderTask = timers.remove(leaderId);
        if (leaderTask != null) {
            leaderTask.cancel();
        }

        boolean lost = (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH);
        // Per-player payout handling happens in sendSummary; base for logging is total
        int totalLoot = session.getLootValue();

        Component endedComp;
        if (reason == RaidEndReason.EXTRACTED) {
            String raw = plugin.getConfig().getString("messages.raid-extracted", "<green><bold>Extracted!</bold> <gray>Loot secured.</gray></green>");
            endedComp = MM.deserialize(raw);
        } else if (lost) {
            String raw = plugin.getConfig().getString("messages.raid-timeout-killed", "<dark_red><bold>The Glitch consumed you.</bold> <gray>Loot lost.</gray></dark_red>");
            endedComp = MM.deserialize(raw);
        } else {
            String endedRaw = plugin.getConfig().getString("messages.raid-ended", "<red>Raid ended <gray>(<reason>)</gray> — returning to hub...</red>");
            endedComp = MM.deserialize(endedRaw.replace("<reason>", reason.name().toLowerCase()));
        }

        for (UUID memberId : membersSnapshot) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                p.sendMessage(endedComp);
            }
        }

        final RaidSession summarySession = session;
        final RaidEndReason summaryReason = reason;
        FoliaScheduler.runLaterGlobal(plugin, () -> sendSummary(summarySession, summaryReason), summaryDelayTicks);

        String leaderName = Bukkit.getOfflinePlayer(leaderId).getName();
        if (leaderName == null) leaderName = leaderId.toString();
        plugin.getLogger().info("Raid ended for " + leaderName + " reason=" + reason + " totalLoot=" + totalLoot + " members=" + membersSnapshot.size());
    }

    /**
     * Called from VelKoth KothWinEvent bridge — winner's inventory and all
     * party members' inventories are stashed before ending raid.
     */
    public void handleKothWin(Player winner, String kothName) {
        if (!isInRaid(winner.getUniqueId())) return;
        RaidSession session = activeRaids.get(winner.getUniqueId());
        if (session != null) {
            // Stash winner's party/raid members' inventories (GlitchStash handles merge)
            // For global mode we stash only the winner's party (not the entire 30m global population)
            Set<UUID> toStash;
            if (isGlobalSession(session)) {
                Party party = partyManager.getParty(winner.getUniqueId());
                if (party != null) {
                    toStash = new HashSet<>();
                    for (UUID mid : party.getMembers()) if (session.getMembers().contains(mid)) toStash.add(mid);
                    if (toStash.isEmpty()) toStash.add(winner.getUniqueId());
                } else {
                    toStash = Set.of(winner.getUniqueId());
                }
            } else {
                toStash = new HashSet<>(session.getMembers());
                toStash.add(winner.getUniqueId());
            }
            for (UUID mid : new HashSet<>(toStash)) {
                Player p = Bukkit.getPlayer(mid);
                if (p != null && p.isOnline() && p.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                    try {
                        com.theglitch.glitchstash.GlitchStash stashPlugin = com.theglitch.glitchstash.GlitchStash.getInstance();
                        if (stashPlugin != null) {
                            stashPlugin.getStashManager().saveStash(p.getUniqueId(), p.getName(),
                                    p.getInventory().getContents(), p.getInventory().getArmorContents(), p.getInventory().getItemInOffHand());
                            // Clear non-winner inventories here (winner will be cleared by GlitchStash listener)
                            if (!mid.equals(winner.getUniqueId())) {
                                p.getInventory().clear();
                                p.getInventory().setArmorContents(new ItemStack[4]);
                                p.getInventory().setItemInOffHand(null);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to stash party member " + mid + " on Koth win: " + e.getMessage());
                    }
                }
            }
        }
        plugin.getLogger().info("Handling KothWin extraction for " + winner.getName() + " (" + kothName + ") — party stashed");
        handleExtraction(winner);
    }

    /**
     * Called when a player successfully extracts (Koth win / hub teleport).
     * Ends their raid as EXTRACTED — loot is preserved (already stashed).
     * If party, the entire raid ends together and other members are pulled to hub.
     * Also pays per-player payout via Vault.
     */
    public void handleExtraction(Player player) {
        if (!isInRaid(player.getUniqueId())) return;
        RaidSession session = activeRaids.get(player.getUniqueId());
        Set<UUID> membersSnapshot;
        // Global mode: extraction is per-player/per-party, NOT the whole 30m window
        if (session != null && isGlobalSession(session)) {
            // For global, only the extracting player's party shares the extraction payout/pull
            Party party = partyManager.getParty(player.getUniqueId());
            if (party != null) {
                membersSnapshot = new HashSet<>();
                for (UUID mid : party.getMembers()) {
                    if (session.getMembers().contains(mid)) membersSnapshot.add(mid);
                }
                if (membersSnapshot.isEmpty()) membersSnapshot.add(player.getUniqueId());
            } else {
                membersSnapshot = new HashSet<>(Set.of(player.getUniqueId()));
            }
        } else {
            membersSnapshot = session != null ? new HashSet<>(session.getMembers()) : new HashSet<>(Set.of(player.getUniqueId()));
            if (session != null) membersSnapshot.add(player.getUniqueId());
            else membersSnapshot.add(player.getUniqueId());
        }

        // Payout per-player before ending (so loot still available)
        if (session != null) {
            for (UUID mid : membersSnapshot) {
                int myLoot = session.getLootValue(mid);
                if (myLoot <= 0) continue;
                int payout = (int) Math.round(myLoot * payoutMultiplier);
                if (payout <= 0) continue;
                Player p = Bukkit.getPlayer(mid);
                if (p != null && p.isOnline()) {
                    try {
                        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                        if (rsp != null) {
                            Economy econ = rsp.getProvider();
                            if (econ != null) {
                                EconomyResponse resp = econ.depositPlayer(p, payout);
                                if (resp.transactionSuccess()) {
                                    p.sendMessage(MM.deserialize("<green>+" + payout + " Shards payout for extraction! <gray>(loot " + myLoot + " ×" + payoutMultiplier + ")</gray></green>"));
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed payout for " + mid + ": " + e.getMessage());
                    }
                } else {
                    // Offline payout — deposit via OfflinePlayer
                    try {
                        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                        if (rsp != null && rsp.getProvider() != null) {
                            Economy econ = rsp.getProvider();
                            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(mid);
                            EconomyResponse resp = econ.depositPlayer(offline, payout);
                            if (!resp.transactionSuccess()) {
                                plugin.getLogger().warning("Offline payout failed for " + mid + ": " + resp.errorMessage);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Offline payout error for " + mid + ": " + e.getMessage());
                    }
                }
            }
        }

        endRaid(player.getUniqueId(), RaidEndReason.EXTRACTED);
        // Pull remaining party/raid members who are still in glitch_red to hub (shared extraction)
        for (UUID mid : membersSnapshot) {
            if (mid.equals(player.getUniqueId())) continue; // winner already teleported by GlitchStash
            Player other = Bukkit.getPlayer(mid);
            if (other != null && other.isOnline() && other.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                FoliaScheduler.runLaterGlobal(plugin, () -> {
                    Player p2 = Bukkit.getPlayer(mid);
                    if (p2 != null && p2.isOnline() && p2.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                        teleportToHub(p2);
                        p2.sendMessage(MM.deserialize("<green>Party extraction — pulled to hub with <white>" + player.getName() + "</white>.</green>"));
                    }
                }, 20L);
            }
        }
    }

    /** Adds a party member who accepted mid-raid to the ongoing session. */
    public void handlePartyMemberAddedToActiveRaid(Player newMember, RaidSession session) {
        UUID nid = newMember.getUniqueId();
        if (isInRaid(nid)) return;
        session.getMembers().add(nid);
        activeRaids.put(nid, session);
        // Prefer global BossBar if this is a global session (single shared timer)
        BossBar bar = null;
        if (isGlobalSession(session)) {
            for (Map.Entry<String, BossBar> e : globalBossBars.entrySet()) {
                if (session == globalSessions.get(e.getKey())) {
                    bar = e.getValue();
                    break;
                }
            }
            if (bar == null) bar = globalBossBars.get(normalizeWorldKey(autoStartWorld));
        }
        if (bar == null) bar = bossBars.get(session.getLeader());
        if (bar != null) {
            try { newMember.showBossBar(bar); } catch (Exception ignored) {}
        }
        newMember.sendMessage(MM.deserialize("<green>Joined ongoing raid! <gray>Time left: <white>" + formatTime(session.getRemainingSeconds()) + "</white></gray>"));
    }

    public BossBar getBossBarForSession(RaidSession session) {
        if (session == null) return null;
        if (isGlobalSession(session)) {
            for (Map.Entry<String, BossBar> e : globalBossBars.entrySet()) {
                if (e.getValue() != null && session == globalSessions.get(e.getKey())) return e.getValue();
            }
            BossBar g = globalBossBars.get(normalizeWorldKey(autoStartWorld));
            if (g != null) return g;
        }
        return bossBars.get(session.getLeader());
    }

    /**
     * Gets the global BossBar for world (used for joiners to see remaining time instantly).
     */
    public BossBar getGlobalBossBar(String world) {
        return globalBossBars.get(normalizeWorldKey(world));
    }

    /**
     * Handles a non-leader quit: remove single player from raid without ending entire raid.
     * If leader quits, this is not used — use endRaid with LEADER_QUIT instead.
     * Global sessions are never disbanded on quit — the 31m cycle owns the global lifecycle.
     */
    public void removeMember(UUID uuid) {
        RaidSession session = activeRaids.remove(uuid);
        if (session == null) {
            return;
        }
        session.getMembers().remove(uuid);
        // Hide bossbar — check both per-player and global
        BossBar bar = bossBars.remove(uuid);
        boolean wasGlobal = isGlobalSession(session);
        if (wasGlobal) {
            // For global, hide the global bar specifically
            for (Map.Entry<String, RaidSession> e : globalSessions.entrySet()) {
                if (e.getValue() == session) {
                    BossBar gBar = globalBossBars.get(e.getKey());
                    if (gBar != null) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) try { p.hideBossBar(gBar); } catch (Exception ignored) {}
                    }
                    break;
                }
            }
            // Also try default auto world bar
            if (bar == null) {
                BossBar gBar = globalBossBars.get(normalizeWorldKey(autoStartWorld));
                if (gBar != null) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) try { p.hideBossBar(gBar); } catch (Exception ignored) {}
                }
            }
        } else if (bar != null) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                try { p.hideBossBar(bar); } catch (Exception ignored) {}
            }
        } else if (wasGlobal) {
            // fallback global hide
            BossBar gBar = globalBossBars.get(normalizeWorldKey(autoStartWorld));
            if (gBar != null) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) try { p.hideBossBar(gBar); } catch (Exception ignored) {}
            }
        }
        FoliaScheduler.Cancellable task = timers.remove(uuid);
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        if (!wasGlobal && session.getMembers().isEmpty()) {
            UUID leaderId = session.getLeader();
            activeRaids.remove(leaderId);
            BossBar leaderBar = bossBars.remove(leaderId);
            if (leaderBar != null) {
                Player lp = Bukkit.getPlayer(leaderId);
                if (lp != null) try { lp.hideBossBar(leaderBar); } catch (Exception ignored) {}
            }
            FoliaScheduler.Cancellable leaderTask = timers.remove(leaderId);
            if (leaderTask != null) try { leaderTask.cancel(); } catch (Exception ignored) {}
        } else if (wasGlobal) {
            // Global empty is not disbanded — keep until timeout handles 1m buffer and endGlobalRaid()
            plugin.getLogger().info("Player " + uuid + " removed from GLOBAL raid. Remaining members: " + session.getMembers().size() + " (global persists until timeout)");
            return;
        }
        plugin.getLogger().info("Player " + uuid + " removed from raid (quit). Remaining members: " + session.getMembers().size());
    }

    public void addLoot(UUID uuid, int amount) {
        RaidSession session = activeRaids.get(uuid);
        if (session == null) return;
        session.addLoot(uuid, amount);
    }

    /**
     * Add loot value derived from ItemStacks' sell prices (GlitchShops) or fallback.
     * Used for container loot and other item-based rewards so the BossBar/status reflects real value.
     * Per-player — does not share across party.
     */
    public void addLootFromItems(Player player, Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        RaidSession session = activeRaids.get(player.getUniqueId());
        if (session == null) return;
        int value = 0;
        try {
            Plugin shopsPlugin = Bukkit.getPluginManager().getPlugin("GlitchShops");
            if (shopsPlugin != null && shopsPlugin.isEnabled()) {
                Object manager = null;
                try {
                    manager = shopsPlugin.getClass().getMethod("getShopManager").invoke(shopsPlugin);
                } catch (NoSuchMethodException e) {
                    Object inst = shopsPlugin.getClass().getMethod("getInstance").invoke(null);
                    if (inst != null) manager = inst.getClass().getMethod("getShopManager").invoke(inst);
                }
                if (manager != null) {
                    java.lang.reflect.Method sellMethod = manager.getClass().getMethod("sellPrice", ItemStack.class);
                    for (ItemStack item : items) {
                        if (item == null || item.getType().isAir()) continue;
                        Integer price = (Integer) sellMethod.invoke(manager, item);
                        if (price != null && price > 0) {
                            value += price * item.getAmount();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (value <= 0) {
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) continue;
                value += item.getAmount() * 5;
            }
            if (value <= 0) value = items.size() * 10;
        }
        if (value > 0) {
            session.addLoot(player.getUniqueId(), value);
            String lootRaw = plugin.getConfig().getString("messages.loot-added", "<gold>+<amount> loot value</gold>");
            try {
                player.sendActionBar(MM.deserialize(lootRaw.replace("<amount>", String.valueOf(value))));
            } catch (Exception ignored) {
            }
        }
    }

    public void incrementDeaths(UUID uuid) {
        RaidSession session = activeRaids.get(uuid);
        if (session == null) return;
        session.incrementDeaths(uuid);
    }

    /**
     * Tick handler for a specific raid (identified by leader UUID).
     * Updates bossbar and handles warnings / timeout kill.
     * Global raids are ticked via tickGlobal(key) — this path handles solo/party raids.
     * If the session is actually a global session (leader is synthetic global UUID),
     * we delegate to tickGlobal to keep the single shared timer authoritative.
     */
    public void tick(UUID leaderId) {
        RaidSession session = activeRaids.get(leaderId);
        if (session == null) {
            BossBar bar = bossBars.remove(leaderId);
            if (bar != null) {
                Player p = Bukkit.getPlayer(leaderId);
                if (p != null) p.hideBossBar(bar);
            }
            FoliaScheduler.Cancellable task = timers.remove(leaderId);
            if (task != null) task.cancel();
            return;
        }
        // If this session is the global extraction, use global tick (single timer for all)
        if (isGlobalSession(session)) {
            // Ensure global tick drives the bossbar; avoid double ticking per-member
            // Still need to handle case where global timer was lost — fallback to global tick
            String key = normalizeWorldKey(autoStartWorld);
            tickGlobal(key);
            return;
        }

        long now = System.currentTimeMillis();
        long remainingMs = session.getEndTime() - now;
        int remainingSeconds = (int) Math.max(0, remainingMs / 1000);
        float progress = durationSeconds > 0 ? (float) remainingSeconds / (float) durationSeconds : 0f;
        progress = Math.max(0f, Math.min(1f, progress));

        BossBar bar = bossBars.get(leaderId);
        if (bar != null) {
            String timeLeftRaw = plugin.getConfig().getString("messages.raid-time-left", "<aqua>Time left: <white><time></white></aqua>");
            String formatted = formatTime(remainingSeconds);
            Component name = MM.deserialize(timeLeftRaw.replace("<time>", formatted));
            bar.name(name);
            bar.progress(progress);
            if (remainingSeconds <= 60) {
                bar.color(BossBar.Color.RED);
            } else if (remainingSeconds <= 300) {
                bar.color(BossBar.Color.YELLOW);
            } else {
                bar.color(BossBar.Color.GREEN);
            }
            for (UUID memberId : session.getMembers()) {
                if (memberId.equals(leaderId)) continue;
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.showBossBar(bar);
                }
            }
        }

        if (remainingSeconds == 60 || remainingSeconds == 30 || remainingSeconds == 10
                || (remainingSeconds <= 5 && remainingSeconds > 0)) {
            sendTimeoutWarning(session, remainingSeconds);
        }

        if (remainingMs <= 0) {
            handleTimeout(leaderId);
        }
    }

    private void sendTimeoutWarning(RaidSession session, int remainingSeconds) {
        String key;
        String fallback;
        if (remainingSeconds == 60) {
            key = "messages.raid-warn-60";
            fallback = "<red><bold>WARNING:</bold> <gray>60 seconds left — extract now or the Glitch will consume you!</gray></red>";
        } else if (remainingSeconds == 30) {
            key = "messages.raid-warn-30";
            fallback = "<red><bold>30 seconds left — get to an extraction beacon!</bold></red>";
        } else if (remainingSeconds == 10) {
            key = "messages.raid-warn-10";
            fallback = "<red><bold>10 seconds!</bold> <gray>The Glitch closes — extract or die!</gray></red>";
        } else {
            key = null;
            fallback = "<red><bold>" + remainingSeconds + "</bold></red>";
        }
        Component msg;
        if (key != null) {
            String raw = plugin.getConfig().getString(key, fallback);
            try { msg = MM.deserialize(raw); } catch (Exception e) { msg = Component.text(remainingSeconds + "s left"); }
        } else {
            msg = MM.deserialize(fallback);
        }
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(800), Duration.ofMillis(200));
        Component title = MM.deserialize("<red><bold>" + remainingSeconds + "</bold></red>");
        for (UUID memberId : session.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(autoStartWorld)) continue;
            p.sendMessage(msg);
            if (remainingSeconds <= 10) {
                p.showTitle(Title.title(title, msg, times));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            } else {
                p.sendActionBar(msg);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
            }
        }
        if (!session.getMembers().contains(session.getLeader())) {
            Player lp = Bukkit.getPlayer(session.getLeader());
            if (lp != null && lp.isOnline() && lp.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                lp.sendMessage(msg);
            }
        }
    }

    private void handleTimeout(UUID leaderId) {
        RaidSession session = activeRaids.get(leaderId);
        if (session == null) return;
        // Global timeout is authoritative: kills EVERYONE in RED, not just session members
        if (isGlobalSession(session)) {
            String worldKey = null;
            for (Map.Entry<String, RaidSession> e : globalSessions.entrySet()) {
                if (e.getValue() == session) { worldKey = e.getKey(); break; }
            }
            if (worldKey == null) worldKey = normalizeWorldKey(autoStartWorld);
            handleGlobalTimeout(worldKey);
            return;
        }
        Set<UUID> membersSnapshot = new HashSet<>(session.getMembers());
        membersSnapshot.add(leaderId);
        for (UUID memberId : membersSnapshot) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null || !p.isOnline()) continue;
            // STRICT: kill only RED world — never hub/pve (spec: timeout kills RED only)
            String w = p.getWorld().getName();
            if (!w.equalsIgnoreCase(autoStartWorld)) continue;
            if (w.equalsIgnoreCase(hubWorld)) continue; // extra safety: never kill in hub
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                teleportToHub(p);
                continue;
            }
            final UUID victimId = memberId;
            timeoutVictims.add(victimId);
            FoliaScheduler.runLaterGlobal(plugin, () -> timeoutVictims.remove(victimId), 600L);
            String killedRaw = plugin.getConfig().getString("messages.raid-timeout-killed",
                    "<dark_red><bold>The Glitch consumed you.</bold> <gray>You failed to extract — raid loot lost. Stash is safe.</gray></dark_red>");
            try { p.sendMessage(MM.deserialize(killedRaw)); } catch (Exception ignored) {}
            Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500));
            try {
                p.showTitle(Title.title(MM.deserialize("<dark_red><bold>Time's up</bold></dark_red>"),
                        MM.deserialize("<red>The Glitch consumed you</red>"), times));
            } catch (Exception ignored) {}
            try { p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f); } catch (Exception ignored) {}
            session.incrementDeaths(victimId);
            try {
                p.setHealth(0.0);
            } catch (Exception e) {
                try { p.damage(1000.0); } catch (Exception ignored) {}
            }
            FoliaScheduler.runLaterGlobal(plugin, () -> {
                Player pp = Bukkit.getPlayer(victimId);
                if (pp != null && pp.isOnline() && pp.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                    teleportToHub(pp);
                }
            }, 60L);
        }
        endRaid(leaderId, RaidEndReason.TIMEOUT_DEATH);
    }

    // ---- Global tick / timeout (single shared 30m extraction + 1m scatter buffer) ----

    /**
     * Global tick for worldKey (lowercased). Updates shared BossBar with remaining time,
     * auto-adds late joiners physically in RED, sends warnings, and triggers timeout at 0.
     * Folia-safe — runs on GlobalRegionScheduler every second.
     */
    private void tickGlobal(String worldKey) {
        RaidSession session = globalSessions.get(worldKey);
        if (session == null) {
            BossBar bar = globalBossBars.remove(worldKey);
            if (bar != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try { p.hideBossBar(bar); } catch (Exception ignored) {}
                }
            }
            FoliaScheduler.Cancellable task = globalTimers.remove(worldKey);
            if (task != null) try { task.cancel(); } catch (Exception ignored) {}
            return;
        }
        long now = System.currentTimeMillis();
        long remainingMs = session.getEndTime() - now;
        int remainingSeconds = (int) Math.max(0, remainingMs / 1000);
        float progress = durationSeconds > 0 ? (float) remainingSeconds / (float) durationSeconds : 0f;
        progress = Math.max(0f, Math.min(1f, progress));

        BossBar bar = globalBossBars.get(worldKey);
        if (bar != null) {
            String timeLeftRaw = plugin.getConfig().getString("messages.raid-time-left", "<aqua>Time left: <white><time></white></aqua>");
            String formatted = formatTime(remainingSeconds);
            Component name = MM.deserialize(timeLeftRaw.replace("<time>", formatted));
            bar.name(name);
            bar.progress(progress);
            if (remainingSeconds <= 60) bar.color(BossBar.Color.RED);
            else if (remainingSeconds <= 300) bar.color(BossBar.Color.YELLOW);
            else bar.color(BossBar.Color.GREEN);

            // Ensure every global member sees the bar if they're still in RED
            for (UUID memberId : session.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline() && member.getWorld().getName().equalsIgnoreCase(worldKey)) {
                    try { member.showBossBar(bar); } catch (Exception ignored) {}
                }
            }
            // Auto-add late joiners who are physically in RED but not yet in the global map
            // (e.g., /mv tp, portal, or race between listener and tick). Keeps remaining time consistent.
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p == null || !p.isOnline()) continue;
                if (!p.getWorld().getName().equalsIgnoreCase(worldKey)) continue;
                if (isInRaid(p.getUniqueId())) continue;
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                // Avoid adding players who just died recently (prevent death loop)
                if (isRecentlyDead(p.getUniqueId(), 5000L)) continue;
                session.getMembers().add(p.getUniqueId());
                activeRaids.put(p.getUniqueId(), session);
                try { p.showBossBar(bar); } catch (Exception ignored) {}
                try { p.sendMessage(MM.deserialize("<green>Joined ongoing raid! <gray>Time left: <white>" + formatTime(remainingSeconds) + "</white></gray>")); } catch (Exception ignored) {}
                try { p.sendActionBar(MM.deserialize("<gray>Extract before <white>" + formatTime(remainingSeconds) + "</white> or lose everything!</gray>")); } catch (Exception ignored) {}
                plugin.getLogger().info("Auto-added " + p.getName() + " to global raid in " + worldKey + " via tickGlobal (remaining=" + formatTime(remainingSeconds) + ")");
            }
        }

        if (remainingSeconds == 60 || remainingSeconds == 30 || remainingSeconds == 10
                || (remainingSeconds <= 5 && remainingSeconds > 0)) {
            sendGlobalTimeoutWarning(session, remainingSeconds, worldKey);
        }

        if (remainingMs <= 0) {
            handleGlobalTimeout(worldKey);
        }
    }

    private void sendGlobalTimeoutWarning(RaidSession session, int remainingSeconds, String worldKey) {
        String key;
        String fallback;
        if (remainingSeconds == 60) {
            key = "messages.raid-warn-60";
            fallback = "<red><bold>WARNING:</bold> <gray>60 seconds left — extract now or the Glitch will consume you!</gray></red>";
        } else if (remainingSeconds == 30) {
            key = "messages.raid-warn-30";
            fallback = "<red><bold>30 seconds left — get to an extraction beacon!</bold></red>";
        } else if (remainingSeconds == 10) {
            key = "messages.raid-warn-10";
            fallback = "<red><bold>10 seconds!</bold> <gray>The Glitch closes — extract or die!</gray></red>";
        } else {
            key = null;
            fallback = "<red><bold>" + remainingSeconds + "</bold></red>";
        }
        Component msg;
        if (key != null) {
            String raw = plugin.getConfig().getString(key, fallback);
            try { msg = MM.deserialize(raw); } catch (Exception e) { msg = Component.text(remainingSeconds + "s left"); }
        } else {
            msg = MM.deserialize(fallback);
        }
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(800), Duration.ofMillis(200));
        Component title = MM.deserialize("<red><bold>" + remainingSeconds + "</bold></red>");
        // Send to all global members in RED
        for (UUID memberId : session.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(worldKey)) continue;
            try {
                p.sendMessage(msg);
                if (remainingSeconds <= 10) {
                    p.showTitle(Title.title(title, msg, times));
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
                } else {
                    p.sendActionBar(msg);
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
                }
            } catch (Exception ignored) {}
        }
        // Also warn any other player physically in RED but not yet mapped (should have been auto-added above, but be safe)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(worldKey)) continue;
            if (session.getMembers().contains(p.getUniqueId())) continue;
            try {
                p.sendMessage(msg);
                if (remainingSeconds <= 10) p.showTitle(Title.title(title, msg, times));
            } catch (Exception ignored) {}
        }
    }

    /**
     * Global timeout: at the end of the 30m extraction, EVERYONE in RED WORLD is killed
     * (not hub/pve), then loot scatters during the 1m buffer before next 31m cycle.
     * This is the authoritative timeout for the shared extraction window.
     * Folia-safe, null-safe, world-filtered. Public for AutoExtractScheduler reflection.
     */
    public void handleGlobalTimeout(String worldKey) {
        RaidSession session = globalSessions.get(worldKey);
        if (session == null) return;
        // Prevent duplicate handling if already ending
        // Collect victims: all session members + anyone physically in RED (spec: kill everyone in RED)
        Set<UUID> victims = new HashSet<>(session.getMembers());
        // Include synthetic leader? It's not a real player, so ignore health kill, but add for completeness
        victims.add(session.getLeader());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            // STRICT world filter: only RED
            if (!p.getWorld().getName().equalsIgnoreCase(worldKey)) continue;
            if (p.getWorld().getName().equalsIgnoreCase(hubWorld)) continue;
            victims.add(p.getUniqueId());
            if (!session.getMembers().contains(p.getUniqueId())) {
                session.getMembers().add(p.getUniqueId());
                activeRaids.put(p.getUniqueId(), session);
            }
        }
        plugin.getLogger().info("Global timeout for world " + worldKey + " — killing " + victims.size() + " victims in RED (hub/pve skipped), entering 1m scatter buffer");
        for (UUID memberId : new HashSet<>(victims)) {
            // Skip synthetic global leader UUID (not a real online player)
            if (memberId.equals(session.getLeader()) && Bukkit.getPlayer(memberId) == null) continue;
            Player p = Bukkit.getPlayer(memberId);
            if (p == null || !p.isOnline()) continue;
            // STRICT: only if still in RED at kill moment — skip if they escaped to hub/pve during iteration
            String w = p.getWorld().getName();
            if (!w.equalsIgnoreCase(worldKey)) continue;
            if (w.equalsIgnoreCase(hubWorld)) continue;
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                try { teleportToHub(p); } catch (Exception ignored) {}
                continue;
            }
            final UUID victimId = memberId;
            timeoutVictims.add(victimId);
            FoliaScheduler.runLaterGlobal(plugin, () -> timeoutVictims.remove(victimId), 600L);
            String killedRaw = plugin.getConfig().getString("messages.raid-timeout-killed",
                    "<dark_red><bold>The Glitch consumed you.</bold> <gray>You failed to extract — raid loot lost. Stash is safe.</gray></dark_red>");
            try { p.sendMessage(MM.deserialize(killedRaw)); } catch (Exception ignored) {}
            Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500));
            try {
                p.showTitle(Title.title(MM.deserialize("<dark_red><bold>Time's up</bold></dark_red>"),
                        MM.deserialize("<red>The Glitch consumed you</red>"), times));
            } catch (Exception ignored) {}
            try { p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f); } catch (Exception ignored) {}
            session.incrementDeaths(victimId);
            try {
                p.setHealth(0.0);
            } catch (Exception e) {
                try { p.damage(1000.0); } catch (Exception ignored) {}
            }
            FoliaScheduler.runLaterGlobal(plugin, () -> {
                Player pp = Bukkit.getPlayer(victimId);
                if (pp != null && pp.isOnline() && pp.getWorld().getName().equalsIgnoreCase(worldKey)) {
                    teleportToHub(pp);
                }
            }, 60L);
        }
        // End global session — this clears bossbar and timers, and sends timeout summaries
        endGlobalRaid(worldKey, RaidEndReason.TIMEOUT_DEATH);
        // Loot scatter during 1m buffer: scheduler owns the next 31m start, but we log/scatter here
        scatterLootForBuffer(session, worldKey);
    }

    /**
     * Scatter loot during the 1m buffer after global timeout. MVP: logs and optionally
     * drops placeholder items. The real scatter (if Mythic loot) can be delegated to
     * the scheduler or GlitchLoot. We keep this Folia-safe and non-destructive.
     */
    private void scatterLootForBuffer(RaidSession session, String worldKey) {
        try {
            World world = Bukkit.getWorld(worldKey);
            if (world == null) world = Bukkit.getWorld(autoStartWorld);
            if (world == null) return;
            for (UUID memberId : session.getMembers()) {
                int loot = session.getLootValue(memberId);
                if (loot <= 0) continue;
                plugin.getLogger().info("Scatter buffer: player " + memberId + " loot " + loot + " would scatter in " + worldKey + " during 1m buffer (MVP log only)");
                // Future: spawn item entities at death locations proportional to loot value
                // For now we avoid spawning to prevent duplicate drops and Folia region issues.
            }
            // Broadcast scatter start
            String scatterRaw = plugin.getConfig().getString("messages.raid-scatter-start",
                    "<gray>Loot from the consumed scatters across <white><world></white> — 60s to scavenge before next extraction!</gray>");
            Component msg = MM.deserialize(scatterRaw.replace("<world>", worldKey));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().getName().equalsIgnoreCase(hubWorld) || p.getWorld().getName().equalsIgnoreCase(worldKey)) {
                    try { p.sendMessage(msg); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed scatter buffer for " + worldKey + ": " + e.getMessage());
        }
    }

    public void teleportToHub(Player player) {
        try {
            World hub = Bukkit.getWorld(hubWorld);
            if (hub != null) {
                player.teleport(hub.getSpawnLocation());
                return;
            }
        } catch (Exception ignored) {}
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv tp " + player.getName() + " " + hubWorld);
        } catch (Exception ignored) {}
    }

    private void sendSummary(RaidSession session, RaidEndReason reason) {
        String titleRaw = plugin.getConfig().getString("messages.raid-summary-title", "<gold><bold>Raid Summary</bold></gold>");
        Component title = MM.deserialize(titleRaw);
        long durationMs = System.currentTimeMillis() - session.getStartTime();
        int durationSec = (int) Math.min(durationMs / 1000, durationSeconds);
        String durationStr = formatTime(durationSec);
        boolean lost = (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH);

        for (UUID memberId : session.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null) continue;
            int myLoot = session.getLootValue(memberId);
            int myDeaths = session.getDeaths(memberId);
            int payout = lost ? 0 : (int) Math.round(myLoot * payoutMultiplier);
            p.sendMessage(Component.empty());
            p.sendMessage(title);
            p.sendMessage(MM.deserialize("<gray>Duration: <white>" + durationStr + "</white>"));
            String reasonLabel = reason.name();
            if (reason == RaidEndReason.TIMEOUT_DEATH) reasonLabel = "TIMEOUT (consumed)";
            else if (reason == RaidEndReason.EXTRACTED) reasonLabel = "EXTRACTED";
            p.sendMessage(MM.deserialize("<gray>Reason: <white>" + reasonLabel + "</white>"));
            if (lost) {
                p.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>→ <red>LOST</red> <gray>(not extracted)</gray>"));
                p.sendMessage(MM.deserialize("<gray>Payout: <red>0</red> <gray>(stash safe)</gray>"));
            } else {
                p.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>x" + payoutMultiplier + " = <green>" + payout + "</green>"));
            }
            p.sendMessage(MM.deserialize("<gray>Your deaths: <red>" + myDeaths + "</red>"));
            if (myDeaths > 0) {
                p.sendMessage(MM.deserialize("<gray>Death recap: <red>" + myDeaths + " death(s) this raid</red>"));
            } else {
                p.sendMessage(MM.deserialize("<gray>Death recap: <green>Flawless — no deaths!</green>"));
            }
            // Party loot recap for context
            if (session.getMembers().size() > 1) {
                p.sendMessage(MM.deserialize("<dark_gray>Party loot total: <gray>" + session.getLootValue() + "</gray></dark_gray>"));
            }
            p.sendMessage(Component.empty());

            Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500));
            Component subtitle;
            if (lost) {
                subtitle = MM.deserialize("<red>consumed • Loot lost</red>");
            } else if (reason == RaidEndReason.EXTRACTED) {
                subtitle = MM.deserialize("<green>extracted • Loot " + payout + " • Deaths " + myDeaths + "</green>");
            } else {
                subtitle = MM.deserialize("<gray>" + reason.name().toLowerCase() + " • Loot " + payout + " • Deaths " + myDeaths + "</gray>");
            }
            p.showTitle(Title.title(title, subtitle, times));
        }
        if (!session.getMembers().contains(session.getLeader())) {
            Player lp = Bukkit.getPlayer(session.getLeader());
            if (lp != null) {
                int myLoot = session.getLootValue(session.getLeader());
                int myDeaths = session.getDeaths(session.getLeader());
                int payout = lost ? 0 : (int) Math.round(myLoot * payoutMultiplier);
                lp.sendMessage(Component.empty());
                lp.sendMessage(title);
                lp.sendMessage(MM.deserialize("<gray>Duration: <white>" + durationStr + "</white>"));
                lp.sendMessage(MM.deserialize("<gray>Reason: <white>" + reason.name() + "</white>"));
                if (lost) {
                    lp.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>→ <red>LOST</red></gray>"));
                } else {
                    lp.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>x" + payoutMultiplier + " = <green>" + payout + "</green>"));
                }
                lp.sendMessage(MM.deserialize("<gray>Your deaths: <red>" + myDeaths + "</red>"));
                lp.sendMessage(Component.empty());
            }
        }
    }

    public String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void shutdown() {
        for (Map.Entry<UUID, FoliaScheduler.Cancellable> entry : timers.entrySet()) {
            try {
                entry.getValue().cancel();
            } catch (Exception ignored) {
            }
        }
        timers.clear();
        for (Map.Entry<String, FoliaScheduler.Cancellable> entry : globalTimers.entrySet()) {
            try { entry.getValue().cancel(); } catch (Exception ignored) {}
        }
        globalTimers.clear();
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            BossBar bar = entry.getValue();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                try {
                    p.hideBossBar(bar);
                } catch (Exception ignored) {
                }
            }
            RaidSession s = activeRaids.get(entry.getKey());
            if (s != null) {
                for (UUID memberId : s.getMembers()) {
                    if (memberId.equals(entry.getKey())) continue;
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null) {
                        try {
                            member.hideBossBar(bar);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        bossBars.clear();
        for (Map.Entry<String, BossBar> entry : globalBossBars.entrySet()) {
            BossBar bar = entry.getValue();
            for (Player p : Bukkit.getOnlinePlayers()) {
                try { p.hideBossBar(bar); } catch (Exception ignored) {}
            }
        }
        globalBossBars.clear();
        globalSessions.clear();
        activeRaids.clear();
        timeoutVictims.clear();
        lastDeathMillis.clear();
    }
}
