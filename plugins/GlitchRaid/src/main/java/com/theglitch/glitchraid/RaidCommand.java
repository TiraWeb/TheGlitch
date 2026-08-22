package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /raid {start, end, status, help}
 */
public final class RaidCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;
    private final RaidManager manager;

    public RaidCommand(GlitchRaid plugin, RaidManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Players only.</red>"));
            return true;
        }
        if (!player.hasPermission("glitchraid.raid")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission (glitchraid.raid).</red>"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start" -> {
                if (manager.isInRaid(player.getUniqueId())) {
                    String alreadyRaw = plugin.getConfig().getString("messages.already-in-raid", "<red>You are already in a raid!</red>");
                    player.sendMessage(MM.deserialize(alreadyRaw));
                    return true;
                }
                boolean started = manager.startRaid(player);
                if (!started) {
                    String alreadyRaw = plugin.getConfig().getString("messages.already-in-raid", "<red>You are already in a raid!</red>");
                    player.sendMessage(MM.deserialize(alreadyRaw));
                }
            }
            case "end" -> {
                if (!manager.isInRaid(player.getUniqueId())) {
                    String notInRaw = plugin.getConfig().getString("messages.not-in-raid", "<red>You are not in a raid.</red>");
                    player.sendMessage(MM.deserialize(notInRaw));
                    return true;
                }
                // Raids end only by extraction or admin — block normal /raid end unless player is admin
                if (!player.hasPermission("glitchraid.admin")) {
                    String blocked = plugin.getConfig().getString("messages.raid-end-blocked",
                            "<red>Raids end only by extracting or by an admin. Use the extraction beacons!</red>");
                    player.sendMessage(MM.deserialize(blocked));
                    return true;
                }
                manager.endRaid(player.getUniqueId(), RaidEndReason.MANUAL);
            }
            case "status" -> {
                if (!manager.isInRaid(player.getUniqueId())) {
                    String notInRaw = plugin.getConfig().getString("messages.not-in-raid", "<red>You are not in a raid.</red>");
                    player.sendMessage(MM.deserialize(notInRaw));
                    return true;
                }
                RaidSession session = manager.getSession(player.getUniqueId());
                if (session == null) {
                    String notInRaw = plugin.getConfig().getString("messages.not-in-raid", "<red>You are not in a raid.</red>");
                    player.sendMessage(MM.deserialize(notInRaw));
                    return true;
                }
                int remaining = session.getRemainingSeconds();
                String formatted = manager.formatTime(remaining);
                String statusRaw = plugin.getConfig().getString("messages.raid-status",
                        "<gray>Time left: <white><time></white> <gray>| Loot: <gold><loot></gold> <gray>| Deaths: <red><deaths></red>");
                // Allow both placeholder styles: <time> and legacy <time> replacement
                String out = statusRaw
                        .replace("<time>", formatted)
                        .replace("<loot>", String.valueOf(session.getLootValue()))
                        .replace("<deaths>", String.valueOf(session.getDeaths()));
                player.sendMessage(MM.deserialize(out));
                // Also show bossbar update hint
                player.sendMessage(MM.deserialize("<gray>Party size: <white>" + session.getMembers().size() + "/" + manager.getPartyMaxSize() + "</white> <gray>| Leader: <white>" + getLeaderName(session) + "</white>"));
            }
            case "help" -> sendHelp(player);
            default -> {
                player.sendMessage(MM.deserialize("<red>Unknown subcommand. Use /raid help</red>"));
                sendHelp(player);
            }
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(MM.deserialize("<gold><bold>GlitchRaid</bold> <gray>— Raid lifecycle</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid start</yellow> <gray>— Start a new raid timer</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid end</yellow> <gray>— End your current raid</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid status</yellow> <gray>— Show time left, loot, deaths</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid help</yellow> <gray>— Show this help</gray>"));
    }

    private String getLeaderName(RaidSession session) {
        Player leader = plugin.getServer().getPlayer(session.getLeader());
        if (leader != null) return leader.getName();
        var offline = plugin.getServer().getOfflinePlayer(session.getLeader());
        String name = offline.getName();
        return name != null ? name : session.getLeader().toString().substring(0, 8);
    }
}
