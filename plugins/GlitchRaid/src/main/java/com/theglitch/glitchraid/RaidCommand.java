package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /raid {start, end, status, invite, accept, leave, kick, list, help}
 * Party is integrated: invite/accept/leave/kick manage the raid party.
 * If a party member enters glitch_red, the whole party is auto-teleported.
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
        PartyManager partyMgr = manager.getPartyManager();
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
                String out = statusRaw
                        .replace("<time>", formatted)
                        .replace("<loot>", String.valueOf(session.getLootValue(player.getUniqueId())))
                        .replace("<deaths>", String.valueOf(session.getDeaths(player.getUniqueId())));
                player.sendMessage(MM.deserialize(out));
                player.sendMessage(MM.deserialize("<gray>Party size: <white>" + session.getMembers().size() + "/" + manager.getPartyMaxSize() + "</white> <gray>| Leader: <white>" + getLeaderName(session) + "</white>"));
                if (session.getMembers().size() > 1) {
                    for (java.util.UUID mid : session.getMembers()) {
                        if (mid.equals(player.getUniqueId())) continue;
                        org.bukkit.entity.Player mp = plugin.getServer().getPlayer(mid);
                        String name = mp != null ? mp.getName() : plugin.getServer().getOfflinePlayer(mid).getName();
                        if (name == null) name = mid.toString().substring(0, 8);
                        player.sendMessage(MM.deserialize("<dark_gray>- " + name + ": <gold>" + session.getLootValue(mid) + "</gold> Loot <red>" + session.getDeaths(mid) + " deaths</red>"));
                    }
                }
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(MM.deserialize("<red>Usage: /raid invite <player></red>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(MM.deserialize("<red>Player not found: <white>" + args[1] + "</white></red>"));
                    return true;
                }
                if (target.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(MM.deserialize("<red>You can't invite yourself.</red>"));
                    return true;
                }
                if (manager.isInRaid(player.getUniqueId())) {
                    player.sendMessage(MM.deserialize("<red>Can't invite while in a raid — finish extraction first.</red>"));
                    return true;
                }
                if (partyMgr.hasParty(target.getUniqueId())) {
                    player.sendMessage(MM.deserialize("<red>That player is already in a party.</red>"));
                    return true;
                }
                boolean ok = partyMgr.invitePlayer(player, target);
                if (!ok) {
                    player.sendMessage(MM.deserialize("<red>Invite failed — party full (" + partyMgr.getParty(player.getUniqueId()).getSize() + "/" + manager.getPartyMaxSize() + ") or already invited.</red>"));
                    return true;
                }
                player.sendMessage(MM.deserialize("<green>Invited <white>" + target.getName() + "</white> to your raid party. <gray>(" + partyMgr.getParty(player.getUniqueId()).getSize() + "/" + manager.getPartyMaxSize() + ")</gray></green>"));
                target.sendMessage(MM.deserialize("<green><white>" + player.getName() + "</white> invited you to a raid party! <yellow>Use /raid accept</yellow> to join. <gray>(30s)</gray></green>"));
                target.sendMessage(MM.deserialize("<gray>Party leader: <white>" + player.getName() + "</white></gray>"));
            }
            case "accept" -> {
                boolean ok = partyMgr.acceptInvite(player);
                if (!ok) {
                    player.sendMessage(MM.deserialize("<red>No pending raid invite.</red>"));
                    return true;
                }
                Party party = partyMgr.getParty(player.getUniqueId());
                if (party == null) {
                    player.sendMessage(MM.deserialize("<green>Joined party.</green>"));
                    return true;
                }
                Player leader = Bukkit.getPlayer(party.getLeader());
                String leaderName = leader != null ? leader.getName() : Bukkit.getOfflinePlayer(party.getLeader()).getName();
                player.sendMessage(MM.deserialize("<green>Joined <white>" + leaderName + "</white>'s raid party! <gray>(" + party.getSize() + "/" + manager.getPartyMaxSize() + ")</gray></green>"));
                if (leader != null && !leader.getUniqueId().equals(player.getUniqueId())) {
                    leader.sendMessage(MM.deserialize("<green><white>" + player.getName() + "</white> joined your party! <gray>(" + party.getSize() + "/" + manager.getPartyMaxSize() + ")</gray></green>"));
                }
                // If party leader is already in a raid, pull new member into same raid and teleport if needed
                for (java.util.UUID mid : party.getMembers()) {
                    if (mid.equals(player.getUniqueId())) continue;
                    if (manager.isInRaid(mid)) {
                        RaidSession session = manager.getSession(mid);
                        if (session != null && !session.getMembers().contains(player.getUniqueId())) {
                            session.getMembers().add(player.getUniqueId());
                            manager.handlePartyMemberAddedToActiveRaid(player, session);
                            player.sendMessage(MM.deserialize("<gray>Added to ongoing raid — teleporting to party...</gray>"));
                            // Teleport to a party member in glitch_red if possible
                            for (java.util.UUID other : session.getMembers()) {
                                Player otherP = Bukkit.getPlayer(other);
                                if (otherP != null && otherP.isOnline() && otherP.getWorld().getName().equalsIgnoreCase(manager.getAutoStartWorld())) {
                                    try {
                                        FoliaScheduler.teleportEntity(player, plugin, otherP.getLocation());
                                        player.showBossBar(manager.getBossBarForSession(session));
                                    } catch (Exception ignored) {}
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            }
            case "decline", "deny" -> {
                boolean ok = partyMgr.declineInvite(player);
                if (!ok) player.sendMessage(MM.deserialize("<red>No pending invite.</red>"));
                else player.sendMessage(MM.deserialize("<gray>Declined invite.</gray>"));
            }
            case "leave" -> {
                if (!partyMgr.hasParty(player.getUniqueId())) {
                    player.sendMessage(MM.deserialize("<red>You're not in a party.</red>"));
                    return true;
                }
                if (manager.isInRaid(player.getUniqueId())) {
                    player.sendMessage(MM.deserialize("<red>Can't leave party while in a raid.</red>"));
                    return true;
                }
                boolean wasLeader = partyMgr.isLeader(player.getUniqueId());
                partyMgr.leaveParty(player.getUniqueId());
                player.sendMessage(MM.deserialize(wasLeader ? "<yellow>Disbanded your raid party.</yellow>" : "<yellow>Left the raid party.</yellow>"));
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(MM.deserialize("<red>Usage: /raid kick <player></red>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    // Try offline
                    var offline = Bukkit.getOfflinePlayer(args[1]);
                    if (offline.getName() == null) {
                        player.sendMessage(MM.deserialize("<red>Player not found.</red>"));
                        return true;
                    }
                    if (!partyMgr.hasParty(player.getUniqueId()) || !partyMgr.isLeader(player.getUniqueId())) {
                        player.sendMessage(MM.deserialize("<red>Only party leader can kick.</red>"));
                        return true;
                    }
                    // Offline kick by UUID lookup — find UUID via offline
                    boolean removed = false;
                    for (java.util.UUID mid : java.util.Set.copyOf(partyMgr.getParty(player.getUniqueId()).rawMembers())) {
                        if (mid.equals(offline.getUniqueId())) {
                            partyMgr.leaveParty(mid);
                            removed = true;
                            break;
                        }
                    }
                    player.sendMessage(removed ? MM.deserialize("<green>Kicked <white>" + offline.getName() + "</white>.</green>") : MM.deserialize("<red>Player not in your party.</red>"));
                    return true;
                }
                if (!partyMgr.kickMember(player, target)) {
                    player.sendMessage(MM.deserialize("<red>Kick failed — not leader or not in party.</red>"));
                    return true;
                }
                player.sendMessage(MM.deserialize("<green>Kicked <white>" + target.getName() + "</white> from party.</green>"));
                target.sendMessage(MM.deserialize("<red>You were kicked from the raid party.</red>"));
            }
            case "list", "party" -> {
                Party party = partyMgr.getParty(player.getUniqueId());
                if (party == null) {
                    player.sendMessage(MM.deserialize("<gray>You're not in a raid party. <yellow>/raid invite <player></yellow> to create one.</gray>"));
                    return true;
                }
                player.sendMessage(MM.deserialize("<gold><bold>Raid Party</bold> <gray>" + party.getSize() + "/" + manager.getPartyMaxSize() + " <gray>Leader: <white>" + getLeaderNameById(party.getLeader()) + "</white>"));
                for (java.util.UUID mid : party.getMembers()) {
                    boolean isLeader = mid.equals(party.getLeader());
                    String name = getPlayerName(mid);
                    boolean inRaid = manager.isInRaid(mid);
                    String suffix = isLeader ? " <yellow>[Leader]</yellow>" : "";
                    suffix += inRaid ? " <green>[In Raid]</green>" : " <gray>[Lobby]</gray>";
                    player.sendMessage(MM.deserialize((isLeader ? "<yellow>- " : "<gray>- ") + name + suffix));
                }
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
        player.sendMessage(MM.deserialize("<gold><bold>GlitchRaid</bold> <gray>— Raid lifecycle & parties</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid status</yellow> <gray>— Time left, your loot/deaths, party</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid invite <player></yellow> <gray>— Invite to party (max 4)</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid accept</yellow><gray>/</gray><yellow>decline</yellow> <gray>— Answer invite (30s)</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid kick <player></yellow> <gray>— Leader kicks</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid leave</yellow> <gray>— Leave party (not in raid)</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid list</yellow> <gray>— Show party</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid start</yellow> <gray>— Start solo (auto also on entering glitch_red)</gray>"));
        player.sendMessage(MM.deserialize("<dark_gray>Party auto-teleports: when any member enters glitch_red, rest are pulled.</dark_gray>"));
    }

    private String getLeaderName(RaidSession session) {
        return getLeaderNameById(session.getLeader());
    }

    private String getLeaderNameById(java.util.UUID id) {
        Player leader = plugin.getServer().getPlayer(id);
        if (leader != null) return leader.getName();
        var offline = plugin.getServer().getOfflinePlayer(id);
        String name = offline.getName();
        return name != null ? name : id.toString().substring(0, 8);
    }

    private String getPlayerName(java.util.UUID id) {
        Player p = plugin.getServer().getPlayer(id);
        if (p != null) return p.getName();
        var offline = plugin.getServer().getOfflinePlayer(id);
        String n = offline.getName();
        return n != null ? n : id.toString().substring(0, 8);
    }
}
