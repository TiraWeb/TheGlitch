package com.theglitch.glitchitems;

import com.theglitch.common.Worlds;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import su.nezushin.nminimap.api.events.AsyncEntityIconSelectEvent;
import su.nezushin.nminimap.api.events.AsyncMarkerRenderEvent;
import su.nezushin.nminimap.markers.impl.LocationMarker;

/**
 * NMinimap hooks (registered only when NMinimap is installed): loot crates that
 * are ready to open show on the Red Zone minimap as tier-coloured chests, and
 * the mob radar only shows real mobs (loot-crate furniture, holograms and armor
 * stands are entities too and used to appear as red dots).
 * Marker art: server/plugins/NMinimap/markers (scripts/gen-minimap-markers.py).
 */
public final class MinimapBridge implements Listener {

    private final GlitchItems plugin;

    public MinimapBridge(GlitchItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMarkers(AsyncMarkerRenderEvent event) {
        Player player = event.getPlayer().getPlayer();
        if (player == null || !Worlds.isGameWorld(player.getWorld().getName())) return;
        double radius = plugin.getConfig().getDouble("minimap.crate-radius", 64.0);
        for (ContainerManager.CrateView crate : plugin.getContainerManager().nearby(player.getLocation(), radius)) {
            if (!crate.ready()) continue;
            event.getMarkers().add(new LocationMarker("crate_" + crate.type(), crate.location()));
        }
        // Rogue Raiders (GlitchBots) are PLAYER-type NPCs, which NMinimap's mob radar always
        // skips — draw them ourselves as the purple "rogue" chevron, within the mob radar's range.
        double rogueRadius = plugin.getConfig().getDouble("minimap.rogue-radius", 48.0);
        double r2 = rogueRadius * rogueRadius;
        org.bukkit.Location here = player.getLocation();
        for (Player other : new java.util.ArrayList<>(player.getWorld().getPlayers())) {
            if (!com.theglitch.common.Bots.isBot(other)) continue;
            org.bukkit.Location at = other.getLocation();
            double dx = at.getX() - here.getX(), dz = at.getZ() - here.getZ();
            if (dx * dx + dz * dz > r2) continue;
            event.getMarkers().add(new LocationMarker("rogue", at));
        }
    }

    @EventHandler
    public void onRadarIcon(AsyncEntityIconSelectEvent event) {
        Entity entity = event.getEntity();
        if (com.theglitch.common.Bots.isBot(entity)) {
            event.setSelectedIcon("rogue");
            event.setAllowRotation(false);
            return;
        }
        if (!(entity instanceof Mob) || entity instanceof ArmorStand) {
            event.setCancelled(true);
            return;
        }
        event.setSelectedIcon("hostile");
        event.setAllowRotation(false);
    }
}
