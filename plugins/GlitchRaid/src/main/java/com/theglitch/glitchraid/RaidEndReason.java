package com.theglitch.glitchraid;

/**
 * Reason a raid ended — used for summary screen and logging.
 */
public enum RaidEndReason {
    /** Timer expired without extraction — player killed, loot lost. */
    TIMEOUT,
    /** Alias for TIMEOUT — killed by the Glitch after refusing to extract. */
    TIMEOUT_DEATH,
    /** Successful extraction via VelKoth/hub. */
    EXTRACTED,
    /** Player or admin manually ended the raid. */
    MANUAL,
    /** Admin-forced end (via /raidadmin). */
    ADMIN,
    /** Leader quit the server. */
    LEADER_QUIT
}
