package com.theglitch.common;

import java.util.Set;

/**
 * Shared world constants — centralizes the hard-coded world names scattered across plugins.
 * <p>
 * Rarity and Resonance enums are not duplicated here yet; they could be moved from
 * GlitchItems in a follow-up. This class covers the world constants and provides
 * a single import for game-world checks.
 * </p>
 */
public final class Worlds {

    /** All game worlds where abilities, shops, extraction, etc. are active. */
    public static final Set<String> GAME_WORLDS = Set.of("glitch_pve", "glitch_red");

    /** Open-world PvE/PvP extraction zone. */
    public static final String GLITCH_RED = "glitch_red";

    /** Dungeon/instanced PvE world. */
    public static final String GLITCH_PVE = "glitch_pve";

    /** Hub / spawn world (if needed). */
    public static final String HUB = "world";

    private Worlds() {
    }

    /**
     * Whether the given world name is a game world.
     */
    public static boolean isGameWorld(String worldName) {
        return worldName != null && GAME_WORLDS.contains(worldName);
    }
}
