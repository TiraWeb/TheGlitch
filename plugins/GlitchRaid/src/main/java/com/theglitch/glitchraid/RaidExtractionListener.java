package com.theglitch.glitchraid;

import dev.velmax.velkoth.api.event.KothWinEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * VelKoth extraction bridge — when a Koth is won in glitch_red, mark raid as extracted.
 * This is the authoritative extraction signal (GlitchStash also listens here).
 * Only registered when VelKoth is present (see GlitchRaid.onEnable).
 */
public final class RaidExtractionListener implements Listener {

    private final GlitchRaid plugin;
    private final RaidManager manager;

    public RaidExtractionListener(GlitchRaid plugin, RaidManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKothWin(KothWinEvent event) {
        Player winner = event.getWinner();
        if (winner == null) return;
        if (!manager.isInRaid(winner.getUniqueId())) return;
        // Extract koth name reflectively to stay compatible across VelKoth versions
        String kothName = "unknown";
        try {
            Object koth = event.getClass().getMethod("getKoth").invoke(event);
            if (koth != null) {
                try {
                    Object n = koth.getClass().getMethod("getName").invoke(koth);
                    if (n != null) kothName = n.toString();
                } catch (NoSuchMethodException ignored) {
                    kothName = koth.toString();
                }
            }
        } catch (Exception ignored) {
            // Try alternative method names
            try {
                Object n = event.getClass().getMethod("getKothName").invoke(event);
                if (n != null) kothName = n.toString();
            } catch (Exception ignored2) {}
        }
        plugin.getLogger().info("KothWinEvent for " + winner.getName() + " (" + kothName + ") — treating as raid extraction");
        manager.handleKothWin(winner, kothName);
    }
}
