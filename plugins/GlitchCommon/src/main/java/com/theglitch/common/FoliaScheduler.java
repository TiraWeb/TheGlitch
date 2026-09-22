package com.theglitch.common;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-safe scheduler wrapper shared by all Glitch plugins.
 * <p>
 * Prefers Paper's GlobalRegionScheduler / RegionScheduler / EntityScheduler when
 * available (Paper 1.20+ + Folia), falls back to {@link Bukkit#getScheduler()}
 * on Purpur, so scheduling logic runs identically on both platforms without
 * thread violations.
 * </p>
 * <p>
 * Location-sensitive operations (block place/clear) use
 * {@code RegionScheduler} when available — required on Folia where the global
 * region does not own chunk data. Reads that may load chunks use
 * {@link World#isChunkLoaded(int, int)} / {@link World#getChunkAtAsync(int, int, boolean)}
 * for async-safe handling.
 * </p>
 */
public final class FoliaScheduler {

    private static final boolean HAS_PAPER_SCHEDULER;
    private static final boolean IS_FOLIA;

    static {
        boolean has = false;
        try {
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            has = true;
        } catch (NoSuchMethodException e) {
            has = false;
        }
        HAS_PAPER_SCHEDULER = has;

        boolean folia = false;
        if (has) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                folia = true;
            } catch (ClassNotFoundException e) {
                folia = false;
            }
        }
        IS_FOLIA = folia;
    }

    /**
     * Cached at class-init — a live server's platform never changes mid-run.
     * Previously called {@code Class.forName(...)} on every invocation, which on
     * Purpur always throws (walking the full plugin classloader group each time).
     * Callers like {@code ScatterManager.placeNew()} invoke this once per container
     * placement attempt (hundreds per scatter cycle); uncached, that repeated
     * classloader traversal was a real contributor to multi-second main-thread
     * stalls during scatter (confirmed via a live watchdog thread dump pinned on
     * this call, 2026-09-22).
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public interface Cancellable {
        void cancel();
    }

    private static class BukkitCancellable implements Cancellable {
        private final org.bukkit.scheduler.BukkitTask task;
        BukkitCancellable(org.bukkit.scheduler.BukkitTask task) { this.task = task; }
        @Override public void cancel() { try { task.cancel(); } catch (Exception ignored) {} }
    }

    private static class PaperCancellable implements Cancellable {
        private final io.papermc.paper.threadedregions.scheduler.ScheduledTask task;
        PaperCancellable(io.papermc.paper.threadedregions.scheduler.ScheduledTask task) { this.task = task; }
        @Override public void cancel() { try { task.cancel(); } catch (Exception ignored) {} }
    }

    // ---- Global region -----------------------------------------------------

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (HAS_PAPER_SCHEDULER) {
            try {
                Bukkit.getGlobalRegionScheduler().execute(plugin, task);
                return;
            } catch (Throwable ignored) {}
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runLaterGlobal(Plugin plugin, Runnable task, long delayTicks) {
        if (HAS_PAPER_SCHEDULER) {
            try {
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, s -> task.run(), delayTicks);
                return;
            } catch (Throwable ignored) {}
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static Cancellable runLaterGlobalCancellable(Plugin plugin, Runnable task, long delayTicks) {
        if (HAS_PAPER_SCHEDULER) {
            try {
                io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, s -> task.run(), delayTicks);
                return new PaperCancellable(t);
            } catch (Throwable ignored) {}
        }
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return new BukkitCancellable(t);
    }

    public static Cancellable runAtFixedRateGlobal(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (HAS_PAPER_SCHEDULER) {
            try {
                io.papermc.paper.threadedregions.scheduler.ScheduledTask t =
                        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, s -> task.run(), delayTicks, periodTicks);
                return new PaperCancellable(t);
            } catch (Throwable ignored) {}
        }
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new BukkitCancellable(t);
    }

    // ---- Region-aware (required for block edits on Folia) ------------------

    /**
     * Run a task on the region that owns {@code loc}. Falls back to global.
     * Used for block place/clear so Folia's region ownership is respected.
     */
    public static void runAtLocation(Plugin plugin, Location loc, Runnable task) {
        if (loc == null || loc.getWorld() == null) {
            runGlobal(plugin, task);
            return;
        }
        if (HAS_PAPER_SCHEDULER) {
            try {
                // Paper 1.20+ RegionScheduler — owning thread per chunk region
                Bukkit.getRegionScheduler().run(plugin, loc, s -> task.run());
                return;
            } catch (Throwable ignored) {
                // Fall back to global below
            }
            // Legacy Folia GlobalRegion fallback
            try {
                Bukkit.getGlobalRegionScheduler().execute(plugin, task);
                return;
            } catch (Throwable ignored) {}
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    // ---- Entity scheduler --------------------------------------------------

    public static void runEntity(Player player, Plugin plugin, Runnable task) {
        if (HAS_PAPER_SCHEDULER) {
            try {
                player.getScheduler().execute(plugin, task, null, 1L);
                return;
            } catch (Throwable ignored) {}
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runDelayedEntity(Player player, Plugin plugin, Runnable task, long delayTicks) {
        if (HAS_PAPER_SCHEDULER) {
            try {
                player.getScheduler().runDelayed(plugin, s -> task.run(), null, delayTicks);
                return;
            } catch (Throwable ignored) {}
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void teleportEntity(Player player, Plugin plugin, Location dest) {
        if (HAS_PAPER_SCHEDULER) {
            try {
                player.getScheduler().run(plugin, s -> player.teleport(dest), null);
                return;
            } catch (Throwable ignored) {}
        }
        player.teleport(dest);
    }

    private FoliaScheduler() {}
}
