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
    // Per-player loot/deaths — concurrent because tick + pickup can race
    private final java.util.concurrent.ConcurrentHashMap<UUID, Integer> lootByPlayer = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Integer> deathsByPlayer = new java.util.concurrent.ConcurrentHashMap<>();

    public RaidSession(UUID leader, Set<UUID> members, long startTime, long endTime) {
        this.leader = leader;
        this.members = members;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getLeader() {
        return leader;
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

    /** @deprecated use {@link #addLoot(UUID,int)} */
    @Deprecated
    public void addLoot(int amount) {
        // Fallback: credit to leader if no player specified (legacy)
        addLoot(leader, amount);
    }

    public void setLootValue(UUID playerId, int value) {
        if (playerId == null) return;
        lootByPlayer.put(playerId, Math.max(0, value));
    }

    @Deprecated
    public void setLootValue(int lootValue) {
        setLootValue(leader, lootValue);
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

    @Deprecated
    public void incrementDeaths() {
        incrementDeaths(leader);
    }

    public void setDeaths(UUID playerId, int deaths) {
        if (playerId == null) return;
        deathsByPlayer.put(playerId, Math.max(0, deaths));
    }

    @Deprecated
    public void setDeaths(int deaths) {
        setDeaths(leader, deaths);
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
