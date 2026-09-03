package com.theglitch.glitchraid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-safe scheduler wrapper — prefers Paper's GlobalRegionScheduler /
 * EntityScheduler when available, falls back to Bukkit scheduler on Purpur.
 * Mirrors GlitchStash pattern (GlobalRegionScheduler) for consistency.
 */
public final class FoliaScheduler {

    private static final boolean HAS_PAPER_SCHEDULER;

    static {
        boolean has = false;
        try {
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            has = true;
        } catch (NoSuchMethodException e) {
            has = false;
        }
        HAS_PAPER_SCHEDULER = has;
    }

    public static boolean isFolia() {
        if (!HAS_PAPER_SCHEDULER) return false;
        try {
            Class<?> cls = Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
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
                io.papermc.paper.threadedregions.scheduler.ScheduledTask t = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, s -> task.run(), delayTicks);
                return new PaperCancellable(t);
            } catch (Throwable ignored) {}
        }
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return new BukkitCancellable(t);
    }

    public static Cancellable runAtFixedRateGlobal(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (HAS_PAPER_SCHEDULER) {
            try {
                io.papermc.paper.threadedregions.scheduler.ScheduledTask t = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, s -> task.run(), delayTicks, periodTicks);
                return new PaperCancellable(t);
            } catch (Throwable ignored) {}
        }
        org.bukkit.scheduler.BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new BukkitCancellable(t);
    }

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
