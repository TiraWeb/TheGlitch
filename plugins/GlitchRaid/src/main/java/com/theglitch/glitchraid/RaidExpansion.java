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
        switch (id) {
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
        switch (id) {
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

    /**
     * Direct helper for non-PAPI contexts.
     */
    public String getPlaceholder(Player player, String identifier) {
        return onPlaceholderRequest(player, identifier);
    }
}
