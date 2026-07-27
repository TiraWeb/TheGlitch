package com.theglitch.glitchdungeons.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DungeonRun {
    public enum State {
        WAITING, ASSIGNING, PREP, ACTIVE, EXTRACTING, COMPLETED, FAILED
    }

    public enum FailReason {
        TIMEOUT, WIPE
    }

    private final int runId;
    private final Party party;
    private final DungeonSlot slot;
    private final int tier;
    private State state;
    private int currentWave;
    private int totalWaves;
    private int remainingTime;
    private int maxTime;
    private final List<UUID> alivePlayers;
    private final List<UUID> deadPlayers;
    private FailReason failReason;
    private long extractionStartTime;

    public DungeonRun(int runId, Party party, DungeonSlot slot, int tier, int maxTime, int totalWaves) {
        this.runId = runId;
        this.party = party;
        this.slot = slot;
        this.tier = tier;
        this.maxTime = maxTime;
        this.remainingTime = maxTime;
        this.totalWaves = totalWaves;
        this.currentWave = 0;
        this.state = State.WAITING;
        this.alivePlayers = new ArrayList<>(party.getMembers());
        this.deadPlayers = new ArrayList<>();
    }

    public int getRunId() { return runId; }
    public Party getParty() { return party; }
    public DungeonSlot getSlot() { return slot; }
    public int getTier() { return tier; }
    public State getState() { return state; }
    public int getCurrentWave() { return currentWave; }
    public int getTotalWaves() { return totalWaves; }
    public int getRemainingTime() { return remainingTime; }
    public int getMaxTime() { return maxTime; }
    public List<UUID> getAlivePlayers() { return new ArrayList<>(alivePlayers); }
    public List<UUID> getDeadPlayers() { return new ArrayList<>(deadPlayers); }
    public FailReason getFailReason() { return failReason; }
    public long getExtractionStartTime() { return extractionStartTime; }

    public void setState(State state) { this.state = state; }
    public void setCurrentWave(int wave) { this.currentWave = wave; }
    public void setRemainingTime(int seconds) { this.remainingTime = seconds; }
    public void setFailReason(FailReason reason) { this.failReason = reason; }

    public void setExtractionStartTime(long time) { this.extractionStartTime = time; }

    public void tickTimer() {
        if (remainingTime > 0) {
            remainingTime--;
        }
    }

    public boolean isTimedOut() {
        return remainingTime <= 0;
    }

    public void playerDied(UUID uuid) {
        alivePlayers.remove(uuid);
        if (!deadPlayers.contains(uuid)) {
            deadPlayers.add(uuid);
        }
    }

    public boolean isWiped() {
        return alivePlayers.isEmpty();
    }

    public boolean isAllWavesComplete() {
        return currentWave >= totalWaves;
    }
}
