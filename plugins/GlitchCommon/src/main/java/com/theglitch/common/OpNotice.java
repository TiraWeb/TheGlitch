package com.theglitch.common;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Notices about server automation (extraction cycles starting and ending, loot
 * re-scatter) that only operators need. Regular players never see them.
 */
public final class OpNotice {

    private OpNotice() {
    }

    /** Sends {@code message} to every online operator (Rogue Raider bots excluded). */
    public static void send(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() && !Bots.isBot(player)) player.sendMessage(message);
        }
    }
}
