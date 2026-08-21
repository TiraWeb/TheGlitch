package com.theglitch.glitchinsurance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InsuranceAdminCommand implements CommandExecutor, TabCompleter {

    private final GlitchInsurance plugin;
    private final InsuranceManager manager;

    public InsuranceAdminCommand(GlitchInsurance plugin, InsuranceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("glitchinsurance.admin")) {
            sender.sendMessage(plugin.getComponent("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /insuranceadmin <reload|list <player>|clear <player>>", NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getComponent("reload"));
                sender.sendMessage(plugin.getComponent("admin-reloaded"));
                return true;
            }
            case "list" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /insuranceadmin list <player>", NamedTextColor.YELLOW));
                    return true;
                }
                UUID uuid = resolveUuid(args[1]);
                if (uuid == null) {
                    sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                    return true;
                }
                var insured = manager.getInsured(uuid);
                if (insured.isEmpty()) {
                    sender.sendMessage(plugin.getComponent("no-insurance"));
                    return true;
                }
                sender.sendMessage(plugin.getComponent("admin-list-header",
                        "<player>", args[1],
                        "<count>", String.valueOf(insured.size())));
                for (InsuranceManager.InsuredItem it : insured) {
                    sender.sendMessage(plugin.getComponent("list-entry",
                            "<item>", it.itemName(),
                            "<remaining>", String.valueOf(it.remainingSeconds())));
                }
                return true;
            }
            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /insuranceadmin clear <player>", NamedTextColor.YELLOW));
                    return true;
                }
                UUID uuid = resolveUuid(args[1]);
                if (uuid == null) {
                    sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                    return true;
                }
                boolean had = manager.clear(uuid);
                if (had) {
                    sender.sendMessage(plugin.getComponent("admin-cleared",
                            "<player>", args[1]));
                } else {
                    sender.sendMessage(plugin.getComponent("no-insurance"));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: /insuranceadmin <reload|list <player>|clear <player>>", NamedTextColor.YELLOW));
                return true;
            }
        }
    }

    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        // Try exact offline
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline.getUniqueId();
        // Search by name among offline? Bukkit.getOfflinePlayer always returns object even if never played — check hasPlayedBefore needed.
        // If not found, try case-insensitive search among offline players? Simplify: if offline.getName() != null use its uuid.
        if (offline.getName() != null) return offline.getUniqueId();
        // Fallback: try to find UUID via server's offline cache iterating?
        // As last resort, return null to indicate not found unless we want to allow any name.
        // For admin tool, we allow creation of uuid via name lookup even if never seen — use offline uuid.
        // But to avoid false positives, if hasPlayedBefore false and name not matching stored, return null.
        // We'll return offline uuid if name matches case-insensitively? Since Bukkit returns same offline object always, we need additional guard.
        // We'll check if offline.hasPlayedBefore() OR Bukkit.getPlayer(name) != null. Already did.
        // If neither, we consider not found.
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            List<String> opts = List.of("reload", "list", "clear");
            List<String> out = new ArrayList<>();
            for (String o : opts) if (o.startsWith(pref)) out.add(o);
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("clear"))) {
            String pref = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
            }
            return out;
        }
        return List.of();
    }
}
