package com.theglitch.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Central MiniMessage helper — deduplicates the 8+ per-plugin {@code MiniMessage.miniMessage()} instances.
 * <p>
 * All plugins should delegate to {@link #MM} or {@link #deserialize(String)} instead of
 * creating their own MiniMessage instances.
 * </p>
 */
public final class MiniMessageUtil {

    /** Shared MiniMessage instance. */
    public static final MiniMessage MM = MiniMessage.miniMessage();

    private MiniMessageUtil() {
    }

    /**
     * Deserialize MiniMessage string safely — falls back to plain text on parse failure.
     *
     * @param raw MiniMessage formatted string
     * @return Component
     */
    public static Component deserialize(String raw) {
        if (raw == null) return Component.empty();
        try {
            return MM.deserialize(raw);
        } catch (Exception e) {
            return Component.text(raw);
        }
    }
}
