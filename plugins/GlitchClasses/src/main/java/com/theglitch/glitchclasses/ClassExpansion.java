package com.theglitch.glitchclasses;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion so the TAB scoreboard can show class data
 * (%glitchclasses_class% / %glitchclasses_level% / %glitchclasses_xp%).
 * Class data lives in GlitchClasses' own storage — not in LuckPerms meta.
 */
public final class ClassExpansion extends PlaceholderExpansion {

    private final GlitchClasses plugin;

    public ClassExpansion(GlitchClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "glitchclasses";
    }

    @Override
    public String getAuthor() {
        return "The Glitch";
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
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }
        ClassData data = plugin.getClassManager().getClassData(player.getUniqueId());
        boolean hasClass = data != null && !data.className().equals("none");
        switch (identifier.toLowerCase(java.util.Locale.ROOT)) {
            case "class":
                return hasClass ? capitalize(data.className()) : "None";
            case "level":
                return hasClass ? String.valueOf(data.level()) : "0";
            case "xp":
                return hasClass ? String.valueOf(data.xp()) : "0";
            case "xp_needed": {
                if (!hasClass) return "0";
                int next = data.level() + 1;
                return String.valueOf(plugin.getClassManager().getXpForLevel(next));
            }
            default:
                return null;
        }
    }

    private String capitalize(String value) {
        return value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
