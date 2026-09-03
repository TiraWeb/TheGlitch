package com.theglitch.glitchstash.ui;

import com.theglitch.glitchstash.GlitchStash;

/**
 * Centralized {@code modern-ui} panel config keys + defaults.
 * Single source for world-panel geometry and remote-perm node so
 * StashPanel, StashUICommand and DialogUI share the same strings.
 * Keys, defaults, permissions and message text are unchanged.
 */
public final class PanelConfig {

    public static final String ENABLED_KEY = "modern-ui.world-panel.enabled";
    public static final boolean ENABLED_DEFAULT = true;
    public static final String WORLD_KEY = "modern-ui.world-panel.world";
    public static final String WORLD_DEFAULT = "hub";
    public static final String X_KEY = "modern-ui.world-panel.x";
    public static final double X_DEFAULT = 67.5D;
    public static final String Y_KEY = "modern-ui.world-panel.y";
    public static final double Y_DEFAULT = -43.5D;
    public static final String Z_KEY = "modern-ui.world-panel.z";
    public static final double Z_DEFAULT = -5.5D;
    public static final String FACING_KEY = "modern-ui.world-panel.facing";
    public static final String FACING_DEFAULT = "west";
    public static final String SPACING_KEY = "modern-ui.world-panel.spacing";
    public static final double SPACING_DEFAULT = 1.35D;
    public static final String REMOTE_PERM_KEY = "modern-ui.remote-perm";
    public static final String REMOTE_PERM_DEFAULT = "theglitch.remoteui";

    public record Snapshot(String world, double x, double y, double z,
                           String facing, double spacing) {}

    private PanelConfig() {
    }

    public static boolean enabled(GlitchStash plugin) {
        try {
            return plugin.getConfig().getBoolean(ENABLED_KEY, ENABLED_DEFAULT);
        } catch (Throwable t) {
            return true;
        }
    }

    public static Snapshot load(GlitchStash plugin) {
        try {
            return new Snapshot(
                    plugin.getConfig().getString(WORLD_KEY, WORLD_DEFAULT),
                    plugin.getConfig().getDouble(X_KEY, X_DEFAULT),
                    plugin.getConfig().getDouble(Y_KEY, Y_DEFAULT),
                    plugin.getConfig().getDouble(Z_KEY, Z_DEFAULT),
                    plugin.getConfig().getString(FACING_KEY, FACING_DEFAULT),
                    plugin.getConfig().getDouble(SPACING_KEY, SPACING_DEFAULT));
        } catch (Throwable t) {
            return new Snapshot(WORLD_DEFAULT, X_DEFAULT, Y_DEFAULT, Z_DEFAULT, FACING_DEFAULT, SPACING_DEFAULT);
        }
    }

    /** Same fallback logic as before: blank/null falls back to default node. */
    public static String remotePermNode(GlitchStash plugin) {
        String node = REMOTE_PERM_DEFAULT;
        if (plugin != null) {
            try {
                node = plugin.getConfig().getString(REMOTE_PERM_KEY, REMOTE_PERM_DEFAULT);
            } catch (Throwable ignored) {
            }
        }
        if (node == null || node.isBlank()) {
            node = REMOTE_PERM_DEFAULT;
        }
        return node;
    }
}
