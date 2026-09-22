package com.theglitch.glitchraid;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *   <li>%glitchraid_member_&lt;1-4&gt;_name% — that party slot's player name, or "" if
 *       the slot is empty. Slot 1 is always the party leader; slots 2-4 are the
 *       remaining members sorted alphabetically (stable ordering — {@link Party}
 *       itself stores members in a {@link java.util.Set}, which has no order).</li>
 *   <li>%glitchraid_member_&lt;1-4&gt;_health% / %glitchraid_member_&lt;1-4&gt;_max_health% —
 *       that slot's live rounded health/max health (0 if the slot is empty or that
 *       member is offline). Resolved natively here instead of chaining through the
 *       ParseOther PAPI expansion + MythicHUD's async placeholder cache for another
 *       player's own %player_health_rounded% — that path never reliably resolved in
 *       testing (2026-09-22) and its failure mode couldn't be pinned down even after
 *       decompiling both ParseOther and MythicHUD's own parser; reading the target
 *       Player object directly here is simpler and has no such indirection to fail.</li>
 *   <li>%glitchraid_member_&lt;1-4&gt;_class% — that slot's %glitchclasses_class% value,
 *       resolved with a direct {@link PlaceholderAPI#setPlaceholders} call against the
 *       target member (same reasoning as the health placeholders above — no ParseOther,
 *       no MythicHUD-side caching).</li>
 * </ul>
 */
public final class RaidExpansion extends PlaceholderExpansion {

    private static final Pattern MEMBER_NAME = Pattern.compile("member_([1-4])_name");
    private static final Pattern MEMBER_HEALTH = Pattern.compile("member_([1-4])_(max_)?health");
    private static final Pattern MEMBER_CLASS = Pattern.compile("member_([1-4])_class");

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
                if (MEMBER_NAME.matcher(id).matches()) return "";
                if (MEMBER_HEALTH.matcher(id).matches()) return "0";
                if (MEMBER_CLASS.matcher(id).matches()) return "";
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
            default: {
                Matcher nameMatch = MEMBER_NAME.matcher(id);
                if (nameMatch.matches()) {
                    int slot = Integer.parseInt(nameMatch.group(1));
                    UUID uuid = resolveSlotUuid(player.getUniqueId(), slot);
                    return uuid == null ? "" : nameOf(uuid);
                }
                Matcher healthMatch = MEMBER_HEALTH.matcher(id);
                if (healthMatch.matches()) {
                    int slot = Integer.parseInt(healthMatch.group(1));
                    boolean max = healthMatch.group(2) != null;
                    UUID uuid = resolveSlotUuid(player.getUniqueId(), slot);
                    if (uuid == null) return "0";
                    Player member = Bukkit.getPlayer(uuid);
                    if (member == null) return "0";
                    double value = max
                            ? member.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                            : member.getHealth();
                    return String.valueOf(Math.round(value));
                }
                Matcher classMatch = MEMBER_CLASS.matcher(id);
                if (classMatch.matches()) {
                    int slot = Integer.parseInt(classMatch.group(1));
                    UUID uuid = resolveSlotUuid(player.getUniqueId(), slot);
                    if (uuid == null) return "";
                    OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
                    return PlaceholderAPI.setPlaceholders(member, "%glitchclasses_class%");
                }
                return null;
            }
        }
    }

    /**
     * Slot 1 is always the party leader; slots 2-4 are the remaining members
     * sorted alphabetically by name, so the party HUD's 4 fixed slots don't
     * visually reshuffle every time someone joins/leaves. Returns null for an
     * empty slot.
     */
    private UUID resolveSlotUuid(UUID viewerUuid, int slot) {
        Party party = manager.getPartyManager().getParty(viewerUuid);
        if (party == null) return null;
        UUID leader = party.getLeader();
        List<UUID> ordered = new ArrayList<>();
        ordered.add(leader);
        List<UUID> rest = new ArrayList<>();
        for (UUID member : party.getMembers()) {
            if (!member.equals(leader)) rest.add(member);
        }
        rest.sort(java.util.Comparator.comparing(this::nameOf, String.CASE_INSENSITIVE_ORDER));
        ordered.addAll(rest);
        int index = slot - 1;
        return index < ordered.size() ? ordered.get(index) : null;
    }

    private String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "";
    }
}
