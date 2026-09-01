package com.theglitch.glitchhud;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Best-effort reflection probe for GlitchStash AutoExtract cycle timing.
 * Never throws; returns -1 when unavailable.
 */
public final class StashCycleProbe {

    private StashCycleProbe() {}

    /** Millis until next extraction cycle, or -1 if unavailable. */
    public static long getMillisUntilNextCycle() {
        try {
            Plugin stash = Bukkit.getPluginManager().getPlugin("GlitchStash");
            if (stash == null || !stash.isEnabled()) return -1;
            Object inst = stash;
            try {
                java.lang.reflect.Method gi = stash.getClass().getMethod("getInstance");
                Object maybe = gi.invoke(null);
                if (maybe != null) inst = maybe;
            } catch (Exception ignored) {}
            Object scheduler = null;
            for (String m : new String[]{"getAutoExtractScheduler", "getScheduler", "getExtractScheduler"}) {
                try {
                    java.lang.reflect.Method method = inst.getClass().getMethod(m);
                    scheduler = method.invoke(inst);
                    if (scheduler != null) break;
                } catch (NoSuchMethodException ignored) {}
            }
            if (scheduler == null) return -1;
            java.lang.reflect.Method getLast = scheduler.getClass().getMethod("getLastCycleStartMillis");
            java.lang.reflect.Method getInterval = scheduler.getClass().getMethod("getIntervalMinutes");
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
