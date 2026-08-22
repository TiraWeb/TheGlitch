package com.theglitch.glitchraid;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple raid party — leader + members, pending invites with expiry.
 * Mirrors GlitchDungeons Party shape but lightweight for GlitchRaid.
 */
public final class Party {

    private final UUID leader;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> pendingInvites = new ConcurrentHashMap<>();

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getLeader() {
        return leader;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    /** Mutable view for internal use (RaidManager). */
    Set<UUID> rawMembers() {
        return members;
    }

    public int getSize() {
        return members.size();
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public void addMember(UUID uuid) {
        if (uuid != null) members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        pendingInvites.remove(uuid);
    }

    public void setPendingInvite(UUID target, long expiresAt) {
        pendingInvites.put(target, expiresAt);
    }

    public boolean isInviteValid(UUID target) {
        Long exp = pendingInvites.get(target);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            pendingInvites.remove(target);
            return false;
        }
        return true;
    }

    public void clearInvite(UUID target) {
        pendingInvites.remove(target);
    }

    public void clearAllInvites() {
        pendingInvites.clear();
    }
}
