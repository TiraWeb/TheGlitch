package com.theglitch.glitchraid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hub-to-Red walk-in portal (replaces the old red NPC).
 * <p>
 * The operator marks two corners in-game ({@code /redportal pos1|pos2} at their
 * feet) and {@code /redportal set} fills only AIR blocks in that cuboid with
 * END_PORTAL — existing blocks are never touched, nothing is broken. Stepping
 * in fires a vanilla END_PORTAL teleport which {@link RedPortalListener}
 * redirects to the raid world (no End dimension needed, nothing to link).
 * </p>
 */
public final class RedPortalManager {

    public record Mark(String world, int x, int y, int z) {}
    public record Region(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Material material) {
        public long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }
    public record FillResult(FillStatus status, int filled, int skipped, long volume) {}
    public enum FillStatus { OK, NO_MARKS, WORLD_MISMATCH, WORLD_MISSING, TOO_BIG }

    /**
     * Floor blocks that render reliably at any distance on any client
     * (vanilla portal shaders get culled by performance mods). Full solid
     * cubes only — no utility blocks, no light-level surprises beyond glow.
     */
    private static final java.util.Set<Material> ALLOWED_FLOORS = java.util.EnumSet.of(
            Material.END_PORTAL,
            Material.CRYING_OBSIDIAN,
            Material.MAGMA_BLOCK,
            Material.OBSIDIAN,
            Material.BLACKSTONE);

    /**
     * Resolves friendly names (end, crying, magma, obsidian, blackstone) or
     * full Material names to an allowed floor, or null when disallowed.
     */
    public static Material resolveFloor(String input) {
        if (input == null || input.isBlank()) return null;
        String key = input.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        Material matched = switch (key) {
            case "END", "END_PORTAL", "PORTAL", "STARFIELD" -> Material.END_PORTAL;
            case "CRYING", "CRYING_OBSIDIAN", "WEEPING" -> Material.CRYING_OBSIDIAN;
            case "MAGMA", "MAGMA_BLOCK", "LAVA" -> Material.MAGMA_BLOCK;
            case "OBSIDIAN" -> Material.OBSIDIAN;
            case "BLACKSTONE" -> Material.BLACKSTONE;
            default -> null;
        };
        if (matched != null) return matched;
        try {
            Material m = Material.valueOf(key);
            return ALLOWED_FLOORS.contains(m) ? m : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String floorOptions() {
        return "end, crying, magma, obsidian, blackstone";
    }

    private final GlitchRaid plugin;

    private final Map<UUID, Mark> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Mark> pos2 = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile int cooldownSeconds = 3;
    private volatile int maxVolume = 28000;
    private volatile Region region;

    public RedPortalManager(GlitchRaid plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("portal.enabled", true);
        cooldownSeconds = Math.max(0, plugin.getConfig().getInt("portal.cooldown-seconds", 3));
        maxVolume = Math.max(1, plugin.getConfig().getInt("portal.max-volume", 28000));
        String world = plugin.getConfig().getString("portal.region.world", "");
        if (world == null || world.isBlank()) {
            if (plugin.getConfig().contains("portal.region.min-x")) {
                plugin.getLogger().warning("RedPortal has saved coords but a blank world — the save was interrupted. Re-run /redportal pos1|pos2|set to restore it.");
            }
            region = null;
        } else {
            int minX = plugin.getConfig().getInt("portal.region.min-x");
            int minY = plugin.getConfig().getInt("portal.region.min-y");
            int minZ = plugin.getConfig().getInt("portal.region.min-z");
            int maxX = plugin.getConfig().getInt("portal.region.max-x");
            int maxY = plugin.getConfig().getInt("portal.region.max-y");
            int maxZ = plugin.getConfig().getInt("portal.region.max-z");
            Material mat = Material.END_PORTAL;
            try {
                String saved = plugin.getConfig().getString("portal.region.material", "END_PORTAL");
                Material parsed = saved == null ? null : Material.valueOf(saved);
                if (parsed != null && ALLOWED_FLOORS.contains(parsed)) mat = parsed;
            } catch (IllegalArgumentException ignored) {}
            region = new Region(world, Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                    Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ), mat);
        }
        plugin.getLogger().info("RedPortal reloaded (enabled=" + enabled
                + " cooldown=" + cooldownSeconds + "s maxVolume=" + maxVolume
                + " region=" + (region == null ? "none" : describe(region)) + ").");
    }

    public boolean isEnabled() { return enabled; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public int maxVolume() { return maxVolume; }
    public Region getRegion() { return region; }
    public boolean hasRegion() { return region != null; }

    public static String describe(Region r) {
        return r.world() + ":(" + r.minX() + "," + r.minY() + "," + r.minZ()
                + ")-(" + r.maxX() + "," + r.maxY() + "," + r.maxZ() + ") [" + r.material() + "]";
    }

    public boolean contains(Location loc) {
        Region r = region;
        if (r == null || loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equalsIgnoreCase(r.world())) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= r.minX() && x <= r.maxX()
                && y >= r.minY() && y <= r.maxY()
                && z >= r.minZ() && z <= r.maxZ();
    }

    public void markPos1(Player player) {
        Location l = player.getLocation();
        pos1.put(player.getUniqueId(), new Mark(l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ()));
    }

    public void markPos2(Player player) {
        Location l = player.getLocation();
        pos2.put(player.getUniqueId(), new Mark(l.getWorld().getName(),
                l.getBlockX(), l.getBlockY(), l.getBlockZ()));
    }

    public Mark getPos1(UUID uuid) { return pos1.get(uuid); }
    public Mark getPos2(UUID uuid) { return pos2.get(uuid); }

    public static String describe(Mark m) {
        return m.world() + ":(" + m.x() + "," + m.y() + "," + m.z() + ")";
    }

    /**
     * Fills the player's marked cuboid with the floor material.
     * Only AIR is filled, plus any blocks of a previous portal floor being
     * replaced (same cuboid, new look). Every other existing block is skipped,
     * never replaced. Persists the region.
     *
     * @param requested null/blank keeps the current floor (or END_PORTAL default)
     * @return fill counts with a status explaining any refusal
     */
    public FillResult fill(Player player, Material requested) {
        Mark a = pos1.get(player.getUniqueId());
        Mark b = pos2.get(player.getUniqueId());
        if (a == null || b == null) return new FillResult(FillStatus.NO_MARKS, 0, 0, 0);
        if (!a.world().equalsIgnoreCase(b.world())) return new FillResult(FillStatus.WORLD_MISMATCH, 0, 0, 0);
        World world = Bukkit.getWorld(a.world());
        if (world == null) return new FillResult(FillStatus.WORLD_MISSING, 0, 0, 0);

        Material mat = requested != null ? requested
                : (region != null ? region.material() : Material.END_PORTAL);
        Material convertFrom = (region != null && region.material() != mat) ? region.material() : null;

        int minX = Math.min(a.x(), b.x());
        int minY = Math.min(a.y(), b.y());
        int minZ = Math.min(a.z(), b.z());
        int maxX = Math.max(a.x(), b.x());
        int maxY = Math.max(a.y(), b.y());
        int maxZ = Math.max(a.z(), b.z());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > maxVolume) return new FillResult(FillStatus.TOO_BIG, 0, 0, volume);

        int filled = 0;
        int skipped = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block;
                    try {
                        block = world.getBlockAt(x, y, z);
                    } catch (Exception e) {
                        skipped++;
                        continue;
                    }
                    // Air-only, plus previous-floor conversion: existing builds
                    // and floors are never touched.
                    boolean convertible = convertFrom != null && block.getType() == convertFrom;
                    if (!block.getType().isAir() && !convertible) {
                        skipped++;
                        continue;
                    }
                    try {
                        block.setType(mat, false);
                        filled++;
                    } catch (Exception e) {
                        skipped++;
                    }
                }
            }
        }

        region = new Region(a.world(), minX, minY, minZ, maxX, maxY, maxZ, mat);
        saveRegion();
        plugin.getLogger().info("RedPortal set by " + player.getName() + " at "
                + describe(region) + " (filled=" + filled + " skipped=" + skipped + ").");
        return new FillResult(FillStatus.OK, filled, skipped, volume);
    }

    /**
     * Removes only the saved portal floor blocks inside the region (safe:
     * player builds are never the floor material unless the op chose a common
     * one — the fill only ever placed into air) and clears the region.
     */
    public int clear() {
        Region r = region;
        if (r == null) return 0;
        World world = Bukkit.getWorld(r.world());
        if (world == null) {
            region = null;
            saveRegion();
            return 0;
        }
        int cleared = 0;
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int y = r.minY(); y <= r.maxY(); y++) {
                for (int z = r.minZ(); z <= r.maxZ(); z++) {
                    Block block;
                    try {
                        block = world.getBlockAt(x, y, z);
                    } catch (Exception e) {
                        continue;
                    }
                    if (block.getType() == r.material()) {
                        try {
                            block.setType(Material.AIR, false);
                            cleared++;
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        plugin.getLogger().info("RedPortal cleared at " + describe(r) + " (cleared=" + cleared + ").");
        region = null;
        saveRegion();
        return cleared;
    }

    private void saveRegion() {
        Region r = region;
        if (r == null) {
            // Drop the whole section so no stale coords linger to confuse reloads.
            plugin.getConfig().set("portal.region", null);
            plugin.getConfig().set("portal.region.world", "");
        } else {
            plugin.getConfig().set("portal.region.world", r.world());
            plugin.getConfig().set("portal.region.material", r.material().name());
            plugin.getConfig().set("portal.region.min-x", r.minX());
            plugin.getConfig().set("portal.region.min-y", r.minY());
            plugin.getConfig().set("portal.region.min-z", r.minZ());
            plugin.getConfig().set("portal.region.max-x", r.maxX());
            plugin.getConfig().set("portal.region.max-y", r.maxY());
            plugin.getConfig().set("portal.region.max-z", r.maxZ());
        }
        plugin.saveConfig();
    }
}
