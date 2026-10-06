package com.theglitch.glitchstash.extract;

import dev.velmax.velkoth.VelKothPlugin;
import dev.velmax.velkoth.api.event.KothCaptureStopEvent;
import dev.velmax.velkoth.api.event.KothStartEvent;
import dev.velmax.velkoth.api.event.KothStopEvent;
import dev.velmax.velkoth.arena.Arena;
import dev.velmax.velkoth.capture.CaptureManager;
import dev.velmax.velkoth.capture.CaptureSession;
import dev.velmax.velkoth.display.DisplayManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Starts and stops VelKoth arenas for the automatic extraction cycle without VelKoth's
 * announcements. {@code CaptureManager#startArena}/{@code #stopArena} send a "beacon
 * activated/deactivated" line (and a start title) to every online player, which for
 * three beacons in three worlds was 18 lines plus titles each round. These mirror those
 * two methods (VelKoth 1.0.9) step for step, minus the announcements; callers tell the
 * operators instead via {@link com.theglitch.common.OpNotice}.
 * <p>
 * A capture win still stops its arena through VelKoth itself, so the public
 * "completed extraction" and "deactivated" lines for a real extraction are unchanged.
 */
public final class QuietKoth {

    private QuietKoth() {
    }

    /** Activates an idle arena. False when it was already running or a listener cancelled the start. */
    public static boolean start(VelKothPlugin velKoth, Arena arena) {
        if (arena.state() != Arena.ArenaState.IDLE) return false;
        KothStartEvent event = new KothStartEvent(arena);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        arena.setState(Arena.ArenaState.ACTIVE);
        CaptureManager captures = velKoth.getCaptureManager();
        captures.createSession(arena);
        DisplayManager display = velKoth.getDisplayManager();
        display.createBossBar(arena);
        display.getScoreboardManager().updateAll();
        display.getHologramManager().spawn(arena);
        captures.startTickLoop();
        return true;
    }

    /** Deactivates a running arena. False when it was already idle (e.g. captured this round). */
    public static boolean stop(VelKothPlugin velKoth, Arena arena) {
        if (arena.state() == Arena.ArenaState.IDLE) return false;
        CaptureManager captures = velKoth.getCaptureManager();
        arena.setState(Arena.ArenaState.IDLE);

        CaptureSession session = captures.getSession(arena.id());
        if (session != null && session.capturingPlayer() != null) {
            Player capturer = Bukkit.getPlayer(session.capturingPlayer());
            if (capturer != null) {
                Bukkit.getPluginManager().callEvent(
                        new KothCaptureStopEvent(arena, capturer, KothCaptureStopEvent.Reason.EVENT_STOPPED));
            }
        }
        captures.removeSession(arena.id());
        DisplayManager display = velKoth.getDisplayManager();
        display.removeBossBar(arena);
        display.getHologramManager().remove(arena);
        display.getScoreboardManager().updateAll();
        Bukkit.getPluginManager().callEvent(new KothStopEvent(arena));
        if (velKoth.getArenaManager().getActiveArenas().isEmpty()) captures.stopTickLoop();
        return true;
    }
}
