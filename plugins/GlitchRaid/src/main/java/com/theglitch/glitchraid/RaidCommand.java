package com.theglitch.glitchraid;

import com.theglitch.common.FoliaScheduler;
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
                if (manager.isGlobalRemainingMode() && !manager.isRedWorld(player.getWorld().getName())) {
                    player.sendMessage(MM.deserialize("<gray>Raids start when you enter a Red Zone — walk through the portal or use <yellow>/redzone</yellow>. Your party leader brings party members in the hub along.</gray>"));
                    return true;
                }
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
                if (manager.isSessionGlobal(session)) {
                    // The shared per-world session's "leader" is a synthetic id — show the
                    // zone and the player's own party instead of leaking a UUID fragment.
                    String zone = manager.getWorldDisplayName(session.getWorldKey());
                    Party party = manager.getPartyManager().getParty(player.getUniqueId());
                    String partyLine = party == null ? "<white>Solo</white>"
                            : "<white>" + party.getSize() + "/" + manager.getPartyMaxSize() + "</white> <gray>| Leader: <white>" + getLeaderNameById(party.getLeader()) + "</white>";
                    player.sendMessage(MM.deserialize("<gray>Zone: <white>" + zone + "</white> <gray>| Raiders: <white>" + session.getMembers().size()
                            + "</white> <gray>| Party: " + partyLine));
                    return true;
                }
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
                    player.sendMessage(MM.deserialize("<red>Usage: /party invite <player></red>"));
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
                // Global-remaining mode: invites are allowed even mid-extraction (shared 30m window)
                if (manager.isInRaid(player.getUniqueId()) && !manager.isGlobalRemainingMode()) {
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
                player.sendMessage(MM.deserialize("<green>Invited <white>" + target.getName() + "</white> to your party. <gray>(" + partyMgr.getParty(player.getUniqueId()).getSize() + "/" + manager.getPartyMaxSize() + ")</gray></green>"));
                target.sendMessage(MM.deserialize("<green><white>" + player.getName() + "</white> invited you to their party (raids + dungeons)! <yellow>/party accept</yellow> to join. <gray>(30s)</gray></green>"));
                target.sendMessage(MM.deserialize("<gray>Party leader: <white>" + player.getName() + "</white></gray>"));
            }
            case "accept" -> {
                boolean ok = partyMgr.acceptInvite(player);
                if (!ok) {
                    player.sendMessage(MM.deserialize("<red>No pending party invite.</red>"));
                    return true;
                }
                Party party = partyMgr.getParty(player.getUniqueId());
                if (party == null) {
                    player.sendMessage(MM.deserialize("<green>Joined party.</green>"));
                    return true;
                }
                Player leader = Bukkit.getPlayer(party.getLeader());
                String leaderName = leader != null ? leader.getName() : Bukkit.getOfflinePlayer(party.getLeader()).getName();
                player.sendMessage(MM.deserialize("<green>Joined <white>" + leaderName + "</white>'s party! <gray>(" + party.getSize() + "/" + manager.getPartyMaxSize() + ")</gray></green>"));
                if (leader != null && !leader.getUniqueId().equals(player.getUniqueId())) {
                    leader.sendMessage(MM.deserialize("<green><white>" + player.getName() + "</white> joined your party! <gray>(" + party.getSize() + "/" + manager.getPartyMaxSize() + ")</gray></green>"));
                }
                // Leader already raiding: join them — but only from the hub (never out of a
                // dungeon or another raid). The world change adds the player to that raid.
                Player raidingLeader = Bukkit.getPlayer(party.getLeader());
                if (raidingLeader != null && !raidingLeader.getUniqueId().equals(player.getUniqueId())
                        && manager.isInRaid(raidingLeader.getUniqueId())
                        && manager.isRedWorld(raidingLeader.getWorld().getName())
                        && !manager.isInRaid(player.getUniqueId())) {
                    String zone = manager.getWorldDisplayName(raidingLeader.getWorld().getName());
                    if (!player.getWorld().getName().equalsIgnoreCase(manager.getHubWorld())) {
                        player.sendMessage(MM.deserialize("<gray>Your leader is raiding <white>" + zone + "</white> — go back to the hub to join them.</gray>"));
                    } else if (manager.isInBufferPeriod(raidingLeader.getWorld().getName())) {
                        player.sendMessage(MM.deserialize("<gray>Your leader is in <white>" + zone + "</white> — it reopens after the loot reshuffle.</gray>"));
                    } else {
                        player.sendMessage(MM.deserialize("<gray>Your leader is raiding <white>" + zone + "</white> — teleporting you to them...</gray>"));
                        FoliaScheduler.teleportEntity(player, plugin, raidingLeader.getLocation());
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
                player.sendMessage(MM.deserialize(wasLeader ? "<yellow>Disbanded your party.</yellow>" : "<yellow>Left the party.</yellow>"));
            }
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(MM.deserialize("<red>Usage: /party kick <player></red>"));
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
                target.sendMessage(MM.deserialize("<red>You were kicked from the party.</red>"));
            }
            case "list", "party" -> {
                Party party = partyMgr.getParty(player.getUniqueId());
                if (party == null) {
                    player.sendMessage(MM.deserialize("<gray>You're not in a party. <yellow>/party invite <player></yellow> to create one.</gray>"));
                    return true;
                }
                player.sendMessage(MM.deserialize("<gold><bold>Party</bold> <gray>" + party.getSize() + "/" + manager.getPartyMaxSize() + " <gray>Leader: <white>" + getLeaderNameById(party.getLeader()) + "</white>"));
                for (java.util.UUID mid : party.getMembers()) {
                    boolean isLeader = mid.equals(party.getLeader());
                    String name = getPlayerName(mid);
                    String suffix = isLeader ? " <yellow>[Leader]</yellow>" : "";
                    Player online = Bukkit.getPlayer(mid);
                    if (online == null) {
                        suffix += " <dark_gray>[Offline]</dark_gray>";
                    } else if (manager.isInRaid(mid)) {
                        suffix += " <red>[" + manager.getWorldDisplayName(online.getWorld().getName()) + "]</red>";
                    } else if (online.getWorld().getName().equalsIgnoreCase(manager.getHubWorld())) {
                        suffix += " <green>[Hub]</green>";
                    } else {
                        suffix += " <light_purple>[Dungeon]</light_purple>";
                    }
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
        player.sendMessage(MM.deserialize("<gold><bold>Party</bold> <gray>— one party for raids and dungeons (max " + manager.getPartyMaxSize() + ")</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/party invite <player></yellow> <gray>— Invite (creates your party)</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/party accept</yellow><gray>/</gray><yellow>decline</yellow> <gray>— Answer an invite (30s)</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/party list</yellow> <gray>— Members and where they are</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/party kick <player></yellow> <gray>— Leader removes a member</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/party leave</yellow> <gray>— Leave (the leader leaving disbands it)</gray>"));
        player.sendMessage(MM.deserialize("<yellow>/raid status</yellow> <gray>— Time left, your loot and deaths</gray>"));
        player.sendMessage(MM.deserialize("<dark_gray>Raids: when the leader enters a Red Zone, members in the hub come along. "
                + "Dungeons: the leader starts one from /dungeons; members must be in the hub and type /ready.</dark_gray>"));
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
