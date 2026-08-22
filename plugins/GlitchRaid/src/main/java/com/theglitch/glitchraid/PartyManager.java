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

    public PartyManager(GlitchRaid plugin) {
        this.plugin = plugin;
        this.maxPartySize = Math.max(1, plugin.getConfig().getInt("raid.party-max-size", 4));
    }

    public void reload() {
        this.maxPartySize = Math.max(1, plugin.getConfig().getInt("raid.party-max-size", 4));
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
        for (Map.Entry<UUID, Party> entry : parties.entrySet()) {
            Party party = entry.getValue();
            if (party.isInviteValid(pid)) {
                if (party.getSize() >= maxPartySize) return false;
                party.addMember(pid);
                playerToLeader.put(pid, party.getLeader());
                party.clearInvite(pid);
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
            for (UUID member : Set.copyOf(party.rawMembers())) {
                playerToLeader.remove(member);
            }
            parties.remove(party.getLeader());
            plugin.getLogger().info("Raid party disbanded (leader left): " + playerUuid);
        } else if (party.getSize() <= 1 && parties.containsKey(party.getLeader())) {
            // Optional: keep solo party for leader; don't auto-disband small parties
        }
        // Clean empty
        if (party.getSize() == 0) {
            parties.remove(party.getLeader());
        }
    }

    public void disbandParty(UUID leaderUuid) {
        Party party = parties.remove(leaderUuid);
        if (party == null) return;
        for (UUID member : party.rawMembers()) {
            playerToLeader.remove(member);
        }
        plugin.getLogger().info("Raid party disbanded by leader: " + leaderUuid);
    }

    public void handleQuit(Player player) {
        // Keep party on quit (rejoin), just log — don't dissolve immediately
        // If you want auto-leave on quit, uncomment:
        // leaveParty(player.getUniqueId());
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
