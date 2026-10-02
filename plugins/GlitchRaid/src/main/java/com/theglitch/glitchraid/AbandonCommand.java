package com.theglitch.glitchraid;

import com.theglitch.common.ChatConfirm;
import com.theglitch.glitchraid.gui.DungeonSelectGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /leave (rerouted here by {@link PartyCommandAlias} in raids and dungeons) and /abandon:
 * give up and go to the hub, after a chat [YES]/[NO].
 * <ul>
 *   <li>Red Zone raid: counts as dying (MIA) — the carried gear drops, insurance and the
 *   Secure Pouch still protect, Raider Rank takes the death penalty, no payout.</li>
 *   <li>Boss dungeon: MythicDungeons' own leave — no clear rewards, the key is spent.</li>
 * </ul>
 */
public final class AbandonCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final RaidManager manager;

    public AbandonCommand(RaidManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String world = player.getWorld().getName();
        if (manager.isRedWorld(world)) {
            if (!manager.isInRaid(player.getUniqueId())) {
                manager.teleportToHub(player);
                player.sendMessage(MM.deserialize("<gray>Back to the hub.</gray>"));
                return true;
            }
            ChatConfirm.ask(player, MM.deserialize("<red><bold>Leave the raid?</bold></red> <gray>You go MIA: everything you're carrying"
                    + " is lost <dark_gray>(insured gear + Secure Pouch kept)</dark_gray>, Raider Rank takes the death penalty, no rewards.</gray>"), () -> {
                if (!player.isOnline() || !manager.isRedWorld(player.getWorld().getName())) return;
                manager.abandonRaid(player);
            });
            return true;
        }
        if (DungeonSelectGUI.isDungeonWorld(world)) {
            ChatConfirm.ask(player, MM.deserialize("<red><bold>Leave the dungeon?</bold></red> <gray>No clear rewards, and the key isn't refunded.</gray>"), () -> {
                if (!player.isOnline() || !DungeonSelectGUI.isDungeonWorld(player.getWorld().getName())) return;
                player.performCommand("md leave");
            });
            return true;
        }
        player.sendMessage(MM.deserialize("<gray>You're not in a raid or a dungeon.</gray>"));
        return true;
    }
}
