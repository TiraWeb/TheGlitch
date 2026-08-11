package com.theglitch.glitchitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /glitchcontainers — mark and manage in-world loot containers.
 * Usage: /glitchcontainers set <type> | clear | info | types | reload
 */
public record ContainerCommand(GlitchItems plugin, ContainerManager manager) implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text(
                    "Usage: /glitchcontainers <set <type>|clear|info|types|reload>", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /glitchcontainers set <type>", NamedTextColor.RED));
                    return true;
                }
                ContainerManager.ContainerType type = manager.getType(args[1].toLowerCase());
                if (type == null) {
                    sender.sendMessage(Component.text("Unknown container type. Use /glitchcontainers types.",
                            NamedTextColor.RED));
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                if (block == null || block.getType().isAir()) {
                    player.sendMessage(Component.text("Look at a block first.", NamedTextColor.RED));
                    return true;
                }
                manager.mark(block, type);
                player.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<green>Set <white>" + type.display() + "</white> on "
                                + block.getType() + " at " + block.getX() + "," + block.getY() + "," + block.getZ()));
            }
            case "clear" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                if (block == null || !manager.isContainer(block)) {
                    player.sendMessage(Component.text("Look at a Glitch container first.", NamedTextColor.RED));
                    return true;
                }
                manager.clear(block);
                player.sendMessage(Component.text("Container flag cleared.", NamedTextColor.GRAY));
            }
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                ContainerManager.ContainerType type = block == null ? null : manager.typeOf(block);
                if (type == null) {
                    player.sendMessage(Component.text("That is not a Glitch container.", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text(type.display() + " (" + type.name() + ")", NamedTextColor.GOLD));
                player.sendMessage(Component.text("Key: "
                        + (type.requiresKey() ? type.keyId() : "none")
                        + " | Regen: " + type.regenSeconds() + "s | Rolls: " + type.maxRolls(),
                        NamedTextColor.GRAY));
            }
            case "types" -> {
                List<ContainerManager.ContainerType> types = manager.getTypes();
                sender.sendMessage(Component.text("Container types (" + types.size() + "):", NamedTextColor.GOLD));
                for (ContainerManager.ContainerType t : types) {
                    sender.sendMessage(Component.text(" - " + t.name() + " (" + t.material() + ") key="
                            + (t.requiresKey() ? t.keyId() : "none"), NamedTextColor.GRAY));
                }
            }
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("GlitchItems reloaded.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(
                    "Usage: /glitchcontainers <set <type>|clear|info|types|reload>", NamedTextColor.RED));
        }
        return true;
    }
}
