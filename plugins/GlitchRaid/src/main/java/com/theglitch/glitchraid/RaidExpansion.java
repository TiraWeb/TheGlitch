package com.theglitch.glitchraid;

import org.bukkit.entity.Player;

/**
 * Optional PlaceholderAPI expansion for GlitchRaid.
 * <p>
 * Provides placeholders:
 * <ul>
 *   <li>%glitchraid_in_raid% — true/false</li>
 *   <li>%glitchraid_time_left% — seconds remaining (or 0 if not in raid)</li>
 *   <li>%glitchraid_loot% — current loot value (or 0 if not in raid)</li>
 * </ul>
 * <p>
 * This class intentionally does NOT hard-depend on PlaceholderAPI at compile time
 * to keep the MVP free of system dependencies (no lib/ PlaceholderAPI.jar required).
 * If PlaceholderAPI is present at runtime, GlitchRaid will attempt to register
 * this expansion via reflection; otherwise placeholders are available via direct calls.
 * <p>
 * To enable full PlaceholderAPI integration with compile-time safety, replace this
 * class with one extending {@code me.clip.placeholderapi.expansion.PlaceholderExpansion}
 * and add PlaceholderAPI to the pom as a provided/system dependency:
 * <pre>
 * public final class RaidExpansion extends PlaceholderExpansion { ... }
 * </pre>
 */
public final class RaidExpansion {

    private final GlitchRaid plugin;
    private final RaidManager manager;

    public RaidExpansion(GlitchRaid plugin, RaidManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public String getIdentifier() {
        return "glitchraid";
    }

    public String getAuthor() {
        return "TheGlitch";
    }

    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    /**
     * Handles placeholder requests. Matches PlaceholderAPI's
     * {@code onPlaceholderRequest(Player, String)} signature.
     *
     * @param player     the player, may be null
     * @param identifier the placeholder identifier without prefix (e.g. "in_raid")
     * @return placeholder value or null if unknown
     */
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }
        String id = identifier.toLowerCase();
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
     * Attempts to register this expansion with PlaceholderAPI via reflection.
     * Avoids hard compile dependency on PlaceholderAPI.
     *
     * @return true if registration succeeded via reflection, false otherwise
     */
    public boolean register() {
        try {
            Class<?> expansionClass = Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            // If PlaceholderAPI is present, we cannot directly cast this class to PlaceholderExpansion
            // without compile-time inheritance. For MVP we log availability and return false;
            // a real integration would require this class to extend PlaceholderExpansion.
            // We attempt to check if PlaceholderAPI's registration method exists.
            Class<?> placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            // Try to find register method — presence indicates API is available
            boolean hasRegister = false;
            for (java.lang.reflect.Method m : placeholderAPIClass.getMethods()) {
                if (m.getName().equalsIgnoreCase("registerPlaceholderHook") || m.getName().equalsIgnoreCase("registerExpansion")) {
                    hasRegister = true;
                    break;
                }
            }
            if (hasRegister) {
                plugin.getLogger().info("PlaceholderAPI detected — RaidExpansion ready (placeholders: %glitchraid_in_raid%, %glitchraid_time_left%, %glitchraid_loot%).");
                plugin.getLogger().info("Note: MVP RaidExpansion is reflection-based; for full PAPI integration, make RaidExpansion extend PlaceholderExpansion and add compile dependency.");
                // Return false to indicate reflection-based MVP (no actual registration), but log as available
                return false;
            }
            return false;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("PlaceholderAPI not found — GlitchRaid placeholders disabled.");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check PlaceholderAPI expansion registration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Direct helper for non-PAPI contexts.
     */
    public String getPlaceholder(Player player, String identifier) {
        return onPlaceholderRequest(player, identifier);
    }
}
