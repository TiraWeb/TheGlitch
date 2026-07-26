package com.theglitch.glitchstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin commands: /stashadmin reload|clear <player>|list
 */
public record StashAdminCommand(GlitchStash plugin, StashManager stashManager) implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("glitchstash.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /stashadmin <reload|clear <player>|list>", NamedTextColor.GRAY));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getComponent("admin-reloaded"));
            }
            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /stashadmin clear <player>", NamedTextColor.GRAY));
                    return true;
                }
                UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                if (stashManager.clearStash(targetUUID)) {
                    sender.sendMessage(plugin.getComponent("admin-cleared", "<player>", args[1]));
                } else {
                    sender.sendMessage(Component.text(args[1] + " has no stash.", NamedTextColor.RED));
                }
            }
            case "list" -> {
                int count = stashManager.getStashCount();
                sender.sendMessage(Component.text("Active stashes: " + count, NamedTextColor.GOLD));
                if (count == 0) {
                    sender.sendMessage(Component.text("No stashes stored.", NamedTextColor.GRAY));
                }
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand: " + args[0], NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("glitchstash.admin")) return List.of();

        if (args.length == 1) {
            return List.of("reload", "clear", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            return null; // Let Bukkit suggest online player names
        }
        return List.of();
    }
}
