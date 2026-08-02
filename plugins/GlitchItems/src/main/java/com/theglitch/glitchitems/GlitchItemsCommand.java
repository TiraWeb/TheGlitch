package com.theglitch.glitchitems;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GlitchItemsCommand implements CommandExecutor {

    private final GlitchItems plugin;
    private final GearManager gearManager;
    private final ResidualGlitchManager glitchManager;

    public GlitchItemsCommand(GlitchItems plugin, GearManager gearManager, ResidualGlitchManager glitchManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
        this.glitchManager = glitchManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>Usage: /glitchitems <give|glitch|reload></gray>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give":
                return give(sender, args);
            case "glitch":
                return glitch(sender, args);
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>GlitchItems reloaded.</green>"));
                return true;
            default:
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>Unknown subcommand.</red>"));
                return true;
        }
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>Usage: /glitchitems give <rarity> <type> [resonance] [player]</gray>"));
            return true;
        }
        Rarity rarity = Rarity.fromId(args[1]);
        if (rarity == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown rarity.</red>"));
            return true;
        }
        GearType type = GearType.fromId(args[2]);
        if (type == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Unknown gear type.</red>"));
            return true;
        }
        Resonance resonance = null;
        Player target = sender instanceof Player player ? player : null;
        if (args.length >= 4) {
            resonance = Resonance.fromId(args[3]);
        }
        if (args.length >= 5) {
            target = Bukkit.getPlayer(args[4]);
            if (target == null) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found.</red>"));
                return true;
            }
        }
        if (target == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Console needs a player argument.</red>"));
            return true;
        }
        ItemStack gear = gearManager.generateGear(type, rarity, resonance);
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItem(target.getLocation(), gear);
        } else {
            target.getInventory().addItem(gear);
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>Gave " + rarity.getDisplayName() + " " + type.getLabel() + " to " + target.getName() + ".</green>"));
        return true;
    }

    private boolean glitch(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>Usage: /glitchitems glitch <player> <set <n>|add <n>|clear></gray>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found.</red>"));
            return true;
        }
        switch (args[2].toLowerCase()) {
            case "set":
            case "add": {
                if (args.length < 4) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<gray>Usage: /glitchitems glitch <player> " + args[2] + " <stacks></gray>"));
                    return true;
                }
                int value;
                try {
                    value = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Stacks must be a number.</red>"));
                    return true;
                }
                int stacks = args[2].equalsIgnoreCase("set")
                        ? value
                        : glitchManager.getStacks(target) + value;
                glitchManager.setStacks(target, Math.max(0, Math.min(stacks, maxStacks())));
                break;
            }
            case "clear":
                glitchManager.clear(target);
                break;
            default:
                sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>Use set <n>, add <n> or clear.</red>"));
                return true;
        }
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>" + target.getName() + " now has " + glitchManager.getStacks(target) + " Residual Glitch stacks.</green>"));
        return true;
    }

    private int maxStacks() {
        return plugin.getConfig().getInt("residual-glitch.max-stacks", 8);
    }
}
