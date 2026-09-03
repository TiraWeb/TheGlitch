package com.theglitch.glitchdungeons.commands;

import com.theglitch.glitchdungeons.ColorUtil;
import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import com.theglitch.glitchdungeons.models.DungeonSlot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DungeonAdminCommand implements CommandExecutor {
    private final GlitchDungeons plugin;

    public DungeonAdminCommand(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.colorize("&cUsage: /dungeonadmin <reload|force-start|cancel|slots|reset-cooldowns>"));
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "force-start" -> handleForceStart(sender, args);
            case "cancel" -> handleCancel(sender);
            case "slots" -> handleSlots(sender);
            case "reset-cooldowns" -> handleResetCooldowns(sender);
            default -> {
                sender.sendMessage(ColorUtil.colorize("&cUnknown subcommand."));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getDungeonConfig().reload(plugin.getConfig());
        sender.sendMessage(ColorUtil.colorize("&aConfig reloaded."));
        return true;
    }

    private boolean handleForceStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        int tier = 1;
        if (args.length >= 2) {
            try {
                tier = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtil.colorize("&cInvalid tier."));
                return true;
            }
        }
        var party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) {
            party = plugin.getPartyManager().createParty(player);
        }
        DungeonRun run = plugin.getDungeonManager().startDungeon(party, tier);
        if (run != null) {
            player.sendMessage(ColorUtil.colorize("&aForce-started dungeon tier " + tier + "."));
        } else {
            player.sendMessage(ColorUtil.colorize("&cFailed to start."));
        }
        return true;
    }

    private boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        DungeonRun run = plugin.getDungeonManager().getPlayerRun(player.getUniqueId());
        if (run == null) {
            sender.sendMessage(ColorUtil.colorize("&cNo active run."));
            return true;
        }
        plugin.getDungeonManager().failDungeon(run, DungeonRun.FailReason.WIPE);
        sender.sendMessage(ColorUtil.colorize("&aDungeon cancelled."));
        return true;
    }

    private boolean handleSlots(CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&6=== Dungeon Slots ==="));
        for (DungeonSlot slot : plugin.getDungeonConfig().getSlots().values()) {
            String status = slot.isOccupied() ? "&cOCCUPIED" : "&aFREE";
            sender.sendMessage(ColorUtil.colorize("  Slot " + slot.getId() + ": (" +
                slot.getCenterX() + ", " + slot.getCenterZ() + ") " + status));
        }
        sender.sendMessage(ColorUtil.colorize("&eFree slots: " + plugin.getDungeonManager().getFreeSlotCount() + "/" +
            plugin.getDungeonConfig().getSlots().size()));
        return true;
    }

    private boolean handleResetCooldowns(CommandSender sender) {
        plugin.getCooldownManager().resetAll();
        sender.sendMessage(ColorUtil.colorize("&aAll cooldowns reset."));
        return true;
    }

}
