package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin commands for GlitchClasses — /classadmin
 */
public record ClassAdminCommand(GlitchClasses plugin, ClassManager classManager)
        implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("glitchclasses.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getComponent("admin-reloaded"));
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.getComponent("admin-usage"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                    return true;
                }
                String className = args[2].toLowerCase();
                if (!classManager.getClassNames().contains(className)) {
                    sender.sendMessage(Component.text("Unknown class: " + args[2], NamedTextColor.RED));
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid level: " + args[3], NamedTextColor.RED));
                    return true;
                }
                classManager.setClass(target.getUniqueId(), className);
                classManager.setLevel(target.getUniqueId(), level);
                target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20 + (level * 2));
                sender.sendMessage(plugin.getComponent("admin-set",
                        "<player>", target.getName(),
                        "<class>", className,
                        "<level>", String.valueOf(level)));
                target.sendMessage(plugin.getComponent("class-selected", "<class>",
                        className.substring(0, 1).toUpperCase() + className.substring(1)));
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getComponent("admin-usage"));
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                    return true;
                }
                classManager.resetClass(target.getUniqueId());
                target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(20);
                sender.sendMessage(plugin.getComponent("admin-reset", "<player>", target.getName()));
            }
            case "list" -> {
                sender.sendMessage(Component.text("=== CLASS DATA ===", NamedTextColor.GOLD));
                for (ClassData data : classManager.getAllPlayers()) {
                    Player target = plugin.getServer().getPlayer(data.uuid());
                    String name = target != null ? target.getName() : data.uuid().toString().substring(0, 8);
                    sender.sendMessage(Component.text(name + ": " + data.className() + " Lv." + data.level(),
                            NamedTextColor.GRAY));
                }
                sender.sendMessage(Component.text("Total: " + classManager.getPlayerCount() + " players",
                        NamedTextColor.GRAY));
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("=== GlitchClasses Admin ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/classadmin reload", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/classadmin set <player> <class> <level>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/classadmin reset <player>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/classadmin list", NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("glitchclasses.admin")) return completions;

        if (args.length == 1) {
            completions.addAll(List.of("reload", "set", "reset", "list"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("reset"))) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                completions.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            completions.addAll(classManager.getClassNames());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            completions.addAll(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        }
        return completions;
    }
}
