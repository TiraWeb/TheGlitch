package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /class command — opens GUI or direct class operations.
 */
public record ClassCommand(GlitchClasses plugin, ClassManager classManager) implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            plugin.getClassGUI().openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "select" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /class select <class>", NamedTextColor.RED));
                    return true;
                }
                String className = args[1].toLowerCase();
                if (!classManager.getClassNames().contains(className)) {
                    player.sendMessage(plugin.getComponent("class-not-found", "<class>", args[1]));
                    return true;
                }
                classManager.setClass(player.getUniqueId(), className);
                plugin.getAbilityItemManager().forceGiveClassItems(player, className);
                player.sendMessage(plugin.getComponent("class-selected", "<class>",
                        className.substring(0, 1).toUpperCase() + className.substring(1)));
                ClassData newData = classManager.getClassData(player.getUniqueId());
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20 + (newData.level() * 2));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case "kit" -> {
                ClassData data = classManager.getClassData(player.getUniqueId());
                if (data.className().equals("none")) {
                    player.sendMessage(Component.text("Select a class first!", NamedTextColor.RED));
                    return true;
                }
                if (plugin.getAbilityItemManager().hasClassItems(player)) {
                    player.sendMessage(Component.text("You already have your ability items.", NamedTextColor.YELLOW));
                    return true;
                }
                plugin.getAbilityItemManager().giveClassItems(player, data.className());
                player.sendMessage(Component.text("Ability items given!", NamedTextColor.GREEN));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }
            case "info" -> {
                ClassData data = classManager.getClassData(player.getUniqueId());
                if (data.className().equals("none")) {
                    player.sendMessage(plugin.getComponent("class-none"));
                    return true;
                }
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("=== YOUR CLASS ===", NamedTextColor.GOLD));
                player.sendMessage(Component.text("Class: ", NamedTextColor.GRAY)
                        .append(Component.text(data.className().toUpperCase(),
                                CLASS_COLORS.getOrDefault(data.className(), NamedTextColor.WHITE),
                                net.kyori.adventure.text.format.TextDecoration.BOLD)));
                player.sendMessage(Component.text("Level: ", NamedTextColor.GRAY)
                        .append(Component.text(data.level() + "/" + classManager.getMaxLevel(), NamedTextColor.GOLD)));
                player.sendMessage(Component.text("XP: ", NamedTextColor.GRAY)
                        .append(Component.text(data.xp() + "/" + classManager.getXpForLevel(data.level() + 1), NamedTextColor.YELLOW)));
                player.sendMessage(Component.empty());
            }
            case "reset" -> {
                int cost = classManager.getResetCost();
                player.sendMessage(plugin.getComponent("class-reset-cost",
                        "<cost>", String.valueOf(cost),
                        "<shards>", "check TODO"));
            }
            default -> {
                player.sendMessage(Component.text("Usage: /class [select <class>|info|reset|kit]", NamedTextColor.RED));
            }
        }
        return true;
    }

    private static final java.util.Map<String, NamedTextColor> CLASS_COLORS = java.util.Map.of(
            "vanguard", NamedTextColor.RED,
            "warden", NamedTextColor.GREEN,
            "specter", NamedTextColor.DARK_PURPLE,
            "operator", NamedTextColor.AQUA
    );
}
