package com.theglitch.glitchstash.extract;

import com.theglitch.glitchstash.GlitchStash;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion "glitchstash" — feeds the MythicHUD extraction card.
 * <ul>
 *   <li>%glitchstash_hud_show% — true while the player is in a red world</li>
 *   <li>%glitchstash_hud_icon% — a0..a7 (turn arrow), zone, closed, or none</li>
 *   <li>%glitchstash_hud_title%, %glitchstash_hud_line1%, %glitchstash_hud_line2% — MiniMessage text</li>
 * </ul>
 */
public final class StashExpansion extends PlaceholderExpansion {

    private final GlitchStash plugin;

    public StashExpansion(GlitchStash plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "glitchstash";
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
        if (player == null) return "";
        ExtractionHud hud = plugin.getExtractionHud();
        ExtractionHud.HudState state = hud == null ? null : hud.state(player.getUniqueId());
        return switch (identifier.toLowerCase(java.util.Locale.ROOT)) {
            case "hud_show" -> String.valueOf(state != null);
            case "hud_icon" -> state == null ? "none" : state.icon();
            case "hud_title" -> state == null ? "" : state.title();
            case "hud_line1" -> state == null ? "" : state.line1();
            case "hud_line2" -> state == null ? "" : state.line2();
            default -> null;
        };
    }
}
