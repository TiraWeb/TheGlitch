package com.theglitch.common;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;

import java.util.Optional;

/**
 * Overlap guard for the floating hub panels. Every panel tags its display and
 * interaction entities with a {@code <plugin>:panel} PDC key; placing a panel
 * whose footprint contains another plugin's tagged entities is refused.
 */
public final class PanelFootprint {

    private PanelFootprint() {
    }

    /**
     * @param anchor    where the panel would be placed (its base centre)
     * @param width     total panel width in blocks (spread sideways from the anchor)
     * @param height    total panel height in blocks (upwards from the anchor)
     * @param ownPlugin namespace of the placing plugin; its own entities are ignored
     * @return the plugin namespace of the first overlapping panel, if any
     */
    public static Optional<String> overlaps(Location anchor, double width, double height, String ownPlugin) {
        if (anchor.getWorld() == null) return Optional.empty();
        double half = width / 2.0 + 0.5;
        Location centre = anchor.clone().add(0, height / 2.0, 0);
        for (Entity e : anchor.getWorld().getNearbyEntities(centre, half, height / 2.0 + 0.5, half)) {
            if (!(e instanceof Display) && !(e instanceof Interaction)) continue;
            for (NamespacedKey key : e.getPersistentDataContainer().getKeys()) {
                if (key.getKey().equals("panel") && !key.getNamespace().equals(ownPlugin)) {
                    return Optional.of(key.getNamespace());
                }
            }
        }
        return Optional.empty();
    }
}
