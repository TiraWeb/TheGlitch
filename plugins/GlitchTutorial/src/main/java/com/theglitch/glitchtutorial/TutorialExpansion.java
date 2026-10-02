package com.theglitch.glitchtutorial;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * %glitchtutorial_active% (true/false), %glitchtutorial_title% ("Tutorial · 3/8"),
 * %glitchtutorial_step% (current objective, MiniMessage) — for the MythicHUD tutorial card.
 */
final class TutorialExpansion extends PlaceholderExpansion {

    private final GlitchTutorial plugin;
    private final TutorialManager manager;

    TutorialExpansion(GlitchTutorial plugin, TutorialManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getIdentifier() {
        return "glitchtutorial";
    }

    @Override
    public String getAuthor() {
        return "TheGlitch";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String id) {
        Player p = offline == null ? null : offline.getPlayer();
        if (p == null) return "";
        return switch (id) {
            case "active" -> String.valueOf(manager.isActive(p));
            case "title" -> manager.title(p);
            case "step" -> manager.objective(p);
            default -> null;
        };
    }
}
