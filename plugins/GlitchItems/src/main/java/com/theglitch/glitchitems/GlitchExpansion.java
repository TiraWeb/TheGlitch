package com.theglitch.glitchitems;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class GlitchExpansion extends PlaceholderExpansion {

    private final GlitchItems plugin;

    public GlitchExpansion(GlitchItems plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "glitchitems";
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
        ResidualGlitchManager glitch = plugin.getGlitchManager();
        switch (identifier.toLowerCase()) {
            case "stacks":
                return String.valueOf(glitch.getStacks(player));
            case "max_stacks":
                return String.valueOf(plugin.getConfig().getInt("residual-glitch.max-stacks", 8));
            case "payout":
                return String.valueOf((int) Math.round((glitch.getPayoutMultiplier(player) - 1.0) * 100));
            case "payout_multiplier":
                return String.format("%.1f", glitch.getPayoutMultiplier(player));
            case "dmg_taken":
                return String.valueOf(glitch.getStacks(player)
                        * plugin.getConfig().getInt("residual-glitch.damage-taken-per-stack", 5));
            default:
                return null;
        }
    }
}
