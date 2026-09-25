package com.theglitch.glitchraid;

import java.util.Set;
import java.util.UUID;

/**
 * Represents an active raid session.
 * Stored per-player (each member UUID maps to the same session instance).
 * Loot and deaths are tracked <b>per-player</b> so party members don't share counters
 * (warden requirement: "they should have their own loot count tho, loot wont be shared").
 */
public final class RaidSession {

    private final UUID leader;
    private final Set<UUID> members;
    private final long startTime;
    private final long endTime;
    // Which red-world (e.g. glitch_red / glitch_red_eleria / glitch_red_horizons) this
    // session belongs to — lets callers avoid scanning globalSessions for identity match.
    private final String worldKey;
    // Per-player loot/deaths — concurrent because tick + pickup can race
    private final java.util.concurrent.ConcurrentHashMap<UUID, Integer> lootByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Integer> deathsByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
    // Kill bounty — the only part of "loot" paid out as shards on extraction. Item loot
    // is NOT paid: the items themselves are kept and sell at the Bazaar, so paying their
    // value too double-paid every raid (and made shop-bought gear a money printer).
    private final java.util.concurrent.ConcurrentHashMap<UUID, Integer> bountyByPlayer = new java.util.concurrent.ConcurrentHashMap<>();

    public RaidSession(UUID leader, Set<UUID> members, long startTime, long endTime, String worldKey) {
        this.leader = leader;
        this.members = members;
        this.startTime = startTime;
        this.endTime = endTime;
        this.worldKey = worldKey;
    }

    public UUID getLeader() {
        return leader;
    }

    /** The red-world this session is anchored to (normalized lowercase world name). */
    public String getWorldKey() {
        return worldKey;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    // ---- Per-player loot ----

    public int getLootValue(UUID playerId) {
        return lootByPlayer.getOrDefault(playerId, 0);
    }

    /** @deprecated use {@link #getLootValue(UUID)} — total across party */
    @Deprecated
    public int getLootValue() {
        int total = 0;
        for (int v : lootByPlayer.values()) total += v;
        return total;
    }

    public void addLoot(UUID playerId, int amount) {
        if (amount <= 0 || playerId == null) return;
        lootByPlayer.merge(playerId, amount, Integer::sum);
    }

    public int getBounty(UUID playerId) {
        return bountyByPlayer.getOrDefault(playerId, 0);
    }

    public void addBounty(UUID playerId, int amount) {
        if (amount <= 0 || playerId == null) return;
        bountyByPlayer.merge(playerId, amount, Integer::sum);
    }

    /** Called once a player's extraction is paid — a rejoin of the same global must start from zero. */
    public void resetLoot(UUID playerId) {
        if (playerId == null) return;
        lootByPlayer.remove(playerId);
        bountyByPlayer.remove(playerId);
    }

    // ---- Per-player deaths ----

    public int getDeaths(UUID playerId) {
        return deathsByPlayer.getOrDefault(playerId, 0);
    }

    @Deprecated
    public int getDeaths() {
        int total = 0;
        for (int v : deathsByPlayer.values()) total += v;
        return total;
    }

    public void incrementDeaths(UUID playerId) {
        if (playerId == null) return;
        deathsByPlayer.merge(playerId, 1, Integer::sum);
    }

    /**
     * Remaining seconds until endTime (clamped to 0).
     */
    public int getRemainingSeconds() {
        long remainingMs = endTime - System.currentTimeMillis();
        return (int) Math.max(0, remainingMs / 1000);
    }

    /**
     * Elapsed seconds since startTime.
     */
    public int getElapsedSeconds() {
        long elapsedMs = System.currentTimeMillis() - startTime;
        return (int) Math.max(0, elapsedMs / 1000);
    }
}
