package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /raidadmin {reload, list, end <player>}
 */
public final class RaidAdminCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;
    private final RaidManager manager;

    public RaidAdminCommand(GlitchRaid plugin, RaidManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("glitchraid.admin")) {
            sender.sendMessage(MM.deserialize("<red>You don't have permission (glitchraid.admin).</red>"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(MM.deserialize("<green>GlitchRaid configuration reloaded.</green>"));
            }
            case "list" -> {
                int count = manager.getActiveCount();
                sender.sendMessage(MM.deserialize("<gold><bold>GlitchRaid</bold> <gray>Active raids: <white>" + count + "</white></gray>"));
                if (count == 0) {
                    sender.sendMessage(MM.deserialize("<gray>No active raids.</gray>"));
                    return true;
                }
                for (RaidSession session : manager.getAllSessions()) {
                    String leaderName = getName(session.getLeader());
                    int remaining = session.getRemainingSeconds();
                    String line = "<gray>- Leader: <white>" + leaderName + "</white>"
                            + " <gray>Time left: <white>" + manager.formatTime(remaining) + "</white>"
                            + " <gray>Loot: <gold>" + session.getTotalLootValue() + "</gold>"
                            + " <gray>Deaths: <red>" + session.getTotalDeaths() + "</red>"
                            + " <gray>Members: <white>" + session.getMembers().size() + "/" + manager.getPartyMaxSize() + "</white>";
                    sender.sendMessage(MM.deserialize(line));
                }
            }
            case "end" -> {
                if (args.length < 2) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /raidadmin end <player></red>"));
                    return true;
                }
                String targetName = args[1];
                Player target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    // Try exact offline lookup
                    var offline = Bukkit.getOfflinePlayer(targetName);
                    if (offline.getName() == null) {
                        sender.sendMessage(MM.deserialize("<red>Player not found: <white>" + targetName + "</white></red>"));
                        return true;
                    }
                    if (!manager.isInRaid(offline.getUniqueId())) {
                        sender.sendMessage(MM.deserialize("<red>Player not in a raid: <white>" + targetName + "</white></red>"));
                        return true;
                    }
                    manager.endRaid(offline.getUniqueId(), RaidEndReason.MANUAL);
                    sender.sendMessage(MM.deserialize("<green>Ended raid for <white>" + offline.getName() + "</white> (offline)</green>"));
                    return true;
                }
                if (!manager.isInRaid(target.getUniqueId())) {
                    sender.sendMessage(MM.deserialize("<red>Player not in a raid: <white>" + target.getName() + "</white></red>"));
                    return true;
                }
                manager.endRaid(target.getUniqueId(), RaidEndReason.MANUAL);
                sender.sendMessage(MM.deserialize("<green>Ended raid for <white>" + target.getName() + "</white></green>"));
            }
            case "rank" -> {
                // /raidadmin rank <set|add|reset> <player> [n]
                var ranks = plugin.getRankManager();
                if (ranks == null || args.length < 3) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /raidadmin rank <set|add|reset> <player> [n]</red>"));
                    return true;
                }
                var target = Bukkit.getOfflinePlayerIfCached(args[2]);
                if (target == null) {
                    sender.sendMessage(MM.deserialize("<red>Player not found: <white>" + args[2] + "</white></red>"));
                    return true;
                }
                String op = args[1].toLowerCase();
                if (op.equals("reset")) {
                    ranks.reset(target.getUniqueId());
                } else {
                    int n;
                    try {
                        n = Integer.parseInt(args.length > 3 ? args[3] : "");
                    } catch (NumberFormatException e) {
                        sender.sendMessage(MM.deserialize("<red>Give a number of RR.</red>"));
                        return true;
                    }
                    int base = op.equals("add") ? ranks.rr(target.getUniqueId()) : 0;
                    ranks.set(target.getUniqueId(), target.getName(), base + n);
                }
                int rr = ranks.rr(target.getUniqueId());
                sender.sendMessage(MM.deserialize("<green>" + target.getName() + " is now " + ranks.tierOf(rr).styled()
                        + " <green>(" + rr + " RR)</green></green>"));
            }
            default -> {
                sender.sendMessage(MM.deserialize("<red>Unknown subcommand.</red>"));
                sendHelp(sender);
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><bold>GlitchRaid Admin</bold></gold>"));
        sender.sendMessage(MM.deserialize("<yellow>/raidadmin reload</yellow> <gray>— Reload config</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/raidadmin list</yellow> <gray>— List active raids</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/raidadmin end <player></yellow> <gray>— End player's raid</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/raidadmin rank <set|add|reset> <player> [n]</yellow> <gray>— Raider Rank RR</gray>"));
    }

    private String getName(java.util.UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        var offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}
