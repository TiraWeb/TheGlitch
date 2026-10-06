package com.theglitch.common;

import java.util.Set;

/**
 * World names shared by every plugin. Config files may override which worlds a
 * feature runs in; these constants are the built-in defaults.
 */
public final class Worlds {

    /** The original Red Zone map, and the default when a config names no world. */
    public static final String GLITCH_RED = "glitch_red";

    /** The Eleria Red Zone map. */
    public static final String GLITCH_RED_ELERIA = "glitch_red_eleria";

    /** The Horizons Red Zone map. */
    public static final String GLITCH_RED_HORIZONS = "glitch_red_horizons";

    /** All Red Zone worlds, where abilities, containers, insurance and extraction are active. */
    public static final Set<String> GAME_WORLDS = Set.of(GLITCH_RED, GLITCH_RED_ELERIA, GLITCH_RED_HORIZONS);

    /** Hub / spawn world (if needed). */
    public static final String HUB = "world";

    /** The new-player tutorial template world; each player runs in a copy named {@code tutorial_<n>}. */
    public static final String TUTORIAL = "tutorial";

    private Worlds() {
    }

    /** The tutorial template or one of its per-player instances ({@code tutorial_<n>}). */
    public static boolean isTutorialWorld(String worldName) {
        return worldName != null && (worldName.equals(TUTORIAL) || worldName.matches(TUTORIAL + "_[0-9]+"));
    }

    /**
     * Whether the given world name is a game world.
     */
    public static boolean isGameWorld(String worldName) {
        return worldName != null && GAME_WORLDS.contains(worldName);
    }
}
