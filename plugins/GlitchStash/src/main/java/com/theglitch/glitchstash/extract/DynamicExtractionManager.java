package com.theglitch.glitchstash.extract;

import com.theglitch.glitchstash.AutoExtractScheduler;
import com.theglitch.glitchstash.ExtractionVariantManager;
import com.theglitch.glitchstash.GlitchStash;
import dev.velmax.velkoth.VelKothPlugin;
import dev.velmax.velkoth.arena.Arena;
import dev.velmax.velkoth.arena.Arena.CaptureMode;
import dev.velmax.velkoth.arena.region.CuboidRegion;
import dev.velmax.velkoth.manager.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Orchestrates one dynamic extraction cycle: pick random validated spots,
 * create/move the VelKoth arenas there, start them via the console path,
 * point the variant-key zones at them and show markers.
 *
 * Cycle flow lives in {@link AutoExtractScheduler}; the scheduler skips its
 * legacy arena discovery whenever {@link #runCycle(int)} returns true.
 */
public final class DynamicExtractionManager {

    private static final int DEFAULT_GRACE_PERIOD = 5;
    private static final int DEFAULT_MAX_SCORE = 30;
    private static final int ZONE_MARGIN_BLOCKS = 2;

    private final GlitchStash plugin;
    private final ExtractionMarkers markers;
    private final SpotPicker spotPicker;

    // Cached config (auto-extract.dynamic.*) — volatile for cross-thread reads
    private volatile boolean dynEnabled = true;
    private volatile int points = 3;
    private volatile int centerX = 1000;
    private volatile int centerZ = 1000;
    private volatile int radius = 1000;
    private volatile int minSeparation = 400;
    private volatile int maxSurfaceY = 100;
    private volatile int captureTimeSeconds = 30;
    private volatile int radiusBlocks = 5;
    private volatile String arenaPrefix = "extraction_dyn";
    private volatile String redWorld = "glitch_red";
    private volatile List<String> fallbackArenas = List.of();

    private final Object cycleLock = new Object();
    private boolean cycleActive = false;
    private volatile List<ExtractionPoint> currentPoints = List.of();

    public DynamicExtractionManager(GlitchStash plugin, ExtractionMarkers markers) {
        this.plugin = plugin;
        this.markers = markers;
        this.spotPicker = new SpotPicker(plugin);
        reload();
    }

    public void reload() {
        dynEnabled = plugin.getConfig().getBoolean("auto-extract.dynamic.enabled", true);
        points = clamp(plugin.getConfig().getInt("auto-extract.dynamic.points", 3), 1, 16);
        centerX = plugin.getConfig().getInt("auto-extract.dynamic.center-x", 1000);
        centerZ = plugin.getConfig().getInt("auto-extract.dynamic.center-z", 1000);
        radius = clamp(plugin.getConfig().getInt("auto-extract.dynamic.radius", 1000), 16, 100000);
        minSeparation = clamp(plugin.getConfig().getInt("auto-extract.dynamic.min-separation", 400), 0, 100000);
        maxSurfaceY = clamp(plugin.getConfig().getInt("auto-extract.dynamic.max-surface-y", 100), 1, 320);
        captureTimeSeconds = clamp(plugin.getConfig().getInt("auto-extract.dynamic.capture-time-seconds", 30), 1, 3600);
        radiusBlocks = clamp(plugin.getConfig().getInt("auto-extract.dynamic.radius-blocks", 5), 1, 64);
        String prefix = plugin.getConfig().getString("auto-extract.dynamic.arena-prefix", "extraction_dyn");
        arenaPrefix = (prefix == null || prefix.isBlank()) ? "extraction_dyn" : prefix.trim();
        String world = plugin.getConfig().getString("auto-extract.red-world", "glitch_red");
        redWorld = (world == null || world.isBlank()) ? "glitch_red" : world.trim();
        List<String> fallback = plugin.getConfig().getStringList("auto-extract.dynamic.fallback-arenas");
        List<String> normalized = new ArrayList<>();
        if (fallback != null) {
            for (String a : fallback) {
                if (a != null && !a.isBlank()) normalized.add(a.trim());
            }
        }
        fallbackArenas = List.copyOf(normalized);
    }

    /**
     * Runs one dynamic cycle. Returns false (and does nothing) when disabled
     * or the world is missing — the caller then uses the legacy arena path.
     */
    public boolean runCycle(int cycleNumber) {
        if (!dynEnabled) return false;

        World world = Bukkit.getWorld(redWorld);
        if (world == null) {
            plugin.getLogger().warning("[DynamicExtract] RED world '" + redWorld + "' not found — falling back to legacy arena discovery for cycle #" + cycleNumber + ".");
            return false;
        }

        long raidMs = plugin.getConfig().getInt("auto-extract.raid-duration-minutes", 30) * 60L * 1000L;
        long openUntil = System.currentTimeMillis() + raidMs;

        SpotPicker.PickSpec spec = new SpotPicker.PickSpec(centerX, centerZ, radius, minSeparation,
                maxSurfaceY, radiusBlocks, fallbackArenas);
        List<ExtractionPoint> picked = spotPicker.pick(world, points, arenaPrefix, openUntil, spec);
        if (picked.isEmpty()) return false;

        ArenaManager arenaManager = null;
        try {
            VelKothPlugin velkoth = VelKothPlugin.getInstance();
            if (velkoth != null) arenaManager = velkoth.getArenaManager();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DynamicExtract] VelKoth unavailable — falling back to legacy arenas for cycle #" + cycleNumber, e);
            return false;
        }
        if (arenaManager == null) return false;

        List<String> started = new ArrayList<>(picked.size());
        try {
            boolean dirty = false;
            for (ExtractionPoint p : picked) {
                CuboidRegion region = new CuboidRegion(world, p.x() - radiusBlocks, p.y() - 1,
                        p.z() - radiusBlocks, p.x() + radiusBlocks, p.y() + 4, p.z() + radiusBlocks);
                Arena arena = arenaManager.getArena(p.arenaId());
                if (arena != null) {
                    arena.setRegion(region);
                    dirty = true;
                } else {
                    arena = new Arena(p.arenaId(), p.arenaId(), region, captureTimeSeconds, CaptureMode.CAPTURE,
                            DEFAULT_GRACE_PERIOD, DEFAULT_MAX_SCORE);
                    arenaManager.addArena(arena);
                    dirty = true;
                }
            }
            if (dirty) arenaManager.saveArenas();

            for (ExtractionPoint p : picked) {
                if (startArena(p.arenaId())) started.add(p.arenaId());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DynamicExtract] Arena setup failed for cycle #" + cycleNumber + " — falling back to legacy arenas.", e);
            return false;
        }

        updateVariantZones(picked);

        try {
            markers.show(picked);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DynamicExtract] Marker display failed (cycle continues): " + e.getMessage(), e);
        }

        synchronized (cycleLock) {
            currentPoints = List.copyOf(picked);
            cycleActive = true;
        }

        StringBuilder coords = new StringBuilder();
        for (ExtractionPoint p : picked) {
            if (coords.length() > 0) coords.append(", ");
            coords.append("(").append(p.x()).append(",").append(p.z()).append(")");
        }
        plugin.getLogger().info("[DynamicExtract] Cycle #" + cycleNumber + ": " + started.size() + "/" + picked.size()
                + " started at " + coords + " (world=" + redWorld + ", open " + (raidMs / 60000L) + "m)");
        return true;
    }

    /**
     * Stops the dynamic arenas, clears markers and resets state. Idempotent —
     * safe to call from both the timeout and scatter paths and from onDisable.
     */
    public void endCycle() {
        List<ExtractionPoint> toStop;
        synchronized (cycleLock) {
            if (!cycleActive) return;
            cycleActive = false;
            toStop = currentPoints;
            currentPoints = List.of();
        }
        for (ExtractionPoint p : toStop) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "koth stop " + p.arenaId());
            } catch (Exception ignored) {
                // Arena may already have ended — VelKoth owns the authoritative stop
            }
        }
        try {
            markers.clear();
        } catch (Exception e) {
            plugin.getLogger().fine("[DynamicExtract] Marker clear failed: " + e.getMessage());
        }
    }

    private boolean startArena(String arenaId) {
        try {
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "koth start " + arenaId);
            if (!dispatched) {
                plugin.getLogger().warning("[DynamicExtract] 'koth start " + arenaId + "' dispatch returned false — command may be unknown.");
            }
            return dispatched;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DynamicExtract] Failed to dispatch 'koth start " + arenaId + "'", e);
            return false;
        }
    }

    /**
     * One variant zone per point covering the arena rect + margin. Key settings
     * are inherited from the first config-defined template zone so operators can
     * keep key semantics without per-cycle zone entries.
     */
    private void updateVariantZones(List<ExtractionPoint> picked) {
        try {
            ExtractionVariantManager variants = plugin.getExtractionVariantManager();
            if (variants == null) return;

            ExtractionVariantManager.Variant template = null;
            List<ExtractionVariantManager.Variant> configured = variants.getVariants();
            if (configured != null && !configured.isEmpty()) template = configured.get(0);

            List<ExtractionVariantManager.Variant> zones = new ArrayList<>(picked.size());
            for (ExtractionPoint p : picked) {
                int x1 = p.x() - radiusBlocks - ZONE_MARGIN_BLOCKS;
                int z1 = p.z() - radiusBlocks - ZONE_MARGIN_BLOCKS;
                int x2 = p.x() + radiusBlocks + ZONE_MARGIN_BLOCKS;
                int z2 = p.z() + radiusBlocks + ZONE_MARGIN_BLOCKS;
                zones.add(new ExtractionVariantManager.Variant(
                        p.arenaId(), p.world(), x1, z1, x2, z2,
                        template != null ? template.keyId() : "",
                        template != null ? template.keyMaterial() : "",
                        template != null ? template.keyName() : "",
                        template != null ? template.payoutBonus() : 0));
            }
            variants.setRuntimeZones(zones);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DynamicExtract] Failed to update variant zones: " + e.getMessage(), e);
        }
    }

    public List<ExtractionPoint> getCurrentPoints() {
        List<ExtractionPoint> pts = currentPoints;
        return pts == null ? List.of() : List.copyOf(pts);
    }

    public boolean isDynamicEnabled() {
        return dynEnabled;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
