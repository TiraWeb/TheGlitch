package com.theglitch.glitchstash.extract;

import com.theglitch.glitchstash.GlitchStash;
import dev.velmax.velkoth.VelKothPlugin;
import dev.velmax.velkoth.arena.Arena;
import dev.velmax.velkoth.arena.region.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Picks random validated surface spots for dynamic extraction cycles.
 * Placement validation mirrors GlitchItems ScatterManager (findValidTargetY /
 * isSolidGround / WorldGuard guard), plus capture-square flatness and
 * point-to-point separation. Purpur only: sync chunk loads.
 */
public final class SpotPicker {

    private static final int MAX_ATTEMPTS = 250;
    private static final int SCAN_DEPTH = 12;
    private static final int FLATNESS_TOLERANCE = 2;

    /** Per-cycle placement parameters from {@code auto-extract.dynamic}. */
    public record PickSpec(int centerX, int centerZ, int radius, int minSeparation,
                           int maxSurfaceY, int radiusBlocks, List<String> fallbackArenas) {}

    private final GlitchStash plugin;
    private volatile boolean wgWarned = false;

    public SpotPicker(GlitchStash plugin) {
        this.plugin = plugin;
    }

    /**
     * Picks up to {@code count} points. Falls back to the configured static
     * arenas (centers of their VelKoth regions) when random validation fails.
     * Never returns zero points without a warning.
     */
    public List<ExtractionPoint> pick(World world, int count, String arenaPrefix,
                                      long openUntilEpochMs, PickSpec spec) {
        List<ExtractionPoint> picked = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            ExtractionPoint p = pickRandom(world, picked, arenaPrefix + i, i, openUntilEpochMs, spec);
            if (p != null) {
                picked.add(p);
            }
        }

        if (picked.size() < count) {
            plugin.getLogger().info("[DynamicExtract] Random spots validated " + picked.size() + "/" + count
                    + " — filling from fallback arenas " + spec.fallbackArenas());
        }
        for (String fbName : spec.fallbackArenas()) {
            if (picked.size() >= count) break;
            ExtractionPoint p = fallbackPoint(fbName, picked.size(), spec.radiusBlocks(), openUntilEpochMs);
            if (p != null) {
                picked.add(p);
            } else {
                plugin.getLogger().warning("[DynamicExtract] Fallback arena '" + fbName + "' not found in VelKoth — skipped.");
            }
        }

        if (picked.size() < count) {
            plugin.getLogger().warning("[DynamicExtract] Only validated " + picked.size() + "/" + count
                    + " extraction points after " + MAX_ATTEMPTS + " attempts and fallback arenas — cycle runs with fewer points.");
        }
        if (picked.isEmpty()) {
            plugin.getLogger().warning("[DynamicExtract] Cycle could not validate any extraction point (terrain/WorldGuard dense?) — cycle will fall back to legacy arenas.");
        }
        return picked;
    }

    private ExtractionPoint pickRandom(World world, List<ExtractionPoint> picked, String arenaId,
                                       int index, long openUntil, PickSpec spec) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = spec.centerX() + rand.nextInt(-spec.radius(), spec.radius() + 1);
            int z = spec.centerZ() + rand.nextInt(-spec.radius(), spec.radius() + 1);

            // Sync chunk load — Purpur (no Folia region threads in this module's cycle path)
            try {
                world.getChunkAt(x >> 4, z >> 4);
            } catch (Exception e) {
                continue;
            }

            Integer targetY = findValidTargetY(world, x, z);
            if (targetY == null) continue;
            if (targetY > spec.maxSurfaceY()) continue; // floating islands

            int groundY = targetY - 1;
            if (!isFlatEnough(world, x, z, groundY, spec.radiusBlocks())) continue;

            if (tooClose(x, z, picked, spec.minSeparation())) continue;

            if (isProtectedRegion(new Location(world, x + 0.5, targetY, z + 0.5))) continue;

            return new ExtractionPoint(arenaId, world.getName(), x, targetY, z, spec.radiusBlocks(), openUntil, index);
        }
        return null;
    }

    /** Ground Y at 9 points (center + 4 edges + 4 corners) must be within ±2 of center. */
    private boolean isFlatEnough(World world, int x, int z, int groundY, int r) {
        int[] checkX = {x, x - r, x + r, x, x, x - r, x + r, x - r, x + r};
        int[] checkZ = {z, z, z, z - r, z + r, z - r, z - r, z + r, z + r};
        for (int i = 0; i < checkX.length; i++) {
            Integer colY = findValidTargetY(world, checkX[i], checkZ[i]);
            if (colY == null) return false;
            if (Math.abs((colY - 1) - groundY) > FLATNESS_TOLERANCE) return false;
            // Also ensure the column has solid ground 2 deep (not a 1-block pillar)
            Block below = world.getBlockAt(checkX[i], colY - 2, checkZ[i]);
            if (below == null || !below.getType().isSolid()) return false;
        }
        return true;
    }

    private boolean tooClose(int x, int z, List<ExtractionPoint> picked, int minSeparation) {
        if (minSeparation <= 0) return false;
        for (ExtractionPoint p : picked) {
            int dx = p.x() - x;
            int dz = p.z() - z;
            if (dx * dx + dz * dz < (long) minSeparation * minSeparation) return true;
        }
        return false;
    }

    /** Existing static arena used as fallback: re-validate its center Y. */
    private ExtractionPoint fallbackPoint(String arenaName, int index, int radiusBlocks, long openUntil) {
        try {
            VelKothPlugin velkoth = VelKothPlugin.getInstance();
            if (velkoth == null) return null;
            Arena arena = velkoth.getArenaManager().getArena(arenaName);
            if (arena == null) return null;
            if (!(arena.region() instanceof CuboidRegion cr)) return null;

            Location center = cr.getCenter();
            World world = cr.world();
            if (world == null) return null;
            // Re-validate Y instead of trusting stale region center
            Integer validY = findValidTargetY(world, center.getBlockX(), center.getBlockZ());
            int y = validY != null ? validY : center.getBlockY();
            if (validY == null) {
                plugin.getLogger().warning("[DynamicExtract] Fallback arena '" + arenaName + "' center Y " + center.getBlockY() + " not on valid ground — using raw Y but may float.");
            }
            return new ExtractionPoint(arenaName, world.getName(), center.getBlockX(), y,
                    center.getBlockZ(), radiusBlocks, openUntil, index);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DynamicExtract] Failed to read fallback arena '" + arenaName + "': " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Ground validation — ported from GlitchItems ScatterManager (no container check)
    // ------------------------------------------------------------------------

    /**
     * Scans down up to 10 blocks from the highest block to skip foliage/water
     * surface and find the first solid ground with 2 air blocks above.
     *
     * @return target Y (first air block above ground) or null if none valid
     */
    private Integer findValidTargetY(World world, int x, int z) {
        if (world == null) return null;
        int highest;
        try {
            highest = world.getHighestBlockYAt(x, z);
        } catch (Exception e) {
            return null;
        }
        int minH = world.getMinHeight();
        int maxH = world.getMaxHeight();
        for (int offset = 0; offset <= SCAN_DEPTH; offset++) {
            int groundY = highest - offset;
            int targetY = groundY + 1;
            int aboveY = groundY + 2;

            if (groundY < minH || aboveY >= maxH) continue;
            Block ground;
            Block target;
            Block above;
            try {
                ground = world.getBlockAt(x, groundY, z);
                target = world.getBlockAt(x, targetY, z);
                above = world.getBlockAt(x, aboveY, z);
            } catch (Exception e) {
                continue;
            }
            if (ground == null || target == null || above == null) continue;

            if (!target.getType().isAir()) continue;
            if (!above.getType().isAir()) continue;
            if (!isSolidGround(ground)) continue;

            Material gm = ground.getType();
            if (gm == Material.WATER || gm == Material.LAVA) continue;
            if (ground.isLiquid()) continue;

            return targetY;
        }
        return null;
    }

    private boolean isSolidGround(Block ground) {
        if (ground == null) return false;
        Material m = ground.getType();
        if (m.isAir()) return false;
        if (!m.isSolid()) return false;
        if (!m.isOccluding()) return false;
        if (m == Material.WATER || m == Material.LAVA) return false;
        if (m == Material.BARRIER || m == Material.BEDROCK) return false;
        try {
            if (Tag.LEAVES.isTagged(m)) return false;
            if (Tag.LOGS.isTagged(m)) return false;
        } catch (Exception ignored) {
            String n = m.name();
            if (n.contains("LEAVES") || n.contains("LOG")) return false;
        }
        // Reject container / scaffolding / fragile blocks that would make capture weird
        String n = m.name();
        if (n.contains("SHULKER") || n.contains("CHEST") || n.contains("BARREL") || n.contains("SCAFFOLDING")) return false;
        return true;
    }

    // ------------------------------------------------------------------------
    // WorldGuard — ported from GlitchItems ScatterManager (reflective, no hard dep)
    // ------------------------------------------------------------------------

    private boolean isProtectedRegion(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Plugin wg = org.bukkit.Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) return false;
        try {
            return isProtectedReflective(loc);
        } catch (Throwable t) {
            if (wgWarned) {
                plugin.getLogger().fine("[DynamicExtract] WG check failed: " + t.getMessage());
            } else {
                wgWarned = true;
                plugin.getLogger().log(Level.WARNING, "[DynamicExtract] WorldGuard check failed — allowing placement (error logged once)", t);
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isProtectedReflective(Location loc) throws Exception {
        Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
        Method getInstance = wgClass.getMethod("getInstance");
        Object wgInstance = getInstance.invoke(null);
        Method getPlatform = wgInstance.getClass().getMethod("getPlatform");
        Object platform = getPlatform.invoke(wgInstance);
        Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
        Object container = getRegionContainer.invoke(platform);
        if (container == null) return false;

        Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Method adaptWorld = bukkitAdapter.getMethod("adapt", World.class);
        Object weWorld = adaptWorld.invoke(null, loc.getWorld());

        Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
        Method get = container.getClass().getMethod("get", weWorldClass);
        Object regionManager = get.invoke(container, weWorld);
        if (regionManager == null) return false;

        Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
        Method at = bv3.getMethod("at", int.class, int.class, int.class);
        Object vec = at.invoke(null, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        Method getApplicable = regionManager.getClass().getMethod("getApplicableRegions", bv3);
        Object regionSet = getApplicable.invoke(regionManager, vec);
        if (regionSet == null) return false;

        Method sizeM = regionSet.getClass().getMethod("size");
        int size = (int) sizeM.invoke(regionSet);
        if (size == 0) return false;

        try {
            Method getRegions = regionSet.getClass().getMethod("getRegions");
            Object regions = getRegions.invoke(regionSet);
            if (regions instanceof java.util.Collection<?> col) {
                if (col.isEmpty()) return false;
                if (col.size() == 1) {
                    Object r = col.iterator().next();
                    try {
                        Method getId = r.getClass().getMethod("getId");
                        String id = (String) getId.invoke(r);
                        if ("__global__".equalsIgnoreCase(id)) return false;
                    } catch (Exception ignored) {
                        // Cannot read id — assume protected
                    }
                }
                return true;
            }
        } catch (Exception ignored) {
            // getRegions unavailable — fall through to size heuristic
        }
        return true;
    }
}
