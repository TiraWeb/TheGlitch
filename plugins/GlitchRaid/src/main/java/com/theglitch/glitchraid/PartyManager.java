package com.theglitch.glitchraid;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raid party manager — invite/accept/leave/disband, size enforcement.
 * Parties persist until disband/leave; raid sessions are separate but party
 * members are auto-pulled into the same raid when one enters glitch_red.
 */
public final class PartyManager {

    private final GlitchRaid plugin;
    private volatile int maxPartySize;
    private final long inviteExpiryMs = 30_000L;

    private final Map<UUID, Party> parties = new ConcurrentHashMap<>(); // leader -> party
    private final Map<UUID, UUID> playerToLeader = new ConcurrentHashMap<>(); // player -> leader
    private volatile DungeonPartyBridge dungeonBridge;

    public PartyManager(GlitchRaid plugin) {
        this.plugin = plugin;
        this.maxPartySize = Math.max(1, plugin.getConfig().getInt("raid.party-max-size", 4));
    }

    public void reload() {
        this.maxPartySize = Math.max(1, plugin.getConfig().getInt("raid.party-max-size", 4));
    }

    /** Mirrors every membership change into MythicDungeons (one party for raids and dungeons). */
    public void setDungeonBridge(DungeonPartyBridge bridge) {
        this.dungeonBridge = bridge;
    }

    private void syncDungeons(Party party, Set<UUID> removed) {
        DungeonPartyBridge bridge = dungeonBridge;
        if (bridge != null) bridge.sync(party, removed);
    }

    /** Re-attaches the player's party in MythicDungeons (it drops one-player parties after a run). */
    public void refreshDungeonParty(UUID playerUuid) {
        Party party = getParty(playerUuid);
        syncDungeons(party, party == null ? Set.of(playerUuid) : Set.of());
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public Party getParty(UUID playerUuid) {
        UUID leader = playerToLeader.get(playerUuid);
        if (leader == null) return null;
        return parties.get(leader);
    }

    public Party getPartyAsLeader(UUID leaderUuid) {
        return parties.get(leaderUuid);
    }

    public boolean hasParty(UUID playerUuid) {
        return playerToLeader.containsKey(playerUuid);
    }

    public boolean isLeader(UUID playerUuid) {
        UUID leader = playerToLeader.get(playerUuid);
        return leader != null && leader.equals(playerUuid);
    }

    public Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    public Party createParty(Player leader) {
        UUID id = leader.getUniqueId();
        if (hasParty(id)) return getParty(id);
        Party party = new Party(id);
        parties.put(id, party);
        playerToLeader.put(id, id);
        plugin.getLogger().info("Raid party created: leader=" + leader.getName());
        syncDungeons(party, Set.of());
        return party;
    }

    public boolean invitePlayer(Player leader, Player target) {
        UUID lid = leader.getUniqueId();
        UUID tid = target.getUniqueId();
        if (!isLeader(lid) && !hasParty(lid)) {
            // Auto-create party if leader has none
            createParty(leader);
        }
        Party party = getParty(lid);
        if (party == null) return false;
        if (!party.isLeader(lid)) return false;
        if (hasParty(tid)) return false;
        if (party.getSize() >= maxPartySize) return false;
        party.setPendingInvite(tid, System.currentTimeMillis() + inviteExpiryMs);
        return true;
    }

    public boolean acceptInvite(Player player) {
        UUID pid = player.getUniqueId();
        // One party at a time — a second accept used to add the player to both.
        if (playerToLeader.containsKey(pid)) return false;
        for (Map.Entry<UUID, Party> entry : parties.entrySet()) {
            Party party = entry.getValue();
            if (party.isInviteValid(pid)) {
                if (party.getSize() >= maxPartySize) return false;
                party.addMember(pid);
                playerToLeader.put(pid, party.getLeader());
                party.clearInvite(pid);
                syncDungeons(party, Set.of());
                plugin.getLogger().info(player.getName() + " accepted raid party invite -> leader=" + Bukkit.getOfflinePlayer(party.getLeader()).getName());
                return true;
            }
        }
        return false;
    }

    public boolean declineInvite(Player player) {
        UUID pid = player.getUniqueId();
        for (Party party : parties.values()) {
            if (party.isInviteValid(pid)) {
                party.clearInvite(pid);
                return true;
            }
        }
        return false;
    }

    public boolean kickMember(Player leader, Player target) {
        UUID lid = leader.getUniqueId();
        Party party = getParty(lid);
        if (party == null || !party.isLeader(lid)) return false;
        UUID tid = target.getUniqueId();
        if (tid.equals(lid)) return false;
        if (!party.isMember(tid)) return false;
        party.removeMember(tid);
        playerToLeader.remove(tid);
        syncDungeons(party, Set.of(tid));
        return true;
    }

    public void leaveParty(UUID playerUuid) {
        Party party = getParty(playerUuid);
        if (party == null) return;
        boolean wasLeader = party.isLeader(playerUuid);
        party.removeMember(playerUuid);
        playerToLeader.remove(playerUuid);
        if (wasLeader) {
            // Leader leaves -> disband (or promote next member — we disband for simplicity)
            Set<UUID> former = Set.copyOf(party.rawMembers());
            for (UUID member : former) {
                playerToLeader.remove(member);
            }
            parties.remove(party.getLeader());
            plugin.getLogger().info("Raid party disbanded (leader left): " + playerUuid);
            java.util.Set<UUID> removed = new java.util.HashSet<>(former);
            removed.add(playerUuid);
            syncDungeons(party, removed);
        } else {
            syncDungeons(party, Set.of(playerUuid));
        }
    }

    /**
     * Returns the party members including leader, or empty set if no party.
     */
    public Set<UUID> getPartyMembers(UUID playerUuid) {
        Party party = getParty(playerUuid);
        if (party == null) return Set.of();
        return party.getMembers();
    }

    public Set<UUID> getPartyMembersIncludingLeader(Player player) {
        return getPartyMembers(player.getUniqueId());
    }
}
