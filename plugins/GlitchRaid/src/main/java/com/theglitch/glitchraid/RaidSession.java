package com.theglitch.glitchraid;

import java.util.Set;
import java.util.UUID;

/**
 * Represents an active raid session.
 * Stored per-player (each member UUID maps to the same session instance).
 */
public final class RaidSession {

    private final UUID leader;
    private final Set<UUID> members;
    private final long startTime;
    private final long endTime;
    private int lootValue;
    private int deaths;

    public RaidSession(UUID leader, Set<UUID> members, long startTime, long endTime) {
        this.leader = leader;
        this.members = members;
        this.startTime = startTime;
        this.endTime = endTime;
        this.lootValue = 0;
        this.deaths = 0;
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

    public int getLootValue() {
        return lootValue;
    }

    public int getDeaths() {
        return deaths;
    }

    public void addLoot(int amount) {
        if (amount <= 0) return;
        this.lootValue += amount;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public void setLootValue(int lootValue) {
        this.lootValue = Math.max(0, lootValue);
    }

    public void setDeaths(int deaths) {
        this.deaths = Math.max(0, deaths);
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
