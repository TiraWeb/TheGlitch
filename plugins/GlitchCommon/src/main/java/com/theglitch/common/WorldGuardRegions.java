package com.theglitch.common;

import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * WorldGuard region checks without a compile-time WorldGuard dependency.
 * <p>
 * Used by loot scatter (GlitchItems) and extraction spot picking (GlitchStash) to keep
 * spawns out of protected regions. The reflective handles are resolved once against
 * WorldGuard's API types and reused, since both callers check hundreds of locations
 * per cycle.
 */
public final class WorldGuardRegions {

    private static final String GLOBAL_REGION = "__global__";

    private record Handles(Method wgInstance, Method platform, Method regionContainer, Method adaptWorld,
                           Method managerForWorld, Method blockVectorAt, Method applicableRegions,
                           Method regions, Method regionId) {
    }

    private static volatile Handles handles;

    private WorldGuardRegions() {
    }

    /**
     * Whether {@code loc} lies in any WorldGuard region other than {@code __global__}.
     * Callers must check that WorldGuard is enabled first.
     *
     * @throws ReflectiveOperationException when WorldGuard's API does not match
     */
    public static boolean isProtected(Location loc) throws ReflectiveOperationException {
        if (loc == null || loc.getWorld() == null) return false;
        Handles h = handles();
        Object wg = h.wgInstance().invoke(null);
        Object container = h.regionContainer().invoke(h.platform().invoke(wg));
        if (container == null) return false;
        Object manager = h.managerForWorld().invoke(container, h.adaptWorld().invoke(null, loc.getWorld()));
        if (manager == null) return false;
        Object vector = h.blockVectorAt().invoke(null, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Object set = h.applicableRegions().invoke(manager, vector);
        if (set == null) return false;
        Collection<?> regions = (Collection<?>) h.regions().invoke(set);
        if (regions.isEmpty()) return false;
        if (regions.size() == 1) {
            Object id = h.regionId().invoke(regions.iterator().next());
            return !GLOBAL_REGION.equalsIgnoreCase(String.valueOf(id));
        }
        return true;
    }

    private static Handles handles() throws ReflectiveOperationException {
        Handles h = handles;
        if (h != null) return h;
        synchronized (WorldGuardRegions.class) {
            if (handles != null) return handles;
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard");
            Class<?> platform = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform");
            Class<?> container = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            Class<?> manager = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
            Class<?> regionSet = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet");
            Class<?> region = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class<?> weWorld = Class.forName("com.sk89q.worldedit.world.World");
            Class<?> blockVector = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            handles = new Handles(
                    worldGuard.getMethod("getInstance"),
                    worldGuard.getMethod("getPlatform"),
                    platform.getMethod("getRegionContainer"),
                    adapter.getMethod("adapt", World.class),
                    container.getMethod("get", weWorld),
                    blockVector.getMethod("at", int.class, int.class, int.class),
                    manager.getMethod("getApplicableRegions", blockVector),
                    regionSet.getMethod("getRegions"),
                    region.getMethod("getId"));
            return handles;
        }
    }
}
