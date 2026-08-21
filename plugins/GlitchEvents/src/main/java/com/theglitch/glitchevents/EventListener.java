package com.theglitch.glitchevents;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Minimal listener — notifies joining admins about active events and next auto-event.
 */
public final class EventListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchEvents plugin;

    public EventListener(GlitchEvents plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var manager = plugin.getEventManager();
        if (manager == null || manager.getActiveTaskCount() == 0) {
            return;
        }
        if (!event.getPlayer().hasPermission("glitchevents.admin")) {
            return;
        }
        long seconds = Math.max(0L, (manager.getNextEventAtMillis() - System.currentTimeMillis()) / 1000L);
        String nextText = manager.getNextEventAtMillis() > 0L ? seconds / 60 + "m " + seconds % 60 + "s" : "not scheduled";
        event.getPlayer().sendMessage(MM.deserialize(
                "<gray>[GlitchEvents] <white>" + manager.getActiveTaskCount()
                        + "</white> active task(s), next auto-event: <white>" + nextText + "</white></gray>"));
    }
}
