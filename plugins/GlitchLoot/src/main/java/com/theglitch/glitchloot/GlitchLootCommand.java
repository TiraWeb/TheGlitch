package com.theglitch.glitchloot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Handles /glitchloot {reload, status}
 */
public final class GlitchLootCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("reload", "status");

    private final GlitchLoot plugin;
    private final LootEngine engine;

    public GlitchLootCommand(GlitchLoot plugin, LootEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("glitchloot.admin")) {
            sender.sendMessage(plugin.mm().deserialize("<red>You don't have permission (glitchloot.admin).</red>"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getMessages().comp("reload",
                        "<green>GlitchLoot configuration reloaded.</green>"));
            }
            case "status" -> {
                sender.sendMessage(plugin.getMessages().comp("status",
                        "<gold><bold>GlitchLoot Status</bold></gold>"));
                sender.sendMessage(plugin.mm().deserialize(
                        "<gray>Power remaining: <white>" + engine.powerRemaining()
                                + "</white>/<white>" + engine.getMaxPowerPerHour() + "</white>"
                                + " <gray>(budget " + (engine.isPowerBudgetEnabled() ? "on" : "off") + ")</gray>"));
                sender.sendMessage(plugin.mm().deserialize(
                        "<gray>Adaptive bonus: <white>" + (engine.isAdaptiveEnabled() ? "on" : "off")
                                + "</white> <gray>| Cooldown: <white>" + engine.getCooldownSeconds()
                                + "s</white> <gray>(" + (engine.isAntiFunnelEnabled() ? "on" : "off") + ")</gray>"));
                if (sender instanceof Player player) {
                    sender.sendMessage(plugin.mm().deserialize(
                            "<gray>Your dry streak: <white>" + engine.getDryStreak(player.getUniqueId())
                                    + "</white> <gray>| Bonus: <aqua>" + engine.bonusPercent(player) + "%</aqua></gray>"));
                } else {
                    sender.sendMessage(plugin.mm().deserialize("<gray>Dry streak/bonus: <white>player only</white></gray>"));
                }
                sender.sendMessage(plugin.mm().deserialize(
                        "<gray>Worlds: <white>" + String.join(", ", engine.getEnabledWorlds()) + "</white></gray>"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.mm().deserialize("<gold><bold>GlitchLoot</bold></gold>"));
        sender.sendMessage(plugin.mm().deserialize("<yellow>/glitchloot reload</yellow> <gray>— Reload config</gray>"));
        sender.sendMessage(plugin.mm().deserialize("<yellow>/glitchloot status</yellow> <gray>— Power budget and your streak</gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
