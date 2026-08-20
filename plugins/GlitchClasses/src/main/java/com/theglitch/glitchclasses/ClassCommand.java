package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Handles /class command — opens GUI or direct class operations.
 */
public final class ClassCommand implements CommandExecutor {

    private final GlitchClasses plugin;
    private final ClassManager classManager;
    private Economy cachedEconomy;
    private long economyCacheTime;

    public ClassCommand(GlitchClasses plugin, ClassManager classManager) {
        this.plugin = plugin;
        this.classManager = classManager;
    }

    private Economy getEconomy() {
        long now = System.currentTimeMillis();
        if (cachedEconomy != null && now - economyCacheTime < 30_000L) return cachedEconomy;
        RegisteredServiceProvider<Economy> reg = org.bukkit.Bukkit.getServicesManager().getRegistration(Economy.class);
        cachedEconomy = reg != null ? reg.getProvider() : null;
        economyCacheTime = now;
        return cachedEconomy;
    }

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
                boolean firstSelect = !classManager.hasClass(player.getUniqueId());
                classManager.setClass(player.getUniqueId(), className);
                if (firstSelect) {
                    plugin.getStarterKit().giveIfFirstSelect(player);
                }
                player.sendMessage(plugin.getComponent("class-selected", "<class>",
                        className.substring(0, 1).toUpperCase() + className.substring(1)));
                classManager.applyMaxHealth(player, classManager.getClassData(player.getUniqueId()).level());
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
                ClassData data = classManager.getClassData(player.getUniqueId());
                if (data.className().equals("none")) {
                    player.sendMessage(plugin.getComponent("class-none"));
                    return true;
                }
                int cost = classManager.getResetCost();
                Economy economy = getEconomy();
                if (economy == null) {
                    player.sendMessage(Component.text("Economy unavailable.", NamedTextColor.RED));
                    return true;
                }
                if (!economy.has(player, cost)) {
                    player.sendMessage(plugin.getComponent("class-reset-cost",
                            "<cost>", String.valueOf(cost),
                            "<shards>", String.valueOf((int) economy.getBalance(player))));
                    return true;
                }
                economy.withdrawPlayer(player, cost);
                classManager.resetClass(player.getUniqueId());
                classManager.applyMaxHealth(player, 0);
                player.sendMessage(plugin.getComponent("class-reset"));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            }
            default -> {
                player.sendMessage(Component.text("Usage: /class [select <class>|info|reset]", NamedTextColor.RED));
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
