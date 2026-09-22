package com.theglitch.glitchraid;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.PartyPlayer;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One-way mirror: GlitchRaid party -> a same-membership Parties-plugin party.
 * <p>
 * MythicHUD's party HUD (Volya's Party HUD template) can only resolve "who is
 * member 1/2/3/4" through the Parties plugin's own relational placeholders
 * (%parties_members_N_...%) — it has no way to look up an arbitrary custom
 * party system. GlitchRaid stays the actual source of truth for party
 * membership/gameplay; this class just keeps a shadow Parties party in sync
 * so the HUD has something real to read. Best-effort: every call is wrapped,
 * a missing/disabled Parties plugin just means the mirror is skipped and
 * GlitchRaid parties keep working exactly as before.
 */
final class PartiesBridge {

    private final GlitchRaid plugin;

    PartiesBridge(GlitchRaid plugin) {
        this.plugin = plugin;
    }

    private boolean available() {
        var p = Bukkit.getPluginManager().getPlugin("Parties");
        return p != null && p.isEnabled();
    }

    /** Re-syncs a GlitchRaid party's membership into a matching Parties party, creating one if needed. */
    void sync(Party party) {
        if (party == null || !available()) return;
        try {
            PartiesAPI api = Parties.getApi();
            UUID leaderId = party.getLeader();
            PartyPlayer leaderPP = api.getPartyPlayer(leaderId);
            if (leaderPP == null) return;

            com.alessiodp.parties.api.interfaces.Party mirror = api.getPartyOfPlayer(leaderId);
            if (mirror == null) {
                String name = "glitchraid-" + leaderId.toString().substring(0, 8);
                if (!api.createParty(name, leaderPP)) return;
                mirror = api.getPartyOfPlayer(leaderId);
                if (mirror == null) return;
            }

            Set<UUID> want = party.getMembers();
            Set<UUID> have = new HashSet<>(mirror.getMembers());

            for (UUID uuid : want) {
                if (!have.contains(uuid)) {
                    PartyPlayer pp = api.getPartyPlayer(uuid);
                    if (pp != null) mirror.addMember(pp);
                }
            }
            for (UUID uuid : have) {
                if (!want.contains(uuid)) {
                    PartyPlayer pp = api.getPartyPlayer(uuid);
                    if (pp != null) mirror.removeMember(pp);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("PartiesBridge sync failed: " + e.getMessage());
        }
    }

    /** Removes the mirrored Parties party entirely (GlitchRaid party fully disbanded). */
    void disband(UUID leaderId) {
        if (leaderId == null || !available()) return;
        try {
            PartiesAPI api = Parties.getApi();
            com.alessiodp.parties.api.interfaces.Party mirror = api.getPartyOfPlayer(leaderId);
            if (mirror != null) mirror.delete();
        } catch (Exception e) {
            plugin.getLogger().warning("PartiesBridge disband failed: " + e.getMessage());
        }
    }
}
