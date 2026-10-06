package com.theglitch.glitchraid;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hub's walk-in Red Zone portal opens the same zone picker as the Red Zone Gate NPC
 * ({@link com.theglitch.glitchraid.gui.RedZoneSelectGUI}), so players choose the world.
 * <p>
 * Trigger is movement: stepping <em>into</em> the marked region opens the picker once (standing
 * in it doesn't re-open it; walk out and back in). This doesn't depend on vanilla portal
 * mechanics — END_PORTAL blocks never fire a teleport event when the box has no End dimension —
 * and any vanilla END_PORTAL teleport in the hub is cancelled. Picking a zone teleports through
 * the picker, which auto-starts/joins the raid via {@link RaidListener#onWorldChange}.
 * </p>
 */
public final class RedPortalListener implements Listener {

    private final RaidManager manager;
    private final RedPortalManager portals;
    private final com.theglitch.glitchraid.gui.RedZoneSelectGUI picker;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public RedPortalListener(RaidManager manager, RedPortalManager portals, com.theglitch.glitchraid.gui.RedZoneSelectGUI picker) {
        this.manager = manager;
        this.portals = portals;
        this.picker = picker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;
        if (!event.getPlayer().getWorld().getName().equalsIgnoreCase(manager.getHubWorld())) return;
        if (!portals.isEnabled()) return;
        event.setCancelled(true); // never the vanilla End teleport from the hub
        // With a marked region the walk-in trigger below opens the picker; without one, any hub
        // end portal does (cooldown-gated, vanilla fires this repeatedly while standing in it).
        if (!portals.hasRegion()) openPicker(event.getPlayer());
    }

    /** Stepping into the marked region opens the zone picker. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!portals.isEnabled() || !portals.hasRegion()) return;
        if (event.getTo() == null) return;
        // Skip look-only packets — block change only.
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!portals.contains(event.getTo()) || portals.contains(event.getFrom())) return;
        openPicker(event.getPlayer());
    }

    private void openPicker(Player player) {
        // Scatter buffer: the Red Zone is closed — the buffer check explains why.
        if (manager.denyRedEntryDuringBuffer(player)) return;
        long now = System.currentTimeMillis();
        long last = cooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < portals.cooldownSeconds() * 1000L) return;
        cooldown.put(player.getUniqueId(), now);
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 1.4f);
        player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0.2);
        picker.open(player);
    }
}
