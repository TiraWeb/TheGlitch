package com.theglitch.common;

/**
 * Central constant for Specter Scavenge scoreboard tag.
 * AbilityListener in GlitchClasses sets this tag; GlitchItems containers read it for bonus loot rolls.
 * Centralizing avoids string duplication and typos across plugins.
 */
public final class ScavengeTag {

    /** Scoreboard tag that marks a Specter with Scavenge active — read by GlitchItems containers. */
    public static final String TAG = "specter_scavenge";

    private ScavengeTag() {
    }
}
