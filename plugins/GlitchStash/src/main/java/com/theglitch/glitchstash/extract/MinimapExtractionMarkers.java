package com.theglitch.glitchstash.extract;

import com.theglitch.glitchstash.GlitchStash;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import su.nezushin.nminimap.api.events.AsyncMarkerRenderEvent;
import su.nezushin.nminimap.markers.impl.LocationMarker;

import java.util.Map;

/**
 * Open extraction points on the NMinimap minimap (registered only when NMinimap
 * is installed). Points outside the map's range stick to the round map's edge,
 * so the marker doubles as a direction hint.
 */
public final class MinimapExtractionMarkers implements Listener {

    private final Map<String, DynamicExtractionManager> managers;

    public MinimapExtractionMarkers(GlitchStash plugin, Map<String, DynamicExtractionManager> managers) {
        this.managers = managers;
    }

    @EventHandler
    public void onMarkers(AsyncMarkerRenderEvent event) {
        Player player = event.getPlayer().getPlayer();
        if (player == null) return;
        DynamicExtractionManager manager = managers.get(player.getWorld().getName().toLowerCase(java.util.Locale.ROOT));
        if (manager == null) return;
        for (ExtractionPoint point : manager.getCurrentPoints()) {
            event.getMarkers().add(new LocationMarker("extract",
                    new Location(player.getWorld(), point.x() + 0.5, point.y(), point.z() + 0.5)));
        }
    }
}
