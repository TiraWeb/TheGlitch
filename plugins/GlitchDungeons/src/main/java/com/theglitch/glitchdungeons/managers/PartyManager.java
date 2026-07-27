package com.theglitch.glitchdungeons.managers;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import com.theglitch.glitchdungeons.models.Party;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyManager {
    private final GlitchDungeons plugin;
    private final Map<UUID, Party> parties;       // leader UUID -> party
    private final Map<UUID, UUID> playerToParty;  // player UUID -> leader UUID

    public PartyManager(GlitchDungeons plugin) {
        this.plugin = plugin;
        this.parties = new HashMap<>();
        this.playerToParty = new HashMap<>();
    }

    public Party getParty(UUID playerUuid) {
        UUID leaderUuid = playerToParty.get(playerUuid);
        if (leaderUuid == null) return null;
        return parties.get(leaderUuid);
    }

    public Party getPartyAsLeader(UUID leaderUuid) {
        return parties.get(leaderUuid);
    }

    public boolean hasParty(UUID playerUuid) {
        return playerToParty.containsKey(playerUuid);
    }

    public boolean isLeader(UUID playerUuid) {
        UUID leaderUuid = playerToParty.get(playerUuid);
        if (leaderUuid == null) return false;
        return leaderUuid.equals(playerUuid);
    }

    public Party createParty(Player leader) {
        if (hasParty(leader.getUniqueId())) return getParty(leader.getUniqueId());
        Party party = new Party(leader.getUniqueId());
        parties.put(leader.getUniqueId(), party);
        playerToParty.put(leader.getUniqueId(), leader.getUniqueId());
        return party;
    }

    public boolean invitePlayer(Player leader, Player target) {
        Party party = getParty(leader.getUniqueId());
        if (party == null) return false;
        if (!party.isLeader(leader.getUniqueId())) return false;
        if (hasParty(target.getUniqueId())) return false;
        if (party.getSize() >= plugin.getDungeonConfig().getMaxPartySize()) return false;
        party.setPendingInvite(target.getUniqueId(), System.currentTimeMillis() + 30000);
        return true;
    }

    public boolean acceptInvite(Player player) {
        for (Party party : parties.values()) {
            if (party.isInviteValid(player.getUniqueId())) {
                party.addMember(player.getUniqueId());
                playerToParty.put(player.getUniqueId(), party.getLeaderUuid());
                party.clearInvite();
                return true;
            }
        }
        return false;
    }

    public boolean kickMember(Player leader, Player target) {
        Party party = getParty(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;
        if (target.getUniqueId().equals(leader.getUniqueId())) return false;
        party.removeMember(target.getUniqueId());
        playerToParty.remove(target.getUniqueId());
        return true;
    }

    public void leaveParty(UUID playerUuid) {
        Party party = getParty(playerUuid);
        if (party == null) return;
        party.removeMember(playerUuid);
        playerToParty.remove(playerUuid);
        if (party.isLeader(playerUuid)) {
            // If in dungeon, fail it before dissolving
            DungeonRun run = plugin.getDungeonManager().getPlayerRun(playerUuid);
            if (run != null && (run.getState() == DungeonRun.State.ACTIVE
                    || run.getState() == DungeonRun.State.PREP
                    || run.getState() == DungeonRun.State.EXTRACTING)) {
                plugin.getDungeonManager().failDungeon(run, DungeonRun.FailReason.WIPE);
            }
            dissolveParty(party);
        }
    }

    public void dissolveParty(Party party) {
        for (UUID member : party.getMembers()) {
            playerToParty.remove(member);
        }
        parties.remove(party.getLeaderUuid());
    }

    public void disbandParty(UUID leaderUuid) {
        Party party = parties.remove(leaderUuid);
        if (party != null) {
            for (UUID member : party.getMembers()) {
                playerToParty.remove(member);
            }
        }
    }

    public void setInDungeon(UUID playerUuid, boolean inDungeon) {
        Party party = getParty(playerUuid);
        if (party != null) {
            party.setState(inDungeon ? Party.State.IN_DUNGEON : Party.State.LFM);
        }
    }

    public void cleanupOffline(Player player) {
        leaveParty(player.getUniqueId());
    }
}
