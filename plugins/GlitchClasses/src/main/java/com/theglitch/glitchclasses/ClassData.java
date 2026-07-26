package com.theglitch.glitchclasses;

import java.util.UUID;

/**
 * Immutable data record for a player's class state.
 */
public record ClassData(
        UUID uuid,
        String className,
        int level,
        int xp
) {}
