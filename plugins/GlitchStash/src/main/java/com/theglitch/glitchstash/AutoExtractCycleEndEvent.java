package com.theglitch.glitchstash;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by {@link AutoExtractScheduler} at t0+30m+5s — after the RED-world
 * timeout kill and just before the next 31-minute cycle restarts.
 * <p>
 * The loot team should listen for this event and scatter world loot /
 * reset containers. It is also a hook for external plugins (GlitchLoot,
 * GlitchItems containers) that want to react to the automated cycle.
 * <p>
 * The event is synchronous and runs on the global region scheduler.
 */
public final class AutoExtractCycleEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final long cycleStartedAtMillis;
    private final int cycleNumber;

    public AutoExtractCycleEndEvent(long cycleStartedAtMillis, int cycleNumber) {
        this.cycleStartedAtMillis = cycleStartedAtMillis;
        this.cycleNumber = cycleNumber;
    }

    /** Epoch millis when this cycle's t0 (arena start) fired. */
    public long getCycleStartedAtMillis() {
        return cycleStartedAtMillis;
    }

    /** Monotonic cycle counter (1 == first firing since server/plug enable). */
    public int getCycleNumber() {
        return cycleNumber;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
