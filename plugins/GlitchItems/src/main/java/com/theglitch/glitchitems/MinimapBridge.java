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
    /**
     * Rogue Raider (GlitchBots) positions per world, refreshed on the main thread every
     * half second. The marker event is async, and Citizens NPC players aren't in
     * world.getPlayers(), so the render path reads this snapshot instead.
     */
    private final java.util.Map<String, java.util.List<org.bukkit.Location>> rogues = new java.util.concurrent.ConcurrentHashMap<>();

    public MinimapBridge(GlitchItems plugin) {
        this.plugin = plugin;
    }

    void startRogueSnapshots() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                if (!Worlds.isGameWorld(w.getName())) continue;
                java.util.List<org.bukkit.Location> found = new java.util.ArrayList<>();
                for (Player p : w.getEntitiesByClass(Player.class)) {
                    if (com.theglitch.common.Bots.isBot(p)) found.add(p.getLocation());
                }
                if (found.isEmpty()) rogues.remove(w.getName());
                else rogues.put(w.getName(), java.util.List.copyOf(found));
            }
        }, 40L, 10L);
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
        for (org.bukkit.Location at : rogues.getOrDefault(player.getWorld().getName(), java.util.List.of())) {
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
