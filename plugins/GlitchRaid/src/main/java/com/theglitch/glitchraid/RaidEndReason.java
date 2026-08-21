package com.theglitch.glitchraid;

/**
 * Reason a raid ended — used for summary screen and logging.
 */
public enum RaidEndReason {
    /** Timer expired. */
    TIMEOUT,
    /** Player or admin manually ended the raid. */
    MANUAL,
    /** Leader quit the server. */
    LEADER_QUIT
}
