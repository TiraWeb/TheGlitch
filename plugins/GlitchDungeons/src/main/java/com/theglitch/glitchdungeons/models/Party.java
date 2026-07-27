package com.theglitch.glitchdungeons.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Party {
    public enum State { LFM, READY, IN_DUNGEON }

    private final UUID leaderUuid;
    private final List<UUID> members;
    private State state;
    private UUID pendingInvite;
    private long inviteExpiry;

    public Party(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
        this.members = new ArrayList<>();
        this.members.add(leaderUuid);
        this.state = State.LFM;
    }

    public UUID getLeaderUuid() { return leaderUuid; }
    public List<UUID> getMembers() { return new ArrayList<>(members); }
    public int getSize() { return members.size(); }
    public State getState() { return state; }
    public UUID getPendingInvite() { return pendingInvite; }

    public boolean isLeader(UUID uuid) {
        return leaderUuid.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public void addMember(UUID uuid) {
        if (!members.contains(uuid)) {
            members.add(uuid);
        }
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public void setState(State state) {
        this.state = state;
    }

    public void setPendingInvite(UUID uuid, long expiry) {
        this.pendingInvite = uuid;
        this.inviteExpiry = expiry;
    }

    public void clearInvite() {
        this.pendingInvite = null;
        this.inviteExpiry = 0;
    }

    public boolean isInviteValid(UUID uuid) {
        if (pendingInvite == null || !pendingInvite.equals(uuid)) return false;
        if (System.currentTimeMillis() > inviteExpiry) {
            clearInvite();
            return false;
        }
        return true;
    }
}
