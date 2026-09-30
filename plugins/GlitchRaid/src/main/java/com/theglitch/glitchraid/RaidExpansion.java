package com.theglitch.glitchraid;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion for GlitchRaid.
 * <p>
 * Provides placeholders:
 * <ul>
 *   <li>%glitchraid_in_raid% — true/false</li>
 *   <li>%glitchraid_time_left% — seconds remaining (or 0 if not in raid)</li>
 *   <li>%glitchraid_time_left_formatted% — mm:ss (or 00:00 if not in raid)</li>
 *   <li>%glitchraid_loot% — current loot value (or 0 if not in raid)</li>
 *   <li>%glitchraid_deaths% — death count this raid (or 0)</li>
 *   <li>%glitchraid_party_size% — party/raid size (or 0)</li>
 * </ul>
 * <p>
 * Per-member placeholders (%glitchraid_member_&lt;1-4&gt;_name/health/class%) used to
 * live here for the party HUD, resolved via a direct cross-player PlaceholderAPI call
 * to sidestep the ParseOther expansion + MythicHUD's async placeholder cache, which
 * never reliably resolved in testing (2026-09-22). That whole approach was replaced —
 * MythicHUD's party HUD template can only resolve "who is member N" through the
 * Parties plugin's own relational placeholders (%parties_list_rank_...%), so
 * PartiesBridge now mirrors GlitchRaid party membership into a real Parties party
 * instead (see PartiesBridge.java, partyhud-p.yml).
 */
public final class RaidExpansion extends PlaceholderExpansion {

    private final GlitchRaid plugin;
    private final RaidManager manager;

    public RaidExpansion(GlitchRaid plugin, RaidManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getIdentifier() {
        return "glitchraid";
    }

    @Override
    public String getAuthor() {
        return "TheGlitch";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String identifier) {
        if (offlinePlayer == null) {
            return "";
        }
        if (offlinePlayer instanceof Player player) {
            return onPlaceholderRequest(player, identifier);
        }
        // Offline player: limited placeholders (in_raid false, others 0)
        String id = identifier.toLowerCase(java.util.Locale.ROOT);
        String rankValue = rank(offlinePlayer.getUniqueId(), id);
        if (rankValue != null) return rankValue;
        switch (id) {
            case "player":
                return offlinePlayer.getName();
            case "in_raid":
                return String.valueOf(manager.isInRaid(offlinePlayer.getUniqueId()));
            case "time_left":
            case "loot":
            case "deaths":
            case "party_size":
                return "0";
            case "time_left_formatted":
                return "00:00";
            default:
                return null;
        }
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }
        String id = identifier.toLowerCase(java.util.Locale.ROOT);
        String rankValue = rank(player.getUniqueId(), id);
        if (rankValue != null) return rankValue;
        switch (id) {
            // Plain player name. MythicDungeons' command functions only expand
            // PlaceholderAPI, and PAPI's own "player" expansion isn't installed;
            // the dungeon clear rewards use %glitchraid_player%.
            case "player":
                return player.getName();
            case "in_raid":
                return String.valueOf(manager.isInRaid(player.getUniqueId()));
            case "time_left": {
                RaidSession session = manager.getSession(player.getUniqueId());
                if (session == null) return "0";
                int remaining = session.getRemainingSeconds();
                return String.valueOf(remaining);
            }
            case "time_left_formatted": {
                RaidSession session = manager.getSession(player.getUniqueId());
                if (session == null) return "00:00";
                int remaining = session.getRemainingSeconds();
                return manager.formatTime(remaining);
            }
            case "loot": {
                RaidSession session = manager.getSession(player.getUniqueId());
                if (session == null) return "0";
                return String.valueOf(session.getLootValue(player.getUniqueId()));
            }
            case "deaths": {
                RaidSession session = manager.getSession(player.getUniqueId());
                if (session == null) return "0";
                return String.valueOf(session.getDeaths(player.getUniqueId()));
            }
            case "party_size": {
                RaidSession session = manager.getSession(player.getUniqueId());
                if (session == null) {
                    Party party = manager.getPartyManager().getParty(player.getUniqueId());
                    return party != null ? String.valueOf(party.getSize()) : "0";
                }
                return String.valueOf(session.getMembers().size());
            }
            default:
                return null;
        }
    }

    /** Raider Rank placeholders: rank_icon, rank_name, rank_rr, rank_next_rr, rank_progress, rank_position. */
    private String rank(java.util.UUID id, String key) {
        if (!key.startsWith("rank_")) return null;
        GlitchRaid gr = GlitchRaid.getInstance();
        com.theglitch.glitchraid.rank.RankManager ranks = gr == null ? null : gr.getRankManager();
        if (ranks == null) return "";
        int rr = ranks.rr(id);
        com.theglitch.glitchraid.rank.RankTier tier = ranks.tierOf(rr);
        com.theglitch.glitchraid.rank.RankTier next = tier.next();
        switch (key) {
            case "rank_icon":
                return String.valueOf(tier.glyph());
            case "rank_name":
                return tier.displayName();
            case "rank_rr":
                return String.valueOf(rr);
            case "rank_next_rr":
                return next == null ? "0" : String.valueOf(ranks.min(next) - rr);
            case "rank_progress": {
                if (next == null) return "100";
                int from = ranks.min(tier), to = ranks.min(next);
                return String.valueOf(Math.max(0, rr - from) * 100 / Math.max(1, to - from));
            }
            case "rank_position":
                return String.valueOf(ranks.position(id));
            default:
                return null;
        }
    }
}
