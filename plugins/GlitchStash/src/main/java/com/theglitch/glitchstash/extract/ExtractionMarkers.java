package com.theglitch.glitchstash.extract;

import com.theglitch.glitchstash.FoliaScheduler;
import com.theglitch.glitchstash.GlitchStash;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Per-point visuals for active extraction points: a locator-bar waypoint
 * (via {@link WaypointBridge}), an invisible anchor armor stand, a floating
 * TextDisplay label and a shared END_ROD particle column task.
 */
public final class ExtractionMarkers {

    private static final String BASE_TAG = "glitch_extract";
    private static final String LABEL_TEXT = "\u27EA EXTRACTION \u27EB";
    private static final long PARTICLE_PERIOD_TICKS = 10L;
    private static final int PARTICLE_STACK = 3;

    private final GlitchStash plugin;
    private final WaypointBridge bridge;
    private final List<ActivePoint> active = new ArrayList<>();
    private final List<long[]> forcedChunks = new ArrayList<>();
    private FoliaScheduler.Cancellable particleTask;

    private record ActivePoint(ExtractionPoint point, ArmorStand marker, TextDisplay label) {}

    public ExtractionMarkers(GlitchStash plugin) {
        this.plugin = plugin;
        this.bridge = new WaypointBridge(plugin);
    }

    public boolean locatorWaypointsAvailable() {
        return bridge.locatorWaypointsAvailable();
    }

    /** Idempotent: clears any previous batch (and stale leftovers) first. */
    public void show(Collection<ExtractionPoint> points) {
        clear();
        if (points == null || points.isEmpty()) return;

        String color = plugin.getConfig().getString("auto-extract.dynamic.waypoint-color", "");
        boolean particles = plugin.getConfig().getBoolean("auto-extract.dynamic.marker-particles", true);

        for (ExtractionPoint point : points) {
            World world = Bukkit.getWorld(point.world());
            if (world == null) {
                plugin.getLogger().warning("Extraction point " + point.index() + ": world '"
                        + point.world() + "' not loaded — no markers.");
                continue;
            }
            ArmorStand marker = spawnMarker(world, point);
            TextDisplay label = spawnLabel(world, point);
            // Waypoints/labels only track while the chunk ticks — keep it loaded
            // for the cycle so distant points stay on the locator bar.
            int cx = point.x() >> 4;
            int cz = point.z() >> 4;
            try {
                if (!world.isChunkForceLoaded(cx, cz)) {
                    world.setChunkForceLoaded(cx, cz, true);
                    forcedChunks.add(new long[]{world.getUID().getMostSignificantBits(),
                            world.getUID().getLeastSignificantBits(), cx, cz});
                }
            } catch (Throwable t) {
                plugin.getLogger().fine("chunk force-load failed: " + t.getClass().getSimpleName());
            }
            if (marker != null) {
                bridge.register(marker, pointTag(point.index()), color);
                active.add(new ActivePoint(point, marker, label));
            } else if (label != null) {
                active.add(new ActivePoint(point, null, label));
            }
        }

        if (particles && !active.isEmpty()) {
            particleTask = FoliaScheduler.runAtFixedRateGlobal(plugin, this::spawnParticles,
                    PARTICLE_PERIOD_TICKS, PARTICLE_PERIOD_TICKS);
        }
    }

    /** Despawns everything; safe to call repeatedly or with nothing active. */
    public void clear() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        for (ActivePoint entry : active) {
            if (entry.marker() != null) {
                bridge.unregister(entry.marker());
                entry.marker().remove();
            }
            if (entry.label() != null) entry.label().remove();
        }
        active.clear();
        for (long[] fc : forcedChunks) {
            World world = findWorld(fc[0], fc[1]);
            if (world != null) {
                try {
                    world.setChunkForceLoaded((int) fc[2], (int) fc[3], false);
                } catch (Throwable ignored) {
                }
            }
        }
        forcedChunks.clear();
        // Backup sweep: restarts mid-cycle or kills can leak tagged entities.
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.getScoreboardTags().contains(BASE_TAG)) {
                    // Stale marker from a previous cycle — release its forced chunk too
                    // (force-loads persist across restarts in level data).
                    try {
                        world.setChunkForceLoaded(stand.getLocation().getBlockX() >> 4,
                                stand.getLocation().getBlockZ() >> 4, false);
                    } catch (Throwable ignored) {
                    }
                    stand.remove();
                }
            }
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getScoreboardTags().contains(BASE_TAG)) display.remove();
            }
        }
    }

    private ArmorStand spawnMarker(World world, ExtractionPoint point) {
        try {
            return world.spawn(new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5),
                    ArmorStand.class, stand -> {
                        stand.setMarker(true);
                        stand.setInvisible(true);
                        stand.setGravity(false);
                        stand.setInvulnerable(true);
                        stand.setPersistent(true);
                        stand.addScoreboardTag(BASE_TAG);
                        stand.addScoreboardTag(pointTag(point.index()));
                    });
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to spawn extraction marker " + point.index() + ": " + t.getMessage());
            return null;
        }
    }

    private TextDisplay spawnLabel(World world, ExtractionPoint point) {
        try {
            return world.spawn(new Location(world, point.x() + 0.5, point.y() + 2.5, point.z() + 0.5),
                    TextDisplay.class, label -> {
                        label.text(Component.text(LABEL_TEXT, NamedTextColor.AQUA));
                        label.setBillboard(Display.Billboard.CENTER);
                        label.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        label.setShadowed(false);
                        label.setPersistent(true);
                        label.addScoreboardTag(BASE_TAG);
                        label.setTransformation(new org.bukkit.util.Transformation(
                                new Vector3f(), new AxisAngle4f(), new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
                    });
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to spawn extraction label " + point.index() + ": " + t.getMessage());
            return null;
        }
    }

    private void spawnParticles() {
        // Cache World lookups per tick — multiple points share the same world.
        java.util.Map<String, World> worldCache = new java.util.HashMap<>(4);
        for (ActivePoint entry : active) {
            String worldName = entry.point().world();
            World world = worldCache.get(worldName);
            if (world == null && !worldCache.containsKey(worldName)) {
                world = Bukkit.getWorld(worldName);
                worldCache.put(worldName, world);
            }
            if (world == null || world.getPlayers().isEmpty()) continue;
            double x = entry.point().x() + 0.5;
            double z = entry.point().z() + 0.5;
            int y = entry.point().y();
            int r = entry.point().radiusBlocks();
            // Vertical column (core)
            for (int i = 0; i < PARTICLE_STACK; i++) {
                world.spawnParticle(Particle.END_ROD, x, y + 0.5 + i, z, 1, 0, 0, 0, 0);
            }
            // Horizontal ring at feet + beacon beam — makes it visible from distance and clearly spread
            try {
                // Ring at y+1
                for (int i = 0; i < 8; i++) {
                    double angle = 2 * Math.PI * i / 8;
                    double rx = x + Math.cos(angle) * (r * 0.6);
                    double rz = z + Math.sin(angle) * (r * 0.6);
                    world.spawnParticle(Particle.END_ROD, rx, y + 1, rz, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.END_ROD, rx, y + 0.2, rz, 1, 0, 0, 0, 0);
                }
                // Extra central flare
                world.spawnParticle(Particle.END_ROD, x, y + 1, z, 3, r * 0.3, 0.5, r * 0.3, 0.02);
            } catch (Exception ignored) {}
        }
    }

    private static String pointTag(int index) {
        return BASE_TAG + "_" + index;
    }

    private static World findWorld(long msb, long lsb) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getUID().getMostSignificantBits() == msb
                    && world.getUID().getLeastSignificantBits() == lsb) return world;
        }
        return null;
    }
}
