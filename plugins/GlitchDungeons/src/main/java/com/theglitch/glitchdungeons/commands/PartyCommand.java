package com.theglitch.glitchdungeons.commands;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.Party;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyCommand implements CommandExecutor {
    private final GlitchDungeons plugin;

    public PartyCommand(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // /pchat or /pc
        if (command.getName().equalsIgnoreCase("pchat")) {
            return handlePartyChat(player, args);
        }

        // /party or /p
        if (args.length == 0) {
            return showPartyInfo(player);
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "kick" -> handleKick(player, args);
            case "leave" -> handleLeave(player);
            case "list" -> showPartyInfo(player);
            default -> {
                player.sendMessage(colorize("&cUnknown subcommand. Use /p invite|accept|kick|leave|list"));
                yield true;
            }
        };
    }

    private boolean showPartyInfo(Player player) {
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(colorize("&cYou are not in a party."));
            return true;
        }
        player.sendMessage(colorize("&6=== Party ==="));
        player.sendMessage(colorize("&eLeader: &f" + getLeaderName(party)));
        player.sendMessage(colorize("&eMembers: &f" + party.getSize() + "/" + plugin.getDungeonConfig().getMaxPartySize()));
        for (Player member : getOnlineMembers(party)) {
            player.sendMessage(colorize("  &a- " + member.getName() +
                (party.isLeader(member.getUniqueId()) ? " &e[LEADER]" : "")));
        }
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(colorize("&cUsage: /p invite <player>"));
            return true;
        }
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            party = plugin.getPartyManager().createParty(player);
            player.sendMessage(colorize("&aParty created!"));
        }
        if (!party.isLeader(player.getUniqueId())) {
            player.sendMessage(colorize("&cOnly the party leader can invite."));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(colorize("&cPlayer not found."));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(colorize("&cYou can't invite yourself."));
            return true;
        }
        if (plugin.getPartyManager().hasParty(target.getUniqueId())) {
            player.sendMessage(colorize("&cThat player is already in a party."));
            return true;
        }
        if (party.getSize() >= plugin.getDungeonConfig().getMaxPartySize()) {
            player.sendMessage(colorize("&cParty is full."));
            return true;
        }
        if (plugin.getPartyManager().invitePlayer(player, target)) {
            player.sendMessage(colorize("&aParty invite sent to &e" + target.getName() + "&a!"));
            target.sendMessage(colorize("&aYou have been invited to " + player.getName() + "'s party. &e/p accept"));
        }
        return true;
    }

    private boolean handleAccept(Player player) {
        if (plugin.getPartyManager().hasParty(player.getUniqueId())) {
            player.sendMessage(colorize("&cYou are already in a party."));
            return true;
        }
        if (plugin.getPartyManager().acceptInvite(player)) {
            Party party = plugin.getPartyManager().getParty(player.getUniqueId());
            player.sendMessage(colorize("&aYou joined the party!"));
            if (party != null) {
                for (Player member : getOnlineMembers(party)) {
                    if (!member.equals(player)) {
                        member.sendMessage(colorize("&a" + player.getName() + " joined the party!"));
                    }
                }
            }
        } else {
            player.sendMessage(colorize("&cNo pending invite or invite expired."));
        }
        return true;
    }

    private boolean handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(colorize("&cUsage: /p kick <player>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(colorize("&cPlayer not found."));
            return true;
        }
        if (plugin.getPartyManager().kickMember(player, target)) {
            target.sendMessage(colorize("&cYou were kicked from the party."));
            Party party = plugin.getPartyManager().getParty(player.getUniqueId());
            if (party != null) {
                for (Player member : getOnlineMembers(party)) {
                    member.sendMessage(colorize("&c" + target.getName() + " was kicked from the party."));
                }
            }
        } else {
            player.sendMessage(colorize("&cCouldn't kick that player."));
        }
        return true;
    }

    private boolean handleLeave(Player player) {
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(colorize("&cYou are not in a party."));
            return true;
        }
        plugin.getPartyManager().leaveParty(player.getUniqueId());
        player.sendMessage(colorize("&cYou left the party."));
        // Notify remaining members
        for (Player member : getOnlineMembers(party)) {
            member.sendMessage(colorize("&c" + player.getName() + " left the party."));
        }
        return true;
    }

    private boolean handlePartyChat(Player player, String[] args) {
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage(colorize("&cYou are not in a party."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(colorize("&cUsage: /pchat <message>"));
            return true;
        }
        String msg = String.join(" ", args);
        String formatted = colorize("&d[Party] " + player.getName() + ": &f" + msg);
        for (Player member : getOnlineMembers(party)) {
            member.sendMessage(formatted);
        }
        return true;
    }

    private String getLeaderName(Party party) {
        Player leader = Bukkit.getPlayer(party.getLeaderUuid());
        return leader != null ? leader.getName() : "Offline";
    }

    private java.util.List<Player> getOnlineMembers(Party party) {
        java.util.List<Player> online = new java.util.ArrayList<>();
        for (java.util.UUID uuid : party.getMembers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) online.add(p);
        }
        return online;
    }

    private String colorize(String msg) {
        return msg.replaceAll("&([0-9a-fk-or])", "\u00A7$1");
    }
}
