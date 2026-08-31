package com.theglitch.glitchstash.extract;

import com.theglitch.glitchstash.FoliaScheduler;
import com.theglitch.glitchstash.GlitchStash;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

/**
 * Locator-bar integration for extraction markers.
 * <p>
 * The Paper 1.21.4 compile API has no waypoint surface, so this talks to
 * Purpur 26.x NMS reflectively (Mojang-mapped runtime classes). Verified
 * mechanism on 26.2: every LivingEntity is a WaypointTransmitter;
 * ServerWaypointManager tracks transmitters whose WAYPOINT_TRANSMIT_RANGE
 * attribute is > 0 (LivingEntity.onAttributeUpdated tracks/untracks on change,
 * but Marker armor stands never tick, so the attribute dirty-flush never runs —
 * trackWaypoint is therefore invoked explicitly). Entity removal untracks via
 * ServerLevel$EntityCallbacks.onDestroyed, so despawn is self-cleaning.
 * <p>
 * Color uses the vanilla console command (proven live on 26.2):
 * {@code waypoint modify @e[tag=<tag>,limit=1,type=armor_stand] color <named|hex RRGGBB|reset>}.
 * {@code /waypoint create} does not exist on 26.2 — creation IS the tracked entity.
 */
public final class WaypointBridge {

    // Vanilla max for the attribute is 6.0E7; stay just under so setBaseValue never clamps.
    private static final double TRANSMIT_RANGE = 5.9E7;
    private static final Set<String> NAMED_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
            "light_purple", "yellow", "white");

    private final GlitchStash plugin;

    private boolean available;
    private boolean warnedColor;

    private Method craftWorldGetHandle;
    private Method craftEntityGetHandle;
    private Method getWaypointManager;
    private Method trackWaypoint;
    private Method untrackWaypoint;
    private Method getAttribute;
    private Method setBaseValue;
    private Field transmitRangeHolder;

    public WaypointBridge(GlitchStash plugin) {
        this.plugin = plugin;
        try {
            Class<?> craftWorld = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Class<?> craftEntity = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> manager = Class.forName("net.minecraft.server.waypoints.ServerWaypointManager");
            Class<?> transmitter = Class.forName("net.minecraft.world.waypoints.WaypointTransmitter");
            Class<?> livingEntity = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> attributeInstance = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeInstance");
            craftWorldGetHandle = craftWorld.getMethod("getHandle");
            craftEntityGetHandle = craftEntity.getMethod("getHandle");
            getWaypointManager = serverLevel.getMethod("getWaypointManager");
            trackWaypoint = manager.getMethod("trackWaypoint", transmitter);
            untrackWaypoint = manager.getMethod("untrackWaypoint", transmitter);
            getAttribute = livingEntity.getMethod("getAttribute", Class.forName("net.minecraft.core.Holder"));
            setBaseValue = attributeInstance.getMethod("setBaseValue", double.class);
            transmitRangeHolder = Class.forName("net.minecraft.world.entity.ai.attributes.Attributes")
                    .getField("WAYPOINT_TRANSMIT_RANGE");
            available = true;
        } catch (Throwable t) {
            available = false;
            plugin.getLogger().info("Locator-bar waypoints unavailable on this server (needs Purpur/1.21.9+ "
                    + "with waypoint API): " + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    public boolean locatorWaypointsAvailable() {
        return available;
    }

    /**
     * Turns the marker into a tracked locator-bar waypoint. Must run on the
     * global/region thread while the marker is alive in its world.
     */
    public void register(ArmorStand marker, String tag, String colorConfig) {
        if (!available || marker == null) return;
        try {
            Object level = craftWorldGetHandle.invoke(marker.getWorld());
            Object nms = craftEntityGetHandle.invoke(marker);
            Object manager = getWaypointManager.invoke(level);
            Object instance = getAttribute.invoke(nms, transmitRangeHolder.get(null));
            setBaseValue.invoke(instance, TRANSMIT_RANGE);
            trackWaypoint.invoke(manager, nms);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Waypoint registration failed for " + tag, t);
            return;
        }
        final String colorArg = colorArg(colorConfig);
        if (colorArg != null) {
            FoliaScheduler.runLaterGlobal(plugin, () -> applyColor(marker, tag, colorArg), 1L);
        } else {
            plugin.getLogger().fine("Waypoint " + tag + " uses the default icon (no/invalid waypoint-color).");
        }
    }

    /** Removes the waypoint; safe even if the entity is already despawned. */
    public void unregister(ArmorStand marker) {
        if (!available || marker == null) return;
        try {
            Object level = craftWorldGetHandle.invoke(marker.getWorld());
            Object nms = craftEntityGetHandle.invoke(marker);
            Object manager = getWaypointManager.invoke(level);
            Object instance = getAttribute.invoke(nms, transmitRangeHolder.get(null));
            setBaseValue.invoke(instance, 0.0D);
            untrackWaypoint.invoke(manager, nms);
        } catch (Throwable ignored) {
            // Entity already gone — ServerLevel$EntityCallbacks untracked it.
        }
    }

    private void applyColor(ArmorStand marker, String tag, String colorArg) {
        if (marker == null || !marker.isValid()) return;
        String cmd = "waypoint modify @e[tag=" + tag + ",limit=1,type=armor_stand] color " + colorArg;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Waypoint color dispatch failed for " + tag, t);
        }
    }

    /** Maps the config value onto a valid {@code waypoint modify ... color} argument, or null. */
    private String colorArg(String colorConfig) {
        if (colorConfig == null) return null;
        String value = colorConfig.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return null;
        if (value.startsWith("#")) value = value.substring(1);
        if (value.matches("[0-9a-f]{6}")) return "hex " + value;
        if (NAMED_COLORS.contains(value)) return value;
        if (!warnedColor) {
            warnedColor = true;
            plugin.getLogger().warning("Invalid auto-extract.dynamic.waypoint-color '" + colorConfig
                    + "' — use a vanilla color name (e.g. aqua) or 6-digit hex.");
        }
        return null;
    }
}
