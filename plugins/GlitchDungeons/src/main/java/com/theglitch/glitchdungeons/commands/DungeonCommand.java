package com.theglitch.glitchdungeons.commands;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.gui.DungeonSelectGUI;
import com.theglitch.glitchdungeons.models.DungeonRun;
import com.theglitch.glitchdungeons.models.Party;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DungeonCommand implements CommandExecutor {
    private final GlitchDungeons plugin;

    public DungeonCommand(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            if (com.theglitch.glitchdungeons.ui.DialogUI.canRemote(plugin, player)) {
                com.theglitch.glitchdungeons.ui.DialogUI.openRoot(plugin, player, () -> {
                    DungeonSelectGUI gui = plugin.getSelectGui();
                    if (gui != null) {
                        gui.open(player);
                    }
                });
            } else {
                DungeonSelectGUI gui = plugin.getSelectGui();
                if (gui != null) {
                    gui.open(player);
                }
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "join" -> handleJoin(player, args);
            case "queue" -> handleQueue(player);
            case "info" -> handleInfo(player);
            case "leave" -> handleLeave(player);
            default -> {
                player.sendMessage(colorize("&cUsage: /dungeon <join|queue|info|leave>"));
                yield true;
            }
        };
    }

    private boolean handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(colorize("&cUsage: /dungeon join <tier>"));
            return true;
        }

        int tier;
        try {
            tier = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cInvalid tier number."));
            return true;
        }

        // Check party
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            // Create solo party
            party = plugin.getPartyManager().createParty(player);
            player.sendMessage(colorize("&aParty created (solo)!"));
        }

        if (!party.isLeader(player.getUniqueId())) {
            player.sendMessage(colorize("&cOnly the party leader can start a dungeon."));
            return true;
        }

        // Check dungeon exists
        if (!plugin.getDungeonConfig().getDungeons().containsKey(tier)) {
            player.sendMessage(colorize("&cInvalid dungeon tier."));
            return true;
        }

        // Check permission
        if (!player.hasPermission("glitchdungeons.dungeon.tier" + tier)) {
            player.sendMessage(colorize("&cYou don't have permission for this dungeon tier."));
            return true;
        }

        // Check cooldown
        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), tier)) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(player.getUniqueId(), tier);
            player.sendMessage(colorize("&cYou must wait " + formatTime(remaining) + " before entering this dungeon again."));
            return true;
        }

        // Check free slots
        if (plugin.getDungeonManager().getFreeSlotCount() == 0) {
            player.sendMessage(colorize("&cAll dungeon slots are full. Try again later."));
            return true;
        }

        // Check all party members online and have permission
        for (java.util.UUID memberUuid : party.getMembers()) {
            Player member = org.bukkit.Bukkit.getPlayer(memberUuid);
            if (member == null || !member.isOnline()) {
                player.sendMessage(colorize("&cAll party members must be online."));
                return true;
            }
            if (!member.hasPermission("glitchdungeons.dungeon.tier" + tier)) {
                player.sendMessage(colorize("&c" + member.getName() + " doesn't have permission for this dungeon tier."));
                return true;
            }
        }

        // Start dungeon
        var tierConfig = plugin.getDungeonConfig().getDungeon(tier);
        String dungeonName = tierConfig != null ? tierConfig.getName() : "Unknown";

        // Notify party
        String joinMsg = colorize("&aJoining " + dungeonName + " (Tier " + tier + ")...");
        for (java.util.UUID memberUuid : party.getMembers()) {
            Player member = org.bukkit.Bukkit.getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(joinMsg);
            }
        }

        DungeonRun run = plugin.getDungeonManager().startDungeon(party, tier);
        if (run == null) {
            player.sendMessage(colorize("&cFailed to start dungeon. Try again."));
            return true;
        }

        return true;
    }

    private boolean handleQueue(Player player) {
        // Solo queue
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            party = plugin.getPartyManager().createParty(player);
        }

        // Find any available tier the player has permission for
        for (int tier = 1; tier <= 4; tier++) {
            if (!player.hasPermission("glitchdungeons.dungeon.tier" + tier)) continue;
            if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), tier)) continue;
            if (!plugin.getDungeonConfig().getDungeons().containsKey(tier)) continue;
            if (plugin.getDungeonManager().getFreeSlotCount() == 0) {
                player.sendMessage(colorize("&cAll dungeon slots are full."));
                return true;
            }

            DungeonRun run = plugin.getDungeonManager().startDungeon(party, tier);
            if (run != null) {
                var tierConfig = plugin.getDungeonConfig().getDungeon(tier);
                player.sendMessage(colorize("&aQueued for " + (tierConfig != null ? tierConfig.getName() : "Tier " + tier) + "!"));
                return true;
            }
        }

        player.sendMessage(colorize("&cNo dungeons available right now."));
        return true;
    }

    private boolean handleInfo(Player player) {
        DungeonRun run = plugin.getDungeonManager().getPlayerRun(player.getUniqueId());
        if (run == null) {
            player.sendMessage(colorize("&cYou are not in a dungeon."));
            return true;
        }
        player.sendMessage(colorize("&6=== Dungeon Info ==="));
        player.sendMessage(colorize("&eState: &f" + run.getState()));
        player.sendMessage(colorize("&eTier: &f" + run.getTier()));
        player.sendMessage(colorize("&eWave: &f" + run.getCurrentWave() + "/" + run.getTotalWaves()));
        int time = run.getRemainingTime();
        player.sendMessage(colorize("&eTime: &f" + formatTime(time)));
        return true;
    }

    private boolean handleLeave(Player player) {
        DungeonRun run = plugin.getDungeonManager().getPlayerRun(player.getUniqueId());
        if (run == null) {
            player.sendMessage(colorize("&cYou are not in a dungeon."));
            return true;
        }
        if (run.getState() == DungeonRun.State.ACTIVE || run.getState() == DungeonRun.State.PREP) {
            plugin.getDungeonManager().failDungeon(run, DungeonRun.FailReason.WIPE);
        } else {
            player.sendMessage(colorize("&cCan't leave right now."));
        }
        return true;
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m + ":" + String.format("%02d", s);
    }

    private String colorize(String msg) {
        return msg.replaceAll("&([0-9a-fk-or])", "\u00A7$1");
    }
}
