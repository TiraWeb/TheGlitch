package com.theglitch.glitchhud;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Best-effort reflection probe for GlitchStash AutoExtract cycle timing.
 * Never throws; returns -1 when unavailable.
 */
public final class StashCycleProbe {

    private StashCycleProbe() {}

    // Cached reflection — formatNextCycle() runs per HUD tick per RED player,
    // so avoid getMethod() on every call. Volatile + benign races.
    private static volatile java.lang.reflect.Method CACHED_GET_INSTANCE;
    private static volatile java.lang.reflect.Method CACHED_GET_SCHEDULER;
    private static volatile java.lang.reflect.Method CACHED_GET_LAST;
    private static volatile java.lang.reflect.Method CACHED_GET_INTERVAL;
    private static final String[] SCHEDULER_NAMES = {"getAutoExtractScheduler", "getScheduler", "getExtractScheduler"};

    /** Millis until next extraction cycle, or -1 if unavailable. */
    public static long getMillisUntilNextCycle() {
        try {
            Plugin stash = Bukkit.getPluginManager().getPlugin("GlitchStash");
            if (stash == null || !stash.isEnabled()) return -1;
            Object inst = stash;
            try {
                java.lang.reflect.Method gi = CACHED_GET_INSTANCE;
                if (gi == null || !gi.getDeclaringClass().isInstance(stash)) {
                    try {
                        gi = stash.getClass().getMethod("getInstance");
                        CACHED_GET_INSTANCE = gi;
                    } catch (Exception ignored) { gi = null; }
                }
                if (gi != null) {
                    Object maybe = gi.invoke(null);
                    if (maybe != null) inst = maybe;
                }
            } catch (Exception ignored) {}
            Object scheduler = null;
            try {
                java.lang.reflect.Method cached = CACHED_GET_SCHEDULER;
                if (cached != null && cached.getDeclaringClass().isInstance(inst)) {
                    try {
                        scheduler = cached.invoke(inst);
                    } catch (Exception ignored) { scheduler = null; }
                }
                if (scheduler == null) {
                    for (String m : SCHEDULER_NAMES) {
                        try {
                            java.lang.reflect.Method method = inst.getClass().getMethod(m);
                            scheduler = method.invoke(inst);
                            if (scheduler != null) {
                                CACHED_GET_SCHEDULER = method;
                                break;
                            }
                        } catch (NoSuchMethodException ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            if (scheduler == null) return -1;
            java.lang.reflect.Method getLast = CACHED_GET_LAST;
            if (getLast == null || !getLast.getDeclaringClass().isInstance(scheduler)) {
                try {
                    getLast = scheduler.getClass().getMethod("getLastCycleStartMillis");
                    CACHED_GET_LAST = getLast;
                } catch (Exception ignored) { return -1; }
            }
            java.lang.reflect.Method getInterval = CACHED_GET_INTERVAL;
            if (getInterval == null || !getInterval.getDeclaringClass().isInstance(scheduler)) {
                try {
                    getInterval = scheduler.getClass().getMethod("getIntervalMinutes");
                    CACHED_GET_INTERVAL = getInterval;
                } catch (Exception ignored) { return -1; }
            }
            long last = (long) getLast.invoke(scheduler);
            int interval = (int) getInterval.invoke(scheduler);
            if (last <= 0 || interval <= 0) return -1;
            long next = last + (long) interval * 60_000L;
            return Math.max(0, next - System.currentTimeMillis());
        } catch (Exception e) {
            return -1;
        }
    }

    public static String formatNextCycle() {
        long ms = getMillisUntilNextCycle();
        if (ms < 0) return null;
        long s = ms / 1000;
        long m = s / 60;
        long sec = s % 60;
        return String.format("%02d:%02d", m, sec);
    }
}
